package ersbot.handlers

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.dispatcher.Dispatcher
import com.github.kotlintelegrambot.dispatcher.text
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.InlineKeyboardMarkup
import com.github.kotlintelegrambot.entities.ParseMode
import com.github.kotlintelegrambot.entities.keyboard.InlineKeyboardButton
import ersbot.config.BotState.activeMenus
import ersbot.config.BotState.currentSelections
import ersbot.config.BotState.userCarts
import ersbot.config.mainMenuText
import ersbot.keyboards.*
import ersbot.models.*
import ersbot.services.*

fun registerTextHandler(dispatcher: Dispatcher) {
    with(dispatcher) {
        text {

            if (message.text?.startsWith("/") == true) {
                return@text
            }

            if (!SubscriptionGuard.requireSubscription(bot, message)) {
                return@text
            }

            val chatId = message.chat.id
            val userText = message.text ?: ""
            val selection = currentSelections[chatId]

            when {
                selection?.state == BotState.AWAITING_ADDRESS && selection.addressStep == AddressStep.ENTER_HOUSE -> {
                    bot.deleteMessage(ChatId.fromId(chatId), message.messageId)
                    val result = validateAddressPart(userText, AddressStep.ENTER_HOUSE)

                    if (!result.isValid) {
                        activeMenus[chatId]?.let { menuId ->
                            bot.editMessageText(
                                chatId = ChatId.fromId(chatId),
                                messageId = menuId,
                                text = "❌ ${result.errorMessage}\n\n🏠 Введите номер дома:",
                                replyMarkup = InlineKeyboardMarkup.create(
                                    listOf(
                                        listOf(
                                            InlineKeyboardButton.CallbackData(
                                                "🔙 Назад к улицам",
                                                "streets_page_0"
                                            )
                                        )
                                    )
                                )
                            )
                        }
                        return@text
                    }

                    selection.addressInput = selection.addressInput?.copy(house = userText.trim())
                    selection.addressStep = AddressStep.ENTER_FLAT

                    activeMenus[chatId]?.let { menuId ->
                        bot.editMessageText(
                            chatId = ChatId.fromId(chatId),
                            messageId = menuId,
                            text = "✅ Дом: <b>${userText.trim()}</b>\n\n🚪 Введите <b>номер квартиры</b> (или нажмите «Пропустить»):\n<i>Пример: 5, 12Б</i>",
                            parseMode = ParseMode.HTML,
                            replyMarkup = getFlatKeyboard()
                        )
                    }
                }

                selection?.state == BotState.AWAITING_ADDRESS && selection.addressStep == AddressStep.ENTER_FLAT -> {
                    bot.deleteMessage(ChatId.fromId(chatId), message.messageId)

                    if (userText == "⏭️ Пропустить" || userText.lowercase() == "пропустить") {
                        selection.addressInput = selection.addressInput?.copy(flat = null)
                        selection.addressStep = AddressStep.CONFIRM
                        activeMenus[chatId]?.let { menuId ->
                            finalizeAddressInput(bot, chatId, menuId, selection, userCarts[chatId])
                        }
                        return@text
                    }

                    val result = validateAddressPart(userText, AddressStep.ENTER_FLAT)
                    if (!result.isValid) {
                        activeMenus[chatId]?.let { menuId ->
                            bot.editMessageText(
                                chatId = ChatId.fromId(chatId),
                                messageId = menuId,
                                text = "❌ ${result.errorMessage}\n\n🚪 Введите номер квартиры или нажмите «Пропустить»:",
                                replyMarkup = getFlatKeyboard()
                            )
                        }
                        return@text
                    }

                    selection.addressInput = selection.addressInput?.copy(flat = userText.trim())
                    selection.addressStep = AddressStep.CONFIRM
                    activeMenus[chatId]?.let { menuId ->
                        finalizeAddressInput(bot, chatId, menuId, selection, userCarts[chatId])
                    }
                }

                selection?.state == BotState.AWAITING_DATETIME -> {
                    bot.deleteMessage(ChatId.fromId(chatId), message.messageId)
                    val strictRegex = Regex(
                        "^\\s*([1-9]|[12]\\d|3[01])\\s+" +
                                "(января|февраля|марта|апреля|мая|июня|июля|" +
                                "августа|сентября|октября|ноября|декабря|" +
                                "янв|фев|мар|апр|май|июн|июл|авг|сен|окт|ноя|дек)\\s*" +
                                "(?:,|\\s+|в)?\\s*" +
                                "([01]\\d|2[0-3]):([0-5]\\d)\\s*$",
                        RegexOption.IGNORE_CASE
                    )
                    if (!strictRegex.matches(userText.trim())) {
                        activeMenus[chatId]?.let { menuId ->
                            bot.editMessageText(
                                chatId = ChatId.fromId(chatId),
                                messageId = menuId,
                                text = "❌ <b>Неверный формат даты!</b>\n\nНапишите по образцу:\n" +
                                        "👉 <i>16 апреля 18:30</i> или <i>11 апр, 10:00</i> или <i>5 янв в 18:00</i>",
                                parseMode = ParseMode.HTML
                            )
                        }
                        return@text
                    }
                    selection.datetime = userText
                    selection.state = BotState.IDLE
                    activeMenus[chatId]?.let { menuId ->
                        renderCheckout(bot, chatId, menuId, userCarts[chatId], selection)
                    }
                }

                else -> {
                    if (userText == "🔄 Перезапустить бота") {
                        bot.deleteMessage(ChatId.fromId(chatId), message.messageId)
                        activeMenus[chatId]?.let { bot.deleteMessage(ChatId.fromId(chatId), it) }
                        userCarts.remove(chatId)
                        currentSelections.remove(chatId)
                        val res = bot.sendMessage(
                            chatId = ChatId.fromId(chatId),
                            text = mainMenuText,
                            parseMode = ParseMode.HTML,
                            replyMarkup = getMainMenuKeyboard(),
                            disableWebPagePreview = true
                        )
                        res.getOrNull()?.messageId?.let { activeMenus[chatId] = it }
                    } else {
                        // ✅ НЕ удаляем сообщение, если это не команда перезапуска
                        // Просто игнорируем ввод вне сценария
                        // bot.deleteMessage(ChatId.fromId(chatId), message.messageId) // ← Закомментировано!
                    }
                }
            }
        }
    }
}

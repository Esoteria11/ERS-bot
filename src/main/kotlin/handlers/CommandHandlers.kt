package ersbot.handlers

import com.github.kotlintelegrambot.dispatcher.Dispatcher
import com.github.kotlintelegrambot.dispatcher.command
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.InlineKeyboardMarkup
import com.github.kotlintelegrambot.entities.KeyboardReplyMarkup
import com.github.kotlintelegrambot.entities.keyboard.KeyboardButton
import com.github.kotlintelegrambot.entities.ParseMode
import com.github.kotlintelegrambot.entities.keyboard.InlineKeyboardButton
import ersbot.config.BotState
import ersbot.config.BotConfig
import ersbot.config.BotState.activeMenus
import ersbot.services.SubscriptionGuard
import ersbot.config.mainMenuText
import ersbot.keyboards.getMainMenuKeyboard
import ersbot.keyboards.getCartKeyboard
import services.getMonthlyStats
import services.calculateSalary

fun registerCommands(dispatcher: Dispatcher) {
    with(dispatcher) {
        command("reload") {
            val userId = message.from?.id
            if (userId == BotConfig.ADMIN_ID) {
                try {
                    BotState.reloadCatalog()
                    bot.sendMessage(ChatId.fromId(message.chat.id), "✅ Данные в таблице обновлены!")
                } catch (e: Exception) {
                    bot.sendMessage(ChatId.fromId(message.chat.id), "❌ Ошибка: ${e.message}")
                    e.printStackTrace()
                }
            }
        }

        command("start") {

            if (!SubscriptionGuard.requireSubscription(bot, message)) {
                return@command
            }

            val chatId = message.chat.id
            bot.deleteMessage(ChatId.fromId(chatId), message.messageId)
            BotState.activeMenus[chatId]?.let { bot.deleteMessage(ChatId.fromId(chatId), it) }
            BotState.userCarts.remove(chatId)
            BotState.currentSelections.remove(chatId)

            bot.sendMessage(
                chatId = ChatId.fromId(chatId),
                text = "✅ Бот обновлен и готов к работе!",
                replyMarkup = KeyboardReplyMarkup(
                    listOf(
                        listOf(KeyboardButton("🔄 Перезапустить бота"))
                    ),
                    resizeKeyboard = true
                )
            )
            val result = bot.sendMessage(
                chatId = ChatId.fromId(chatId),
                text = mainMenuText,
                replyMarkup = getMainMenuKeyboard(),
                parseMode = ParseMode.HTML,
                disableWebPagePreview = true
            )
            result.getOrNull()?.messageId?.let { activeMenus[chatId] = it }
        }

        command("cart") {
            val chatId = message.chat.id
            val cart = BotState.userCarts[chatId]

            if (cart.isNullOrEmpty()) {
                bot.sendMessage(
                    chatId = ChatId.fromId(chatId),
                    text = "🛒 <b>Ваша корзина пуста</b>\nДобавьте товары из каталога!",
                    parseMode = ParseMode.HTML,
                    replyMarkup = InlineKeyboardMarkup.create(
                        listOf(
                            listOf(InlineKeyboardButton.CallbackData("📦 Перейти в каталог", "orderBtn")),
                            listOf(InlineKeyboardButton.CallbackData("🔙 В меню", "backToMenuBtn"))
                        )
                    )
                )
            } else {
                val total = cart.sumOf { it.price }
                val itemsText = cart.mapIndexed { i, item ->
                    "${i + 1}. ${item.category} • ${item.brand} • ${item.flavor} - <b>${item.price}₽</b>"
                }.joinToString("\n")

                bot.sendMessage(
                    chatId = ChatId.fromId(chatId),
                    text = "🛒 <b>Ваша корзина</b>\n\n" +
                            "<b>Товары:</b>\n$itemsText\n\n" +
                            "━━━━━━━━━━━━━━━━\n" +
                            "💰 <b>Итого: $total₽</b>\n",
                    parseMode = ParseMode.HTML,
                    replyMarkup = getCartKeyboard(cart)
                )
            }
        }

        command("salary") {
            val chatId = message.chat.id

            if (chatId != BotConfig.ADMIN_ID) {
                bot.sendMessage(ChatId.fromId(chatId), text = "⛔ Эта команда доступна только администратору.")
                return@command
            }

            val stats = getMonthlyStats()
            val salary = calculateSalary(stats.netScore)

            val month = java.time.LocalDate.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("MMMM yyyy", java.util.Locale("ru")))

            bot.sendMessage(
                chatId = ChatId.fromId(chatId),
                text = "📊 <b>Отчёт за $month</b>\n\n" +
                        "📦 Завершено заказов: ${stats.orders}\n" +
                        "🟢 Плюсы: ${stats.pluses}\n" +
                        "🔴 Минусы: ${stats.minuses}\n" +
                        "🟡 Нейтрально: ${stats.neutrals}\n" +
                        "⚖️ Чистый баланс: ${stats.netScore}\n\n" +
                        "💰 Текущая зарплата: <b>${salary} ₽</b>\n\n" +
                        "💵 Наличными: ${stats.cashSum} ₽\n" +
                        "💳 Переводами: ${stats.transferSum} ₽",
                parseMode = ParseMode.HTML
            )
        }
    }
}
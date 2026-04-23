package ersbot.handlers

import com.github.kotlintelegrambot.dispatcher.Dispatcher
import com.github.kotlintelegrambot.dispatcher.command
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.KeyboardReplyMarkup
import com.github.kotlintelegrambot.entities.keyboard.KeyboardButton
import com.github.kotlintelegrambot.entities.ParseMode
import ersbot.config.BotState
import ersbot.config.BotConfig
import ersbot.config.BotState.activeMenus
import ersbot.config.BotState.catalog
import ersbot.config.mainMenuText
import ersbot.keyboards.getMainMenuKeyboard
import services.fetchCatalogFromSheets

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
    }
}
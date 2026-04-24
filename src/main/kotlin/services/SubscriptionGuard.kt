package ersbot.services

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.Message
import com.github.kotlintelegrambot.entities.ParseMode

object SubscriptionGuard {
    fun requireSubscription(bot: Bot, message: Message): Boolean {
        val userId = message.from?.id ?: return false
        val chatId = ChatId.fromId(message.chat.id)

        if (!SubscriptionService.isUserSubscribed(bot, userId)) {
            bot.sendMessage(
                chatId = chatId,
                text = SubscriptionService.getBlockMessage(),
                replyMarkup = SubscriptionService.getSubscribeKeyboard(),
                parseMode = ParseMode.HTML,
                disableWebPagePreview = true
            )
            return false
        }
        return true
    }

    fun requireSubscriptionForCallback(bot: Bot, chatId: Long, userId: Long): Boolean {
        if (!SubscriptionService.isUserSubscribed(bot, userId)) {
            bot.sendMessage(
                chatId = ChatId.fromId(chatId),
                text = SubscriptionService.getBlockMessage(),
                replyMarkup = SubscriptionService.getSubscribeKeyboard(),
                parseMode = ParseMode.HTML,
                disableWebPagePreview = true
            )
            return false
        }
        return true
    }
}
package ersbot.services

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.InlineKeyboardMarkup
import com.github.kotlintelegrambot.entities.keyboard.InlineKeyboardButton
import ersbot.config.BotConfig.TARGET_CHANNEL_ID
import ersbot.config.BotConfig.CHANNEL_LINK

object SubscriptionService {

    fun isUserSubscribed(bot: Bot, userId: Long): Boolean {

        return try {
            val result = bot.getChatMember(
                chatId = ChatId.fromId(TARGET_CHANNEL_ID),
                userId = userId
            )
            val member = result.getOrNull()
            member?.let { isMemberStatus(it.status) } ?: false
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun isMemberStatus(status: String): Boolean {
        return status in listOf("member", "creator", "administrator")
    }

    fun getSubscribeKeyboard() = InlineKeyboardMarkup.create(
        listOf(
            listOf(InlineKeyboardButton.Url("🔔 Подписаться на канал", CHANNEL_LINK)),
            listOf(InlineKeyboardButton.CallbackData("✅ Проверить подписку", "action:check_subscription")),
        )
    )

    fun getBlockMessage() = """
        ⚠️ <b>Доступ ограничен</b>
        
        Для использования бота необходимо быть подписанным на наш канал: <a href="$CHANNEL_LINK">@ERS</a>
        
        После подписки нажмите кнопку проверки ниже 👇
    """.trimIndent()
}
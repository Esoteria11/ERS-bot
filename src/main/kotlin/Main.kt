package ersbot

import ersbot.config.BotConfig
import ersbot.config.BotState
import ersbot.handlers.registerCallbacks
import ersbot.handlers.registerCommands
import ersbot.handlers.registerTextHandler
import com.github.kotlintelegrambot.bot
import com.github.kotlintelegrambot.dispatch

fun main() {
    BotState.reloadCatalog()

    bot {
        token = BotConfig.BOT_TOKEN

        dispatch {
            registerCommands(this)
            registerCallbacks(this)
            registerTextHandler(this)
        }
    }.startPolling()

    println("Бот запущен ✅")
}

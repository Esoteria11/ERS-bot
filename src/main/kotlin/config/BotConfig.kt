package ersbot.config

object BotConfig {
    val BOT_TOKEN = System.getenv("BOT_TOKEN")
        ?: error("BOT_TOKEN not defined")

    val ADMIN_ID = System.getenv("ADMIN_ID")?.toLongOrNull()
        ?: error("ADMIN_ID not defined")

    const val SHEETS_ID = "1WWesYlgWWC2DUy1Na0XfiT1-HxCwJ_yHxRUArc4vGoU"

    const val TARGET_CHANNEL_ID = 595625333L
    const val CHANNEL_LINK = "https://t.me/jdv2929"
}

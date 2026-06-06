package ersbot.services

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.ParseMode
import ersbot.config.BotConfig
import services.getWeeklyOrders

object ReportService {

    fun generateWeeklyReport(bot: Bot) {
        val orders = getWeeklyOrders()

        val totalOrders = orders.size
        if (totalOrders == 0) {
            bot.sendMessage(
                ChatId.fromId(BotConfig.ADMIN_ID),
                "📊 <b>Еженедельный отчёт:</b>\n\nЗа эту неделю заказов не было.",
                ParseMode.HTML
            )
            return
        }

        var totalRevenue = 0
        val productStats = mutableMapOf<String, Int>()
        val deliveryStats = mutableMapOf<String, Int>()
        var referralCount = 0
        val userOrders = mutableMapOf<Long, Int>()

        for (order in orders) {
            val amount = order["totalAmount"]?.toIntOrNull() ?: 0
            totalRevenue += amount

            val items = order["items"] ?: ""
            items.split(",").map { it.trim() }.filter { it.isNotBlank() }.forEach { item ->
                productStats[item] = productStats.getOrDefault(item, 0) + 1
            }

            // Считаем доставку
            val deliveryType = if (order["deliveryType"] == "courier") "🚚 Доставка" else "🏃‍♂️ Самовывоз"
            deliveryStats[deliveryType] = deliveryStats.getOrDefault(deliveryType, 0) + amount

            // Считаем рефералы
            if (order["referralUsed"] == "Yes") referralCount++

            val userId = order["userId"]?.toLongOrNull()
            if (userId != null) {
                userOrders[userId] = userOrders.getOrDefault(userId, 0) + 1
            }
        }

        val repeatBuyers = userOrders.count { it.value > 1 }
        val averageCheck = if (totalOrders > 0) totalRevenue / totalOrders else 0

        // Формируем текст отчёта
        val report = buildString {
            append("📊 <b>Еженедельный отчёт о продажах</b>\n\n")

            append("<b>1. Основная статистика:</b>\n")
            append("📦 Заказов: $totalOrders\n")
            append("💰 Выручка: $totalRevenue руб.\n")
            append("🧾 Средний чек: $averageCheck руб.\n\n")

            append("<b>2. Проданные товары:</b>\n")
            productStats.toSortedMap().forEach { (name, count) ->
                append("• $name: $count шт.\n")
            }
            append("\n")

            append("<b>3. Клиенты:</b>\n")
            append("🔄 Повторных покупателей: $repeatBuyers\n")
            append("🎁 Использовано реферальных кодов: $referralCount\n\n")

            append("<b>4. Способы получения:</b>\n")
            deliveryStats.forEach { (type, sum) ->
                append("$type: $sum руб.\n")
            }
        }

        // Отправляем менеджеру
        bot.sendMessage(
            ChatId.fromId(BotConfig.ADMIN_ID),
            report,
            ParseMode.HTML
        )

        println("✅ Еженедельный отчёт отправлен")
    }
}

package ersbot.config

import com.github.kotlintelegrambot.Bot
import ersbot.services.ReportService
import java.time.DayOfWeek
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

object Scheduler {

    fun startWeeklyReportScheduler(bot: Bot) {
        val scheduler = Executors.newScheduledThreadPool(1)

        val moscowZone = ZoneId.of("Europe/Moscow")
        val now = ZonedDateTime.now(moscowZone)

        var nextRun = now.withHour(10).withMinute(0).withSecond(0).withNano(0)

        if (now >= nextRun) {
            nextRun = nextRun.plusWeeks(1)
        }

        while (nextRun.dayOfWeek != DayOfWeek.MONDAY) {
            nextRun = nextRun.plusDays(1)
        }

        val initialDelay = ChronoUnit.MILLIS.between(now, nextRun)
        val period = TimeUnit.DAYS.toMillis(7)

        println("⏰ Отчёт настроен. Первый запуск: $nextRun")

        scheduler.scheduleAtFixedRate({
            try {
                println("⏰ Запуск генерации отчёта...")
                ReportService.generateWeeklyReport(bot)
            } catch (e: Exception) {
                println("❌ Ошибка при генерации отчёта: ${e.message}")
                e.printStackTrace()
            }
        }, initialDelay, period, TimeUnit.MILLISECONDS)
    }
}
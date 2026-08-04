package services

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.sheets.v4.Sheets
import com.google.api.services.sheets.v4.SheetsScopes
import com.google.api.services.sheets.v4.model.ValueRange
import com.google.auth.http.HttpCredentialsAdapter
import com.google.auth.oauth2.ServiceAccountCredentials
import ersbot.models.ProductDetails
import ersbot.config.BotConfig
import ersbot.models.PendingOrder

private fun createSheetsService(): Sheets {
    val credentialStream = object {}.javaClass.getResourceAsStream("/credentials.json")
        ?: throw Exception("Файл credentials.json не найден в папке resources!")

    val credentials = ServiceAccountCredentials.fromStream(credentialStream)
        .createScoped(listOf(SheetsScopes.SPREADSHEETS))

    return try {
        Sheets.Builder(
            GoogleNetHttpTransport.newTrustedTransport(),
            GsonFactory.getDefaultInstance(),
            HttpCredentialsAdapter(credentials)
        ).setApplicationName("CandyShopBot").build()
    } catch (e: Exception) {
        throw Exception("Ошибка инициализации Google Sheets: ${e.message}")
    }
}

fun fetchCatalogFromSheets(): Map<String, Map<String, ProductDetails>> {
    val spreadsheetId = BotConfig.SHEETS_ID
    val range = "Наличие!A2:G"

    val sheetsService = createSheetsService()

    val response = sheetsService.spreadsheets().values()
        .get(spreadsheetId, range)
        .execute()

    val rows = response.getValues() ?: return emptyMap()
    val newCatalog = mutableMapOf<String, MutableMap<String, ProductDetails>>()

    for (row in rows) {
        if (row.size < 6 || row[0].toString().isBlank()) continue

        val category = row[0].toString().trim()
        val brand = row[1].toString()
            .replace("\u00A0", " ")
            .replace("&amp;", "&")
            .trim()
            .replace(Regex("\\s+"), " ")
        val details = ProductDetails(
            price = row[2].toString().filter { it.isDigit() }.toIntOrNull() ?: 0,
            photoUrl = row[4].toString().trim(),
            flavors = row[5].toString().split(",").map { it.trim() }.filter { it.isNotBlank() },
            description = row.getOrNull(6)?.toString()?.trim() ?: "",
        )

        newCatalog.getOrPut(category) { mutableMapOf() }[brand] = details
    }

    println("✅ Каталог успешно загружен! Категорий: ${newCatalog.size}")
    return newCatalog
}

fun generateRandomCode(): String {
    val chars = ('A'..'Z') + ('0'..'9')
    val part1 = (1..3).map { chars.random() }.joinToString("")
    val part2 = (1..3).map { chars.random() }.joinToString("")
    return "ERS-$part1-$part2"
}

fun getOrCreateReferralCode(userId: Long, username: String?): String {
    val spreadsheetId = BotConfig.SHEETS_ID
    val sheetName = "Referrals"
    val range = "$sheetName!A2:E"

    val sheetsService = createSheetsService()
    val response = sheetsService.spreadsheets().values().get(spreadsheetId, range).execute()
    val rows = response.getValues()

    if (rows != null) {
        for (row in rows) {
            if (row.getOrNull(0).toString() == userId.toString()) {
                return row.getOrNull(2).toString()
            }
        }
    }

    val newCode = generateRandomCode()
    val newRow = listOf(
        userId.toString(),
        "@${username ?: "unknown"}",
        newCode,
        "0",
        "FALSE"
    )

    val body = ValueRange().setValues(listOf(newRow))
    sheetsService.spreadsheets().values()
        .append(spreadsheetId, "$sheetName!A1", body)
        .setValueInputOption("USER_ENTERED")
        .execute()

    return newCode
}

fun applyReferralCode(code: String): Pair<Long, Int>? {
    val spreadsheetId = BotConfig.SHEETS_ID
    val sheetName = "Referrals"
    val range = "$sheetName!A2:E"

    val sheetsService = createSheetsService()
    val response = sheetsService.spreadsheets().values().get(spreadsheetId, range).execute()
    val rows = response.getValues() ?: return null

    for (index in rows.indices) {
        val row = rows[index]
        if (row.getOrNull(2).toString() == code) {
            val ownerId = row[0].toString().toLongOrNull() ?: continue
            var currentInvites = row.getOrNull(3).toString().toIntOrNull() ?: 0
            currentInvites++

            val rowIndex = index + 2

            val updateRange = "$sheetName!D$rowIndex"
            val body = ValueRange().setValues(listOf(listOf(currentInvites.toString())))
            sheetsService.spreadsheets().values()
                .update(spreadsheetId, updateRange, body)
                .setValueInputOption("USER_ENTERED")
                .execute()

            if (currentInvites >= 5) {
                val discountRange = "$sheetName!E$rowIndex"
                val discountBody = ValueRange().setValues(listOf(listOf("TRUE")))
                sheetsService.spreadsheets().values()
                    .update(spreadsheetId, discountRange, discountBody)
                    .setValueInputOption("USER_ENTERED")
                    .execute()
            }

            return Pair(ownerId, currentInvites)
        }
    }
    return null
}

fun isReferralCodeValid(code: String, currentUserId: Long): Boolean {
    val spreadsheetId = BotConfig.SHEETS_ID
    val range = "Referrals!A2:C"

    val response = try {
        createSheetsService().spreadsheets().values().get(spreadsheetId, range).execute()
    } catch (e: Exception) {
        return false
    }
    val rows = response.getValues() ?: return false

    for (row in rows) {
        if (row.getOrNull(2).toString() == code) {
            return row.getOrNull(0).toString() != currentUserId.toString()
        }
    }
    return false
}

fun hasUserDiscount(userId: Long): Boolean {
    val spreadsheetId = BotConfig.SHEETS_ID
    val range = "Referrals!A2:E"

    val response = try {
        createSheetsService().spreadsheets().values().get(spreadsheetId, range).execute()
    } catch (e: Exception) {
        return false
    }
    val rows = response.getValues() ?: return false

    for (row in rows) {
        if (row.getOrNull(0).toString() == userId.toString()) {
            return row.getOrNull(4).toString().uppercase() == "TRUE"
        }
    }
    return false
}

fun consumeUserDiscount(userId: Long) {
    val spreadsheetId = BotConfig.SHEETS_ID
    val sheetName = "Referrals"
    val range = "$sheetName!A2:E"

    val sheetsService = createSheetsService()
    val response = try {
        sheetsService.spreadsheets().values().get(spreadsheetId, range).execute()
    } catch (e: Exception) {
        return
    }
    val rows = response.getValues() ?: return

    for (index in rows.indices) {
        if (rows[index].getOrNull(0).toString() == userId.toString()) {
            val rowIndex = index + 2

            val updateRange = "$sheetName!D$rowIndex:E$rowIndex"
            val body = ValueRange().setValues(listOf(listOf("0", "FALSE")))

            sheetsService.spreadsheets().values()
                .update(spreadsheetId, updateRange, body)
                .setValueInputOption("USER_ENTERED")
                .execute()
            return
        }
    }
}

// Сохранение заказа в историю
fun saveOrderToHistory(
    orderId: String,
    userId: Long,
    username: String?,
    items: String,
    totalAmount: Int,
    deliveryType: String?,
    referralCode: String?
) {
    val spreadsheetId = BotConfig.SHEETS_ID
    val sheetName = "OrdersHistory"

    val newRow = listOf(
        java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
        orderId,
        userId.toString(),
        username ?: "Unknown",
        items,
        totalAmount.toString(),
        deliveryType ?: "Unknown",
        if (referralCode != null) "Yes" else "No"
    )

    val sheetsService = createSheetsService()
    val body = ValueRange().setValues(listOf(newRow))

    sheetsService.spreadsheets().values()
        .append(spreadsheetId, "$sheetName!A1", body)
        .setValueInputOption("USER_ENTERED")
        .execute()

    println("✅ Заказ $orderId сохранён в историю")
}

fun getWeeklyOrders(): List<Map<String, String>> {
    val spreadsheetId = BotConfig.SHEETS_ID
    val sheetName = "OrdersHistory"
    val range = "$sheetName!A2:H"

    val sheetsService = createSheetsService()
    val response = sheetsService.spreadsheets().values().get(spreadsheetId, range).execute()
    val rows = response.getValues() ?: return emptyList()

    val oneWeekAgo = java.time.LocalDateTime.now().minusWeeks(1)
    val orders = mutableListOf<Map<String, String>>()

    // Список форматов, которые может выдать Google Sheets
    val dateFormats = listOf(
        java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd H:mm:ss"), // Sheets часто убирает ведущий ноль у часа
        java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss"),
        java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    )

    for (row in rows) {
        if (row.size < 8) continue

        val dateStr = row[0].toString().trim()
        if (dateStr.isBlank()) continue

        var parsedDate: java.time.LocalDateTime? = null
        for (fmt in dateFormats) {
            try {
                parsedDate = java.time.LocalDateTime.parse(dateStr, fmt)
                break
            } catch (_: Exception) { }
        }

        if (parsedDate == null) continue

        if (parsedDate.isAfter(oneWeekAgo)) {
            orders.add(mapOf(
                "date" to dateStr,
                "orderId" to row[1].toString(),
                "userId" to row[2].toString(),
                "username" to row[3].toString(),
                "items" to row[4].toString(),
                "totalAmount" to row[5].toString(),
                "deliveryType" to row[6].toString(),
                "referralUsed" to row[7].toString()
            ))
        }
    }
    return orders
}

fun updateInventoryAfterOrder(brand: String, flavor: String): Boolean {
    val spreadsheetId = BotConfig.SHEETS_ID
    val sheetName = "Наличие"

    val sheetsService = createSheetsService()
    val range = "$sheetName!A2:F"

    val response = sheetsService.spreadsheets().values().get(spreadsheetId, range).execute()
    val rows = response.getValues() ?: return false

    for ((rowIndex, row) in rows.withIndex()) {
        if (row.size < 6) continue

        val brandInTable = row[1].toString().trim()

        if (brandInTable.equals(brand, ignoreCase = true) ||
            brandInTable.contains(brand, ignoreCase = true)) {

            val flavorsCell = row[5].toString().trim()

            val flavorsList = flavorsCell.split(",").map { it.trim() }.filter { it.isNotBlank() }

            val updatedFlavors = mutableListOf<String>()
            var flavorFound = false
            var flavorRemoved = false

            for (flavorItem in flavorsList) {
                val flavorName = flavorItem.replace(Regex("\\s*\\d+шт"), "").trim()
                val orderedFlavorName = flavor.replace(Regex("\\s*\\d+шт"), "").trim()

                if (flavorName.equals(orderedFlavorName, ignoreCase = true)) {
                    flavorFound = true

                    val quantityMatch = Regex("(\\d+)шт").find(flavorItem)
                    val currentQty = quantityMatch?.groupValues?.get(1)?.toIntOrNull() ?: 1

                    val newQty = currentQty - 1

                    if (newQty > 0) {
                        updatedFlavors.add("$flavorName ${newQty}шт")
                    } else {
                        flavorRemoved = true
                    }
                } else {
                    updatedFlavors.add(flavorItem)
                }
            }

            if (!flavorFound) {
                println("⚠️ Вкус '$flavor' не найден в бренде '$brand'")
                return false
            }

            val actualRowIndex = rowIndex + 2
            val updateRange = "$sheetName!F$actualRowIndex"

            val newFlavorsText = if (updatedFlavors.isEmpty()) {
                ""
            } else {
                updatedFlavors.joinToString(", ")
            }

            val body = ValueRange().setValues(listOf(listOf(newFlavorsText)))
            sheetsService.spreadsheets().values()
                .update(spreadsheetId, updateRange, body)
                .setValueInputOption("USER_ENTERED")
                .execute()

            println("✅ Обновлён инвентарь: $brand - $flavor (осталось: $newFlavorsText)")
            return true
        }
    }

    println("⚠️ Бренд '$brand' не найден в таблице")
    return false
}

fun updateInventoryForOrder(items: List<ersbot.models.CartItem>): Map<String, Boolean> {
    val results = mutableMapOf<String, Boolean>()

    for (item in items) {
        val flavorWithQty = "${item.flavor} 1шт"
        val success = updateInventoryAfterOrder(item.brand, flavorWithQty)
        results["${item.brand} - ${item.flavor}"] = success
    }

    return results
}

data class MonthlyStats(
    val orders: Int,
    val pluses: Int,
    val minuses: Int,
    val neutrals: Int,
    val netScore: Int,
    val cashSum: Int,
    val transferSum: Int
)

fun saveOrderMetrics(order: PendingOrder) {
    val spreadsheetId = BotConfig.SHEETS_ID
    val sheetName = "OrderMetrics"

    val answerMinutes = if (order.answeredAt != null)
        java.time.Duration.between(order.createdAt, order.answeredAt).toMinutes() else null

    val assemblyMinutes = if (order.answeredAt != null && order.assembledAt != null)
        java.time.Duration.between(order.answeredAt, order.assembledAt).toMinutes() else null

    val month = java.time.LocalDate.now()
        .format(java.time.format.DateTimeFormatter.ofPattern("MMMM yyyy", java.util.Locale("ru")))

    val paymentTextForSheet = when (order.paymentType) {
        "cash" -> "Наличные"
        "transfer" -> "Перевод"
        "mixed" -> "Смешанная"
        else -> ""
    }

    val newRow = listOf(
        java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
        month,
        order.orderId,
        answerMinutes?.toString() ?: "",
        order.answerRating?.toString() ?: "",
        assemblyMinutes?.toString() ?: "",
        order.assemblyRating?.toString() ?: "",
        paymentTextForSheet,
        order.cashAmount?.toString() ?: "",
        order.transferAmount?.toString() ?: "",
        order.totalAmount.toString()
    )

    val sheetsService = createSheetsService()
    val body = ValueRange().setValues(listOf(newRow))
    sheetsService.spreadsheets().values()
        .append(spreadsheetId, "$sheetName!A1", body)
        .setValueInputOption("USER_ENTERED")
        .execute()

    println("✅ Метрики заказа ${order.orderId} записаны в OrderMetrics")
}

fun getMonthlyStats(): MonthlyStats {
    val spreadsheetId = BotConfig.SHEETS_ID
    val sheetName = "OrderMetrics"
    val range = "$sheetName!A2:K"

    val sheetsService = createSheetsService()
    val response = sheetsService.spreadsheets().values().get(spreadsheetId, range).execute()
    val rows = response.getValues() ?: return MonthlyStats(0, 0, 0, 0, 0, 0, 0)

    val currentMonth = java.time.LocalDate.now()
        .format(java.time.format.DateTimeFormatter.ofPattern("MMMM yyyy", java.util.Locale("ru")))

    var orders = 0; var pluses = 0; var minuses = 0; var neutrals = 0
    var netScore = 0; var cashSum = 0; var transferSum = 0

    for (row in rows) {
        if (row.size < 11) continue
        if (row[1].toString().trim() != currentMonth) continue

        orders++

        val answerRating = row[4].toString().toIntOrNull() ?: 0
        val assemblyRating = row[6].toString().toIntOrNull() ?: 0

        for (r in listOf(answerRating, assemblyRating)) {
            when (r) {
                1 -> pluses++
                -1 -> minuses++
                else -> neutrals++
            }
            netScore += r
        }

        val total = row[10].toString().toIntOrNull() ?: 0
        when (row[7].toString().trim()) {
            "Наличные" -> cashSum += total
            "Перевод" -> transferSum += total
            "Смешанная" -> {
                cashSum += row[8].toString().toIntOrNull() ?: 0
                transferSum += row[9].toString().toIntOrNull() ?: 0
            }
        }
    }

    return MonthlyStats(orders, pluses, minuses, neutrals, netScore, cashSum, transferSum)
}

fun calculateSalary(score: Int): Int {
    return when (score) {
        in 0..15 -> 5000
        in 16..31 -> 5500
        in 32..47 -> 6000
        in 48..63 -> 6500
        in 64..71 -> 7000
        in 72..79 -> 7500
        in 80..95 -> 8000
        in 96..111 -> 8500
        in 112..127 -> 9000
        in 128..143 -> 9500
        else -> if (score >= 144) 10000 else 5000
    }
}
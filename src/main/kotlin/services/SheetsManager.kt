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
        val brand = row[1].toString().trim()
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

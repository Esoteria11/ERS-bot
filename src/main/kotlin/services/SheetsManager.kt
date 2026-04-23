package services

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.sheets.v4.Sheets
import com.google.api.services.sheets.v4.SheetsScopes
import com.google.auth.http.HttpCredentialsAdapter
import com.google.auth.oauth2.ServiceAccountCredentials
import ersbot.models.ProductDetails
import ersbot.config.BotConfig

fun fetchCatalogFromSheets(): Map<String, Map<String, ProductDetails>> {
    val spreadsheetId = BotConfig.SHEETS_ID
    val range = "Sheet1!A2:F" // Читаем со 2-й строки, колонки A-F

    // 1. Подключаемся к файлу ключей
    val credentialStream = object {}.javaClass.getResourceAsStream("/credentials.json")
        ?: throw Exception("Файл credentials.json не найден в папке resources!")

    val credentials = ServiceAccountCredentials.fromStream(credentialStream)
        .createScoped(listOf(SheetsScopes.SPREADSHEETS_READONLY))

    // 2. Инициализируем сервис Google Sheets
    val sheetsService = try {
        Sheets.Builder(
            GoogleNetHttpTransport.newTrustedTransport(),
            GsonFactory.getDefaultInstance(),
            HttpCredentialsAdapter(credentials)
        ).setApplicationName("CandyShopBot").build()
    } catch (e: Exception) {
        throw Exception("Ошибка инициализации Google Sheets: ${e.message}")
    }

    // 3. Получаем данные
    val response = sheetsService.spreadsheets().values()
        .get(spreadsheetId, range)
        .execute()

    val rows = response.getValues() ?: return emptyMap()
    val newCatalog = mutableMapOf<String, MutableMap<String, ProductDetails>>()

    for (row in rows) {
        // Если в строке меньше 6 колонок или первая колонка пуста — пропускаем
        if (row.size < 6 || row[0].toString().isBlank()) continue

        val category = row[0].toString().trim()
        val brand = row[1].toString().trim()
        val details = ProductDetails(
            price = row[2].toString().filter { it.isDigit() }.toIntOrNull() ?: 0,
            photoUrl = row[4].toString().trim(),
            flavors = row[5].toString().split(",").map { it.trim() }.filter { it.isNotBlank() }
        )

        // Группируем по категориям
        newCatalog.getOrPut(category) { mutableMapOf() }[brand] = details
    }

    println("✅ Каталог успешно загружен! Категорий: ${newCatalog.size}")
    return newCatalog
}

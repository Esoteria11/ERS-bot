package ersbot.keyboards

import com.github.kotlintelegrambot.entities.InlineKeyboardMarkup
import com.github.kotlintelegrambot.entities.keyboard.InlineKeyboardButton
import data.ALL_STREETS
import ersbot.models.AddressStep

fun getMainMenuKeyboard(): InlineKeyboardMarkup {
    return InlineKeyboardMarkup.Companion.create(
        listOf(
            InlineKeyboardButton.CallbackData(text = "📦 Сделать заказ", callbackData = "orderBtn"),
            InlineKeyboardButton.CallbackData(text = "❓ Задать вопрос", callbackData = "questionBtn")
        ),
        listOf(InlineKeyboardButton.Url(text = "\uD83C\uDF81 Конкурс", "https://t.me/c/3784318482/11")),
        listOf(
            InlineKeyboardButton.Url(text = "⭐ Отзывы", url = "https://t.me/+EeJL0ODGc3gzOWFi"),
            InlineKeyboardButton.Url(text = "📢 Телеграм канал", url = "https://t.me/+0QYXuyrVVKwwMDhi")
        )
    )
}

fun getCityKeyboard(): InlineKeyboardMarkup {
    val cities = listOf("с. Александровское")
    return InlineKeyboardMarkup.Companion.create(
        cities.chunked(2).map { row ->
            row.map { city -> InlineKeyboardButton.CallbackData(city, "addr_city_$city") }
        } + listOf(listOf(InlineKeyboardButton.CallbackData("🔙 Назад к корзине", "checkout")))
    )
}

fun getStreetKeyboard(page: Int = 0): InlineKeyboardMarkup {
    val streetsPerPage = 8 // 8 кнопок + навигация = оптимальная высота экрана
    val totalPages = (ALL_STREETS.size + streetsPerPage - 1) / streetsPerPage
    val currentPage = page.coerceIn(0, totalPages - 1)

    val pageStreets = ALL_STREETS.drop(currentPage * streetsPerPage).take(streetsPerPage)
    val keyboardRows: MutableList<List<InlineKeyboardButton>> = mutableListOf()

    // Каждая улица — отдельная строка (занимает всю ширину)
    pageStreets.forEach { street ->
        val globalIndex = ALL_STREETS.indexOf(street)
        keyboardRows.add(
            listOf(InlineKeyboardButton.CallbackData(street, "street_$globalIndex"))
        )
    }

    // Навигация
    val navRow = mutableListOf<InlineKeyboardButton>()
    if (currentPage > 0) navRow.add(InlineKeyboardButton.CallbackData("⬅️", "streets_page_${currentPage - 1}"))
    navRow.add(InlineKeyboardButton.CallbackData("❌ Отмена", "addr_back_to_city"))
    if (currentPage < totalPages - 1) navRow.add(InlineKeyboardButton.CallbackData("➡️", "streets_page_${currentPage + 1}"))
    keyboardRows.add(navRow)

    return InlineKeyboardMarkup.Companion.create(keyboardRows)
}

fun getFlatKeyboard(): InlineKeyboardMarkup {
    return InlineKeyboardMarkup.Companion.create(
        listOf(
            listOf(InlineKeyboardButton.CallbackData("⏭️ Пропустить", "addr_skip_flat")),
            listOf(InlineKeyboardButton.CallbackData("🔙 Назад", "addr_back_to_house")) // ✅ Теперь ведёт на шаг назад
        )
    )
}

fun getBackKeyboardForStep(step: AddressStep): InlineKeyboardMarkup {
    val callback = when(step) {
        AddressStep.ENTER_STREET -> "addr_back_to_city"
        AddressStep.ENTER_HOUSE -> "streets_page_0"  // ← Возврат к выбору улицы
        AddressStep.ENTER_FLAT -> "addr_back_to_house"
        else -> "addr_start"
    }
    return InlineKeyboardMarkup.Companion.create(listOf(listOf(InlineKeyboardButton.CallbackData("🔙 Назад", callback))))
}

fun getAddressConfirmationKeyboard(): InlineKeyboardMarkup {
    return InlineKeyboardMarkup.Companion.create(
        listOf(
            listOf(InlineKeyboardButton.CallbackData("✅ Подтвердить адрес", "addr_confirmed")),
            listOf(InlineKeyboardButton.CallbackData("✏️ Изменить адрес", "addr_start")),
            listOf(InlineKeyboardButton.CallbackData("❌ Отменить доставку", "cancelDelivery"))
        )
    )
}

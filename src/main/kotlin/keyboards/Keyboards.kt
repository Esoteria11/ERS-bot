package ersbot.keyboards

import com.github.kotlintelegrambot.entities.InlineKeyboardMarkup
import com.github.kotlintelegrambot.entities.keyboard.InlineKeyboardButton
import data.ALL_STREETS
import ersbot.config.BotState
import ersbot.models.AddressStep
import ersbot.models.CartItem

fun getMainMenuKeyboard(): InlineKeyboardMarkup {
    return InlineKeyboardMarkup.create(
        listOf(
            InlineKeyboardButton.CallbackData(text = "📦 Сделать заказ", callbackData = "orderBtn")
        ),
        listOf(
            InlineKeyboardButton.CallbackData(text = "\uD83D\uDD52 Расписание", callbackData = "scheduleBtn"),
            InlineKeyboardButton.CallbackData(text = "❓ Задать вопрос", callbackData = "questionBtn")
        ),
        listOf(
            InlineKeyboardButton.Url(text = "📢 Наш канал", url = "https://t.me/+0QYXuyrVVKwwMDhi"),
            InlineKeyboardButton.Url(text = "⭐ Отзывы", url = "https://t.me/+EeJL0ODGc3gzOWFi")
        ),
        listOf(
            InlineKeyboardButton.CallbackData(text = "🎁 Реферальная система", callbackData = "refSystemBtn")
        )
    )
}

fun getCategoryKeyboardWithCart(chatId: Long, categories: List<String>): InlineKeyboardMarkup {
    val buttons = categories.map { listOf(InlineKeyboardButton.CallbackData(it, "c_$it")) }.toMutableList()

    val cartSize = BotState.userCarts[chatId]?.size ?: 0
    if (cartSize > 0) {
        buttons.add(
            listOf(InlineKeyboardButton.CallbackData("🛒 Корзина ($cartSize шт.)", "view_cart"))
        )
    }

    buttons.add(listOf(InlineKeyboardButton.CallbackData("🔙 В главное меню", "backToMenuBtn")))
    return InlineKeyboardMarkup.create(buttons)
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

    pageStreets.forEach { street ->
        val globalIndex = ALL_STREETS.indexOf(street)
        keyboardRows.add(
            listOf(InlineKeyboardButton.CallbackData(street, "street_$globalIndex"))
        )
    }

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

fun getDeliveryKeyboard(chatId: Long): InlineKeyboardMarkup {
    val buttons = mutableListOf<List<InlineKeyboardButton>>()

    buttons.add(listOf(InlineKeyboardButton.CallbackData("🏃‍♂️ Самовывоз (Бесплатно)", "del_pickup")))
    buttons.add(listOf(InlineKeyboardButton.CallbackData("🚚 Доставка (+220р)", "del_courier")))

    val savedAddr = ersbot.config.BotState.userLastAddresses[chatId]
    if (savedAddr != null) {
        val addrText = "${savedAddr.city}, ${savedAddr.street}, д. ${savedAddr.house}"
        buttons.add(listOf(InlineKeyboardButton.CallbackData(" $addrText", "use_saved_address")))
    }

    buttons.add(listOf(InlineKeyboardButton.CallbackData("🛒 Вернуться к корзине", "view_cart")))

    return InlineKeyboardMarkup.create(buttons)
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

fun getManagerOrderButtons(orderId: String): InlineKeyboardMarkup {
    return InlineKeyboardMarkup.Companion.create(
        listOf(
            listOf(InlineKeyboardButton.CallbackData("✅ Оплатил", "admin_paid_$orderId")),
            listOf(InlineKeyboardButton.CallbackData("❌ Отказался", "admin_refused_$orderId"))
        )
    )
}

fun getCartKeyboard(cart: List<CartItem>): InlineKeyboardMarkup {
    val total = cart.sumOf { it.price }

    val buttons = listOf(
        listOf(
            InlineKeyboardButton.CallbackData("🗑️ Удалить товар", "cart_delete_select"),
            InlineKeyboardButton.CallbackData("➕ Добавить товар", "orderBtn")
        ),
        listOf(
            InlineKeyboardButton.CallbackData("❌ Очистить всё", "cart_clear"),
            InlineKeyboardButton.CallbackData("✅ Оформить заказ", "checkout")
        ),
        listOf(
            InlineKeyboardButton.CallbackData("🔙 Назад в меню", "backToMenuBtn")
        )
    )

    return InlineKeyboardMarkup.create(buttons)
}

fun getCartItemWithQtyKeyboard(cart: List<CartItem>, index: Int): InlineKeyboardMarkup {
    val item = cart[index]
    val buttons = mutableListOf<List<InlineKeyboardButton>>()

    buttons.add(
        listOf(
            InlineKeyboardButton.CallbackData("◀️", "cart_dec_$index"),
            InlineKeyboardButton.CallbackData("${item.brand} - ${item.price}₽", "noop"),
            InlineKeyboardButton.CallbackData("▶️", "cart_inc_$index")
        )
    )

    buttons.add(
        listOf(
            InlineKeyboardButton.CallbackData("🗑️ Удалить товар", "cart_remove_$index")
        )
    )

    buttons.add(
        listOf(
            InlineKeyboardButton.CallbackData("🔙 Назад к корзине", "view_cart")
        )
    )

    return InlineKeyboardMarkup.create(buttons)
}

fun getDeleteSelectionKeyboard(cart: List<CartItem>): InlineKeyboardMarkup {
    val buttons = cart.mapIndexed { index, item ->
        InlineKeyboardButton.CallbackData(
            "${index + 1}️⃣ ${item.brand} (${item.flavor})",
            "cart_delete_$index"
        )
    }

    return InlineKeyboardMarkup.create(
        buttons.chunked(1) +
                listOf(listOf(InlineKeyboardButton.CallbackData("❌ Отмена", "cart_delete_cancel")))
    )
}

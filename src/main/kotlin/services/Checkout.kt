package ersbot.services

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.InlineKeyboardMarkup
import com.github.kotlintelegrambot.entities.ParseMode
import com.github.kotlintelegrambot.entities.keyboard.InlineKeyboardButton
import ersbot.config.BotState
import ersbot.models.*
import ersbot.keyboards.*

fun renderCheckout(
    bot: Bot,
    chatId: Long,
    msgId: Long,
    cart: List<CartItem>?,
    selection: CurrentSelection
) {
    if (cart.isNullOrEmpty()) {
        bot.editMessageText(
            chatId = ChatId.fromId(chatId), messageId = msgId,
            text = "Ой, кажется ваша корзина пуста! Пожалуйста, начните оформление заказа заново 🔄",
            replyMarkup = InlineKeyboardMarkup.create(
                listOf(
                    listOf(InlineKeyboardButton.CallbackData("📦 В каталог", "orderBtn")),
                    listOf(InlineKeyboardButton.CallbackData("🔙 В меню", "backToMenuBtn"))
                )
            )
        )
        return
    }

    var totalSum = cart.sumOf { it.price }
    var receipt = "🛒 <b>Товары для заказа:</b>\n\n"

    cart.forEachIndexed { i, item ->
        receipt += "${i + 1}. ${item.category} ${item.brand} - ${item.flavor} (<b>${item.price} руб.</b>)\n"
    }

    if (selection.isDelivery) {
        if (selection.deliveryType == "pickup") {
            receipt += "\n🏃‍♂️<b>Способ получения:</b> Самовывоз (бесплатно)"
            receipt += "\n<i>❗Самовывоз осуществляется только по договорённости с менеджером @ERS_rrs ❗</i>"
        } else if (selection.deliveryType == "courier") {
            totalSum += 220
            receipt += "\n🚚 <b>Доставка:</b> 220 руб."
            if (selection.addressInput?.isComplete() == true) {
                val addr = selection.addressInput!!
                receipt += "\n📍 <b>Адрес:</b> ${addr.city}, ${addr.street}, д. ${addr.house}${addr.flat?.let { ", кв. $it" } ?: ""}"
            } else {
                receipt += "\n📍 <b>Адрес:</b> <i>не указан</i>"
            }
            receipt += "\n🕒 <b>Дата и Время:</b> ${selection.datetime.ifBlank { "не указано" }}"
        }
    }
    receipt += "\n\n💰 <b>ИТОГО К ОПЛАТЕ: $totalSum руб.</b>"

    val buttons = mutableListOf<List<InlineKeyboardButton>>()

    if (!selection.isDelivery) {
        receipt += "\n\n⚠️ <i>Для завершения заказа необходимо выбрать способ получения.</i>"
        buttons.add(listOf(InlineKeyboardButton.CallbackData("🚚 Выбрать способ получения", "startDelivery")))
        buttons.add(listOf(InlineKeyboardButton.CallbackData("🛒 Вернуться к корзине", "view_cart")))

        bot.editMessageText(
            chatId = ChatId.fromId(chatId), messageId = msgId, text = receipt,
            parseMode = ParseMode.HTML, replyMarkup = InlineKeyboardMarkup.create(buttons)
        )
        return
    }

    if (selection.deliveryType == "courier") {
        if (selection.addressInput?.isComplete() != true || selection.datetime.isBlank()) {
            val btnText = if (selection.addressInput?.isComplete() == true) {
                "🕒 Указать время доставки"
            } else {
                "📍 Указать адрес и время доставки"
            }
            receipt += "\n\n⚠️ <i>Заполните адрес и время доставки для завершения заказа.</i>"

            buttons.add(listOf(InlineKeyboardButton.CallbackData(btnText, "addr_start")))
            buttons.add(listOf(InlineKeyboardButton.CallbackData("🔙 Изменить способ получения", "cancelDelivery")))
            buttons.add(listOf(InlineKeyboardButton.CallbackData("🛒 Вернуться к корзине", "view_cart")))

            bot.editMessageText(
                chatId = ChatId.fromId(chatId), messageId = msgId, text = receipt,
                parseMode = ParseMode.HTML, replyMarkup = InlineKeyboardMarkup.create(buttons)
            )
            return
        }
    }

    receipt += "\n\n✅ <i>Всё готово к оформлению!</i>"

    buttons.add(listOf(InlineKeyboardButton.CallbackData("✅ Оформить заказ", "submitFinal")))
    buttons.add(listOf(InlineKeyboardButton.CallbackData("🤝 Ввести код друга", "enterRefCodeBtn")))
    buttons.add(listOf(InlineKeyboardButton.CallbackData("🔙 Изменить способ получения", "cancelDelivery")))
    buttons.add(listOf(InlineKeyboardButton.CallbackData("🛒 Вернуться к корзине", "view_cart")))

    bot.editMessageText(
        chatId = ChatId.fromId(chatId), messageId = msgId, text = receipt,
        parseMode = ParseMode.HTML, replyMarkup = InlineKeyboardMarkup.create(buttons)
    )
}

fun finalizeAddressInput(
    bot: Bot,
    chatId: Long,
    menuId: Long?,
    selection: CurrentSelection,
    cart: List<CartItem>?
) {
    selection.addressStep = AddressStep.CONFIRM
    val address = selection.addressInput ?: return
    val confirmationText = """
        ✅ <b>Адрес подтверждён:</b>
        ${address.formatForUser()}
        
    💡 Нажмите «Подтвердить», чтобы продолжить, или «Изменить», чтобы исправить адрес.
    """.trimIndent()

    menuId?.let {
        bot.editMessageText(
            chatId = ChatId.fromId(chatId), messageId = it,
            text = confirmationText, parseMode = ParseMode.HTML,
            replyMarkup = getAddressConfirmationKeyboard()
        )
    }
}

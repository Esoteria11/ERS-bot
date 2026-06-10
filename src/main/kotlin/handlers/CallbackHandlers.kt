package ersbot.handlers

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.dispatcher.Dispatcher
import com.github.kotlintelegrambot.dispatcher.callbackQuery
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.InlineKeyboardMarkup
import com.github.kotlintelegrambot.entities.KeyboardReplyMarkup
import com.github.kotlintelegrambot.entities.keyboard.KeyboardButton
import com.github.kotlintelegrambot.entities.ParseMode
import com.github.kotlintelegrambot.entities.keyboard.InlineKeyboardButton
import data.ALL_STREETS
import ersbot.services.*
import ersbot.config.BotConfig
import ersbot.config.BotState
import ersbot.config.BotState.activeMenus
import ersbot.config.BotState.catalog
import ersbot.config.BotState.currentSelections
import ersbot.config.BotState.userCarts
import ersbot.config.mainMenuText
import ersbot.keyboards.*
import ersbot.models.*
import services.*

fun escapeHtml(text: String): String {
    return text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
}

fun registerCallbacks(dispatcher: Dispatcher) {
    with(dispatcher) {

        callbackQuery("action:check_subscription") {
            val chatId = callbackQuery.message?.chat?.id ?: return@callbackQuery
            val userId = callbackQuery.from?.id ?: return@callbackQuery
            val msgId = callbackQuery.message?.messageId

            if (SubscriptionService.isUserSubscribed(bot, userId)) {
                msgId?.let { bot.deleteMessage(ChatId.fromId(chatId), it) }

                bot.sendMessage(
                    chatId = ChatId.fromId(chatId),
                    text = "✅ Бот обновлен и готов к работе!",
                    replyMarkup = KeyboardReplyMarkup(
                        listOf(
                            listOf(KeyboardButton("🔄 Перезапустить бота"))
                        ),
                        resizeKeyboard = true
                    )
                )

                val result = bot.sendMessage(
                    chatId = ChatId.fromId(chatId),
                    text = mainMenuText,
                    replyMarkup = getMainMenuKeyboard(),
                    parseMode = ParseMode.HTML,
                    disableWebPagePreview = true
                )

                result.getOrNull()?.messageId?.let { activeMenus[chatId] = it }
            } else {
                bot.answerCallbackQuery(
                    callbackQueryId = callbackQuery.id!!,
                    text = "⚠️ Вы не подписаны на канал ERS",
                    showAlert = true
                )
            }
        }

        callbackQuery("orderBtn") {
            if (!checkSubAndReturn(bot, callbackQuery)) return@callbackQuery
            val chatId = callbackQuery.message?.chat?.id ?: return@callbackQuery
            val msgId = callbackQuery.message?.messageId ?: return@callbackQuery
            currentSelections[chatId] = CurrentSelection()

            val categoryButtons = getCategoryKeyboardWithCart(chatId, catalog.keys.toList())

            bot.editMessageText(
                chatId = ChatId.fromId(chatId), messageId = msgId, text = "Отлично! Выбери категорию:",
                replyMarkup = categoryButtons, disableWebPagePreview = false
            )
        }

        callbackQuery("scheduleBtn") {
            if (!checkSubAndReturn(bot, callbackQuery)) return@callbackQuery
            val chatId = callbackQuery.message?.chat?.id ?: return@callbackQuery
            val msgId = callbackQuery.message?.messageId ?: return@callbackQuery

            val scheduleText = """
                🕒 <b>График работы магазина:</b>
                
                <b>Пн - Пт</b>: 08:00 - 01:00
                <b>Сб</b>: Круглосуточно
                <b>Вс</b>: 08:00 - 01:00
            """.trimIndent()

            bot.editMessageText(
                chatId = ChatId.fromId(chatId),
                messageId = msgId,
                text = scheduleText,
                parseMode = ParseMode.HTML,
                replyMarkup = InlineKeyboardMarkup.create(
                    listOf(
                        listOf(InlineKeyboardButton.CallbackData("🔙 Назад в меню", "backToMenuBtn"))
                    )
                )
            )
        }

        callbackQuery("questionBtn") {
            if (!checkSubAndReturn(bot, callbackQuery)) return@callbackQuery
            val chatId = callbackQuery.message?.chat?.id ?: return@callbackQuery
            val msgId = callbackQuery.message?.messageId ?: return@callbackQuery
            bot.editMessageText(
                chatId = ChatId.fromId(chatId), messageId = msgId,
                text = "Задавай свой вопрос здесь: @ERS_rrs, менеджер ответит в ближайшее время!",
                replyMarkup = InlineKeyboardMarkup.create(
                    listOf(
                        listOf(InlineKeyboardButton.CallbackData("🔙 Назад в меню", "backToMenuBtn"))
                    )
                )
            )
        }

        callbackQuery("backToMenuBtn") {
            if (!checkSubAndReturn(bot, callbackQuery)) return@callbackQuery
            val chatId = callbackQuery.message?.chat?.id ?: return@callbackQuery
            val msgId = callbackQuery.message?.messageId ?: return@callbackQuery
            currentSelections.remove(chatId)
            bot.editMessageText(
                chatId = ChatId.fromId(chatId),
                messageId = msgId,
                text = mainMenuText,
                parseMode = ParseMode.HTML,
                replyMarkup = getMainMenuKeyboard(),
                disableWebPagePreview = true
            )
        }

        callbackQuery("view_cart") {
            if (!checkSubAndReturn(bot, callbackQuery)) return@callbackQuery
            val chatId = callbackQuery.message?.chat?.id ?: return@callbackQuery
            val msgId = callbackQuery.message?.messageId ?: return@callbackQuery
            val cart = userCarts[chatId]

            if (cart.isNullOrEmpty()) {
                bot.editMessageText(
                    chatId = ChatId.fromId(chatId),
                    messageId = msgId,
                    text = "🛒 <b>Ваша корзина пуста.</b>\nДобавьте товары из каталога!",
                    parseMode = ParseMode.HTML,
                    replyMarkup = InlineKeyboardMarkup.create(
                        listOf(
                            listOf(InlineKeyboardButton.CallbackData("📦 Перейти в каталог", "orderBtn")),
                            listOf(InlineKeyboardButton.CallbackData("🔙 В меню", "backToMenuBtn"))
                        )
                    )
                )
            } else {
                val total = cart.sumOf { it.price }
                val itemsText = cart.mapIndexed { i, item ->
                    "${i + 1}. ${item.category} • ${item.brand} • ${item.flavor} - <b>${item.price}₽</b>"
                }.joinToString("\n")

                bot.editMessageText(
                    chatId = ChatId.fromId(chatId),
                    messageId = msgId,
                    text = "🛒 <b>Ваша корзина</b>\n\n" +
                            "<b>Товары:</b>\n$itemsText\n\n" +
                            "━━━━━━━━━━━━━━━━\n" +
                            "💰 <b>Итого: $total₽</b>\n",
                    parseMode = ParseMode.HTML,
                    replyMarkup = getCartKeyboard(cart)
                )
            }
        }

        callbackQuery {
            val data = callbackQuery.data
            if (!data.startsWith("cart_remove_")) return@callbackQuery

            val chatId = callbackQuery.message?.chat?.id ?: return@callbackQuery
            val msgId = callbackQuery.message?.messageId ?: return@callbackQuery
            val index = data.removePrefix("cart_remove_").toIntOrNull() ?: return@callbackQuery

            val cart = userCarts[chatId]
            if (cart != null && index in cart.indices) {
                val removedItem = cart.removeAt(index)

                if (cart.isEmpty()) {
                    bot.editMessageText(
                        chatId = ChatId.fromId(chatId),
                        messageId = msgId,
                        text = "🗑️ <b>${removedItem.brand} (${removedItem.flavor})</b> удалён.\n\nКорзина пуста.",
                        parseMode = ParseMode.HTML,
                        replyMarkup = InlineKeyboardMarkup.create(
                            listOf(
                                listOf(InlineKeyboardButton.CallbackData("📦 Сделать заказ", "orderBtn")),
                                listOf(InlineKeyboardButton.CallbackData("🔙 В меню", "backToMenuBtn"))
                            )
                        )
                    )
                } else {
                    val total = cart.sumOf { it.price }
                    val itemsText = cart.mapIndexed { i, item ->
                        "${i + 1}. ${item.category} • ${item.brand} • ${item.flavor} - <b>${item.price}₽</b>"
                    }.joinToString("\n")

                    bot.editMessageText(
                        chatId = ChatId.fromId(chatId),
                        messageId = msgId,
                        text = "✅ <b>${removedItem.brand} (${removedItem.flavor})</b> удалён.\n\n" +
                                "<b>Товары:</b>\n$itemsText\n\n" +
                                "━━━━━━━━━━━━━━━━\n" +
                                "💰 <b>Итого: $total₽</b>\n",
                        parseMode = ParseMode.HTML,
                        replyMarkup = getCartKeyboard(cart)
                    )
                }
            }
        }

        callbackQuery("cart_clear") {
            if (!checkSubAndReturn(bot, callbackQuery)) return@callbackQuery
            val chatId = callbackQuery.message?.chat?.id ?: return@callbackQuery
            val msgId = callbackQuery.message?.messageId ?: return@callbackQuery

            userCarts.remove(chatId)

            bot.editMessageText(
                chatId = ChatId.fromId(chatId),
                messageId = msgId,
                text = "🧹 <b>Корзина была очищена.</b>\n",
                parseMode = ParseMode.HTML,
                replyMarkup = InlineKeyboardMarkup.create(
                    listOf(
                        listOf(InlineKeyboardButton.CallbackData("📦 Сделать заказ", "orderBtn")),
                        listOf(InlineKeyboardButton.CallbackData("🔙 В главное меню", "backToMenuBtn"))
                    )
                )
            )
        }

        callbackQuery("cart_delete_select") {
            if (!checkSubAndReturn(bot, callbackQuery)) return@callbackQuery
            val chatId = callbackQuery.message?.chat?.id ?: return@callbackQuery
            val msgId = callbackQuery.message?.messageId ?: return@callbackQuery
            val cart = userCarts[chatId]

            if (cart.isNullOrEmpty()) {
                bot.answerCallbackQuery(callbackQuery.id, "Корзина пуста!", showAlert = true)
                return@callbackQuery
            }

            if (cart.size == 1) {
                val removedItem = cart.removeAt(0)
                userCarts.remove(chatId)

                bot.editMessageText(
                    chatId = ChatId.fromId(chatId),
                    messageId = msgId,
                    text = "🗑️ <b>${removedItem.brand} (${removedItem.flavor})</b> удалён.\n\nКорзина пуста.",
                    parseMode = ParseMode.HTML,
                    replyMarkup = InlineKeyboardMarkup.create(
                        listOf(
                            listOf(InlineKeyboardButton.CallbackData("📦 Сделать заказ", "orderBtn")),
                            listOf(InlineKeyboardButton.CallbackData("🔙 В меню", "backToMenuBtn"))
                        )
                    )
                )
                return@callbackQuery
            }

            bot.editMessageText(
                chatId = ChatId.fromId(chatId),
                messageId = msgId,
                text = "❓ <b>Какой товар желаете удалить?</b>\n\n" +
                        cart.mapIndexed { i, item ->
                            "${i + 1}. ${item.category} • ${item.brand} • ${item.flavor} - ${item.price}₽"
                        }.joinToString("\n"),
                parseMode = ParseMode.HTML,
                replyMarkup = getDeleteSelectionKeyboard(cart)
            )
        }

        callbackQuery {
            val data = callbackQuery.data
            if (!data.startsWith("cart_delete_")) return@callbackQuery
            if (data == "cart_delete_select") return@callbackQuery
            if (data == "cart_delete_cancel") return@callbackQuery

            val chatId = callbackQuery.message?.chat?.id ?: return@callbackQuery
            val msgId = callbackQuery.message?.messageId ?: return@callbackQuery
            val index = data.removePrefix("cart_delete_").toIntOrNull() ?: return@callbackQuery

            val cart = userCarts[chatId]
            if (cart != null && index in cart.indices) {
                val removedItem = cart.removeAt(index)

                if (cart.isEmpty()) {
                    bot.editMessageText(
                        chatId = ChatId.fromId(chatId),
                        messageId = msgId,
                        text = "🗑️ <b>${removedItem.brand} (${removedItem.flavor})</b> удалён.\n\nКорзина пуста.",
                        parseMode = ParseMode.HTML,
                        replyMarkup = InlineKeyboardMarkup.create(
                            listOf(
                                listOf(InlineKeyboardButton.CallbackData("📦 Сделать заказ", "orderBtn")),
                                listOf(InlineKeyboardButton.CallbackData("🔙 В меню", "backToMenuBtn"))
                            )
                        )
                    )
                } else {
                    val total = cart.sumOf { it.price }
                    val itemsText = cart.mapIndexed { i, item ->
                        "${i + 1}. ${item.category} • ${item.brand} • ${item.flavor} - <b>${item.price}₽</b>"
                    }.joinToString("\n")

                    bot.editMessageText(
                        chatId = ChatId.fromId(chatId),
                        messageId = msgId,
                        text = "✅ <b>${removedItem.brand} (${removedItem.flavor})</b> удалён.\n\n" +
                                "<b>Товары:</b>\n$itemsText\n\n" +
                                "━━━━━━━━━━━━━━━━\n" +
                                "💰 <b>Итого: $total₽</b>\n",
                        parseMode = ParseMode.HTML,
                        replyMarkup = getCartKeyboard(cart)
                    )
                }
            }
        }

        callbackQuery("cart_delete_cancel") {
            if (!checkSubAndReturn(bot, callbackQuery)) return@callbackQuery
            val chatId = callbackQuery.message?.chat?.id ?: return@callbackQuery
            val msgId = callbackQuery.message?.messageId ?: return@callbackQuery
            val cart = userCarts[chatId]

            if (cart.isNullOrEmpty()) {
                bot.editMessageText(
                    chatId = ChatId.fromId(chatId),
                    messageId = msgId,
                    text = "🛒 <b>Ваша корзина пуста</b>\nДобавьте товары из каталога!",
                    parseMode = ParseMode.HTML,
                    replyMarkup = InlineKeyboardMarkup.create(
                        listOf(
                            listOf(InlineKeyboardButton.CallbackData("📦 Перейти в каталог", "orderBtn")),
                            listOf(InlineKeyboardButton.CallbackData("🔙 В меню", "backToMenuBtn"))
                        )
                    )
                )
            } else {
                val total = cart.sumOf { it.price }
                val itemsText = cart.mapIndexed { i, item ->
                    "${i + 1}. ${item.category} • ${item.brand} • ${item.flavor} - <b>${item.price}₽</b>"
                }.joinToString("\n")

                bot.editMessageText(
                    chatId = ChatId.fromId(chatId),
                    messageId = msgId,
                    text = "🛒 <b>Ваша корзина</b>\n\n" +
                            "<b>Товары:</b>\n$itemsText\n\n" +
                            "━━━━━━━━━━━━━━━━\n" +
                            "💰 <b>Итого: $total₽</b>\n",
                    parseMode = ParseMode.HTML,
                    replyMarkup = getCartKeyboard(cart)
                )
            }
        }

        callbackQuery("checkout") {
            if (!checkSubAndReturn(bot, callbackQuery)) return@callbackQuery
            val chatId = callbackQuery.message?.chat?.id ?: return@callbackQuery
            val msgId = callbackQuery.message?.messageId ?: return@callbackQuery
            val cart = userCarts[chatId]
            val selection = currentSelections[chatId] ?: return@callbackQuery
            renderCheckout(bot, chatId, msgId, cart, selection)
        }

        callbackQuery("startDelivery") {
            if (!checkSubAndReturn(bot, callbackQuery)) return@callbackQuery
            val chatId = callbackQuery.message?.chat?.id ?: return@callbackQuery
            val msgId = callbackQuery.message?.messageId ?: return@callbackQuery
            bot.editMessageText(
                chatId = ChatId.fromId(chatId), messageId = msgId,
                text = "Выберите способ получения заказа:",
                replyMarkup = getDeliveryKeyboard(chatId)
            )
        }

        callbackQuery("del_pickup") {
            if (!checkSubAndReturn(bot, callbackQuery)) return@callbackQuery
            val chatId = callbackQuery.message?.chat?.id ?: return@callbackQuery
            val msgId = callbackQuery.message?.messageId ?: return@callbackQuery
            val selection = currentSelections[chatId] ?: return@callbackQuery
            selection.isDelivery = true
            selection.deliveryType = "pickup"
            selection.state = UserState.IDLE
            renderCheckout(bot, chatId, msgId, userCarts[chatId], selection)
        }

        callbackQuery("del_courier") {
            if (!checkSubAndReturn(bot, callbackQuery)) return@callbackQuery
            val chatId = callbackQuery.message?.chat?.id ?: return@callbackQuery
            val msgId = callbackQuery.message?.messageId ?: return@callbackQuery
            val selection = currentSelections[chatId] ?: return@callbackQuery
            selection.isDelivery = true
            selection.deliveryType = "courier"
            selection.state = UserState.AWAITING_ADDRESS
            selection.addressStep = AddressStep.SELECT_CITY
            selection.addressInput = AddressInput()
            bot.editMessageText(
                chatId = ChatId.fromId(chatId),
                messageId = msgId,
                text = "🏙️ Выберите населённый пункт:",
                replyMarkup = getCityKeyboard()
            )
        }

        callbackQuery("addr_start") {
            if (!checkSubAndReturn(bot, callbackQuery)) return@callbackQuery
            val chatId = callbackQuery.message?.chat?.id ?: return@callbackQuery
            val msgId = callbackQuery.message?.messageId ?: return@callbackQuery
            val selection = currentSelections[chatId] ?: return@callbackQuery

            if (selection.addressInput?.isComplete() == true) {
                selection.state = UserState.AWAITING_DATETIME
                bot.editMessageText(
                    chatId = ChatId.fromId(chatId), messageId = msgId,
                    text = "📍 Адрес уже указан: <b>${selection.addressInput!!.formatForUser()}</b>\n\n" +
                            "📅 Теперь напишите <b>Дату и Время доставки</b>:\n" +
                            "⚠️ <b>ФОРМАТ:</b> Число месяц время\n" +
                            "👉 <i>Например: 16 апреля 18:30</i>\n\n",
                    parseMode = ParseMode.HTML,
                    replyMarkup = InlineKeyboardMarkup.create(
                        listOf(
                            listOf(InlineKeyboardButton.CallbackData("✏️ Изменить адрес", "addr_change")),
                            listOf(InlineKeyboardButton.CallbackData("🛒 Вернуться к корзине", "view_cart"))
                        )
                    )
                )
            } else {
                selection.addressStep = AddressStep.SELECT_CITY
                selection.addressInput = AddressInput()
                selection.state = UserState.AWAITING_ADDRESS
                bot.editMessageText(
                    chatId = ChatId.fromId(chatId),
                    messageId = msgId,
                    text = "🏙️ Выберите населённый пункт:",
                    replyMarkup = getCityKeyboard()
                )
            }
        }

        callbackQuery("addr_change") {
            if (!checkSubAndReturn(bot, callbackQuery)) return@callbackQuery
            val chatId = callbackQuery.message?.chat?.id ?: return@callbackQuery
            val msgId = callbackQuery.message?.messageId ?: return@callbackQuery
            val selection = currentSelections[chatId] ?: return@callbackQuery

            selection.addressStep = AddressStep.SELECT_CITY
            selection.addressInput = AddressInput()
            selection.state = UserState.AWAITING_ADDRESS

            bot.editMessageText(
                chatId = ChatId.fromId(chatId),
                messageId = msgId,
                text = "🏙️ Выберите населённый пункт:",
                replyMarkup = getCityKeyboard()
            )
        }

        callbackQuery("addr_back_to_city") {
            if (!checkSubAndReturn(bot, callbackQuery)) return@callbackQuery
            val chatId = callbackQuery.message?.chat?.id ?: return@callbackQuery
            val msgId = callbackQuery.message?.messageId ?: return@callbackQuery
            val selection = currentSelections[chatId] ?: return@callbackQuery
            selection.addressStep = AddressStep.SELECT_CITY
            selection.addressInput = AddressInput()
            bot.editMessageText(
                chatId = ChatId.fromId(chatId),
                messageId = msgId,
                text = "🏙️ Выберите город доставки:",
                replyMarkup = getCityKeyboard()
            )
        }

        callbackQuery("addr_back_to_street") {
            if (!checkSubAndReturn(bot, callbackQuery)) return@callbackQuery
            val chatId = callbackQuery.message?.chat?.id ?: return@callbackQuery
            val msgId = callbackQuery.message?.messageId ?: return@callbackQuery
            val selection = currentSelections[chatId] ?: return@callbackQuery
            selection.addressStep = AddressStep.ENTER_STREET
            bot.editMessageText(
                chatId = ChatId.fromId(chatId), messageId = msgId,
                text = "🛣️ Введите <b>название улицы</b>:",
                parseMode = ParseMode.HTML, replyMarkup = getBackKeyboardForStep(AddressStep.ENTER_STREET)
            )
        }

        callbackQuery("addr_back_to_house") {
            if (!checkSubAndReturn(bot, callbackQuery)) return@callbackQuery
            val chatId = callbackQuery.message?.chat?.id ?: return@callbackQuery
            val msgId = callbackQuery.message?.messageId ?: return@callbackQuery
            val selection = currentSelections[chatId] ?: return@callbackQuery

            selection.addressStep = AddressStep.ENTER_HOUSE

            bot.editMessageText(
                chatId = ChatId.fromId(chatId), messageId = msgId,
                text = "🏠 <b>Введите номер дома</b>:\n<i>Пример: 1, 12А, 7-2</i>",
                parseMode = ParseMode.HTML,
                replyMarkup = InlineKeyboardMarkup.create(
                    listOf(
                        listOf(InlineKeyboardButton.CallbackData("🔙 Назад к улицам", "streets_page_0"))
                    )
                )
            )
        }

        callbackQuery("cancelDelivery") {
            if (!checkSubAndReturn(bot, callbackQuery)) return@callbackQuery
            val chatId = callbackQuery.message?.chat?.id ?: return@callbackQuery
            val msgId = callbackQuery.message?.messageId ?: return@callbackQuery
            val selection = currentSelections[chatId] ?: return@callbackQuery
            selection.isDelivery = false
            selection.deliveryType = ""
            selection.addressInput = null
            selection.datetime = ""
            selection.addressStep = AddressStep.SELECT_CITY
            selection.state = UserState.IDLE

            renderCheckout(bot, chatId, msgId, userCarts[chatId], selection)
        }

        callbackQuery("submitFinal") {
            if (!checkSubAndReturn(bot, callbackQuery)) return@callbackQuery
            val chatId = callbackQuery.message?.chat?.id ?: return@callbackQuery
            val msgId = callbackQuery.message?.messageId ?: return@callbackQuery
            val cart = userCarts[chatId]
            val selection = currentSelections[chatId] ?: return@callbackQuery

            if (cart.isNullOrEmpty()) {
                bot.editMessageText(
                    ChatId.fromId(chatId), msgId,
                    text = "❌ Корзина пуста.",
                    replyMarkup = InlineKeyboardMarkup.create(
                        listOf(listOf(InlineKeyboardButton.CallbackData("🔙 В меню", "backToMenuBtn")))
                    )
                )
                return@callbackQuery
            }

            var initialSum = cart.sumOf { it.price }
            var finalSum = initialSum.toDouble()
            var discountText = ""

            val userId = callbackQuery.from.id
            var discountUsed = false
            if (hasUserDiscount(userId)) {
                finalSum = initialSum * 0.92
                discountText = "\n🎁 <b>Применена скидка 8% за 5 приглашенных друзей!</b>"
                discountUsed = true
            }

            var adminReceipt = ""
            cart.forEachIndexed { i, item ->
                adminReceipt += "${i + 1}. ${item.category} ${item.brand} - ${item.flavor} (${item.price} руб.)\n"
            }

            var addressText: String? = null
            var datetimeText: String? = null

            if (selection.isDelivery) {
                if (selection.deliveryType == "pickup") {
                    adminReceipt += "\n🏃‍♂️ <b>Получение</b>: Самовывоз"
                } else if (selection.deliveryType == "courier") {
                    finalSum += 220.0
                    adminReceipt += "\n🚚 <b>Доставка:</b> 220 руб."
                    adminReceipt += "\n📍 <b>Адрес:</b> ${selection.addressInput!!.formatForAdmin()}"
                    adminReceipt += "\n🕒 <b>Время доставки:</b> ${selection.datetime}"
                }
            }

            if (selection.enteredReferralCode != null) {
                adminReceipt += "\n🤝 <b>Использован код друга:</b> ${selection.enteredReferralCode}"
            }

            val orderId = (1000..9999).random().toString()

            val pendingOrder = PendingOrder(
                userId = userId,
                chatId = chatId,
                username = callbackQuery.message?.chat?.username,
                orderId = orderId,
                items = cart.toList(),
                totalAmount = finalSum.toInt(),
                discountUsed = discountUsed,
                referralCode = selection.enteredReferralCode,
                deliveryType = selection.deliveryType,
                address = addressText,
                datetime = datetimeText
            )

            BotState.pendingOrders[orderId] = pendingOrder

            val adminText = "🚨 <b>НОВЫЙ ЗАКАЗ!</b>\n\n" +
                    "<b>Покупатель:</b> @${callbackQuery.message?.chat?.username ?: "Скрыт (ID: $chatId)"}\n\n" +
                    "<b>Товары:</b>\n$adminReceipt\n" +
                    "💰 <b>Общая сумма к оплате: ${finalSum.toInt()} руб.</b> $discountText\n\n" +
                    "<b>Подтвердите действие:</b>"

            bot.sendMessage(
                ChatId.fromId(BotConfig.ADMIN_ID),
                text = adminText,
                parseMode = ParseMode.HTML,
                replyMarkup = getManagerOrderButtons(orderId)
            )

            bot.editMessageText(
                chatId = ChatId.fromId(chatId), messageId = msgId,
                text = "🎉 <b>Заказ принят! Спасибо за ваше доверие</b> ❤\n" +
                        "Менеджер @ERS_rrs скоро свяжется с вами.\nНомер заказа: #<b>$orderId</b>\n\n" +
                        "💰 <b>К оплате: ${finalSum.toInt()} руб.</b> $discountText\n\n" +
                        "❗ Если в течение 15 минут Вам не отпишет менеджер, значит у вас скрыт ID или закрытый профиль. \n" +
                        "Убедительная просьба, напишите нам сами: @ERS_rrs",
                parseMode = ParseMode.HTML,
                replyMarkup = InlineKeyboardMarkup.create(
                    listOf(
                        listOf(InlineKeyboardButton.CallbackData("🔙 В меню", "backToMenuBtn"))
                    )
                ),
                disableWebPagePreview = true
            )

            userCarts.remove(chatId)
            currentSelections.remove(chatId)
        }

        callbackQuery("cancelFinal") {
            if (!checkSubAndReturn(bot, callbackQuery)) return@callbackQuery
            val chatId = callbackQuery.message?.chat?.id ?: return@callbackQuery
            val msgId = callbackQuery.message?.messageId ?: return@callbackQuery
            userCarts.remove(chatId)
            currentSelections.remove(chatId)
            bot.editMessageText(
                chatId = ChatId.fromId(chatId), messageId = msgId,
                text = "Корзина очищена 🗑️ Ничего страшного, Вы можете начать заново в любой момент!",
                replyMarkup = InlineKeyboardMarkup.create(
                    listOf(
                        listOf(
                            InlineKeyboardButton.CallbackData(
                                "🔙 Вернуться в меню", "backToMenuBtn"
                            )
                        )
                    )
                ), disableWebPagePreview = true
            )
        }

        callbackQuery {
            val data = callbackQuery.data
            if (!data.startsWith("street_")) return@callbackQuery

            val chatId = callbackQuery.message?.chat?.id ?: return@callbackQuery
            val msgId = callbackQuery.message?.messageId ?: return@callbackQuery
            val selection = currentSelections[chatId] ?: return@callbackQuery

            val index = data.removePrefix("street_").toIntOrNull()
            if (index == null || index !in ALL_STREETS.indices) {
                bot.answerCallbackQuery(callbackQuery.id, "❌ Улица не найдена", showAlert = true)
                return@callbackQuery
            }

            val fullStreetName = ALL_STREETS[index]
            selection.addressInput = selection.addressInput?.copy(street = fullStreetName)
            selection.addressStep = AddressStep.ENTER_HOUSE

            bot.editMessageText(
                chatId = ChatId.fromId(chatId), messageId = msgId,
                text = "✅ Улица: <b>$fullStreetName</b>\n\n🏠 <b>Теперь введите номер дома</b>:\n<i>Пример: 1, 13, 17А</i>",
                parseMode = ParseMode.HTML,
                replyMarkup = InlineKeyboardMarkup.create(
                    listOf(listOf(InlineKeyboardButton.CallbackData("🔙 Назад к улицам", "streets_page_0")))
                )
            )
        }

        callbackQuery {
            val data = callbackQuery.data
            if (!data.startsWith("streets_page_")) return@callbackQuery

            val chatId = callbackQuery.message?.chat?.id ?: return@callbackQuery
            val msgId = callbackQuery.message?.messageId ?: return@callbackQuery

            val page = data.removePrefix("streets_page_").toIntOrNull() ?: 0

            bot.editMessageText(
                chatId = ChatId.fromId(chatId), messageId = msgId,
                text = "🛣️ <b>Выберите улицу из списка:</b>",
                parseMode = ParseMode.HTML,
                replyMarkup = getStreetKeyboard(page = page)
            )
        }

        callbackQuery {
            val chatId = callbackQuery.message?.chat?.id ?: return@callbackQuery
            val msgId = callbackQuery.message?.messageId ?: return@callbackQuery
            val data = callbackQuery.data
            val selection = currentSelections[chatId] ?: return@callbackQuery

            if (data.startsWith("addr_city_")) {
                val city = data.removePrefix("addr_city_")
                selection.addressInput = selection.addressInput?.copy(city = city)
                selection.addressStep = AddressStep.ENTER_STREET

                bot.editMessageText(
                    chatId = ChatId.fromId(chatId), messageId = msgId,
                    text = "✅ Населённый пункт: <b>$city</b>\n\n🛣️ <b>Выберите улицу из списка:</b>",
                    parseMode = ParseMode.HTML,
                    replyMarkup = getStreetKeyboard(page = 0)
                )
                return@callbackQuery
            }

            if (data.startsWith("c_")) {
                val cat = data.removePrefix("c_")
                selection.category = cat
                val brands =
                    catalog[cat]?.keys?.map { listOf(InlineKeyboardButton.CallbackData(it, "b_$it")) } ?: emptyList()
                bot.editMessageText(
                    ChatId.fromId(chatId),
                    msgId, text = "Выбрана категория: <b>$cat</b>.\n" + "Теперь выбери бренд:",
                    parseMode = ParseMode.HTML,
                    replyMarkup = InlineKeyboardMarkup.create(
                        brands + listOf(
                            listOf(
                                InlineKeyboardButton.CallbackData("🔙 Назад", "orderBtn")
                            )
                        )
                    )
                )
            } else if (data.startsWith("b_")) {
                val brand = data.removePrefix("b_")
                    .replace("\u00A0", " ")
                    .replace("&amp;", "&")
                    .trim()
                    .replace(Regex("\\s+"), " ")

                selection.brand = brand

                val info = catalog[selection.category]?.get(brand)

                if (info == null) {
                    bot.answerCallbackQuery(callbackQuery.id, "⚠️ Товар временно недоступен", showAlert = true)
                    return@callbackQuery
                }

                try {
                    selection.price = info.price
                    selection.currentFlavors = info.flavors

                    val escapedBrand = escapeHtml(brand)

                    val flavors = info.flavors.mapIndexed { index, flavor ->
                        listOf(InlineKeyboardButton.CallbackData(flavor, "f_$index"))
                    }.toMutableList()

                    flavors.add(listOf(InlineKeyboardButton.CallbackData("🔙 Назад к брендам", "c_${selection.category}")))

                    val hasPhoto = info.photoUrl.isNotBlank()

                    val descriptionBlock = if (info.description.isNotBlank()) {
                        "\n📝 <b>Описание:</b>\n${escapeHtml(info.description)}\n"
                    } else {
                        ""
                    }

                    val textWithPhoto = if (hasPhoto) {
                        "<a href=\"${info.photoUrl}\">&#8203;</a>⭐ <b>Бренд:</b> $escapedBrand\n" +
                                "💰 <b>Цена:</b> ${info.price} руб.\n" +
                                descriptionBlock +
                                "\n👇 Выбери желаемый вкус:"
                    } else {
                        "⭐ <b>Бренд:</b> $escapedBrand\n" +
                                "💰 <b>Цена:</b> ${info.price} руб.\n" +
                                descriptionBlock +
                                "\n👇 Выбери желаемый вкус:"
                    }

                    bot.editMessageText(
                        ChatId.fromId(chatId), msgId, text = textWithPhoto, parseMode = ParseMode.HTML,
                        replyMarkup = InlineKeyboardMarkup.create(flavors), disableWebPagePreview = !hasPhoto
                    )

                } catch (e: Exception) {
                    bot.answerCallbackQuery(callbackQuery.id, "⚠️ Произошла ошибка", showAlert = true)
                }
            } else if (data.startsWith("f_")) {
                val flavorIndex = data.removePrefix("f_").toIntOrNull()

                if (flavorIndex == null) {
                    return@callbackQuery
                }

                val flav = selection.currentFlavors.getOrNull(flavorIndex)

                if (flav == null) {
                    bot.answerCallbackQuery(callbackQuery.id, "⚠️ Вкус не найден", showAlert = true)
                    return@callbackQuery
                }

                val escapedFlav = escapeHtml(flav)
                val escapedBrand = escapeHtml(selection.brand)

                userCarts.getOrPut(chatId) { mutableListOf() }.add(
                    CartItem(
                        selection.category,
                        selection.brand,
                        flav,
                        selection.price
                    )
                )
                val total = userCarts[chatId]?.sumOf { it.price } ?: 0
                val kb = InlineKeyboardMarkup.create(
                    listOf(
                        InlineKeyboardButton.CallbackData("🛒 Смотреть корзину", "view_cart")
                    ),
                    listOf(
                        InlineKeyboardButton.CallbackData("➕ Добавить товар", "orderBtn")
                    ),
                    listOf(
                        InlineKeyboardButton.CallbackData("🔙 Назад в меню", "backToMenuBtn")
                    )
                )

                try {
                    bot.editMessageText(
                        ChatId.fromId(chatId), msgId,
                        text = "✅ <b>$escapedBrand ($escapedFlav)</b> добавлен в корзину!\n\n" +
                                "🛍️ В корзине: ${userCarts[chatId]?.size} товаров\n" +
                                "💰 Сумма: <b>$total руб.</b>\n\nЧто делаем дальше?",
                        parseMode = ParseMode.HTML, replyMarkup = kb, disableWebPagePreview = true
                    )
                } catch (e: Exception) {
                }
            }
        }

        callbackQuery("addr_confirmed") {
            if (!checkSubAndReturn(bot, callbackQuery)) return@callbackQuery
            val chatId = callbackQuery.message?.chat?.id ?: return@callbackQuery
            val selection = currentSelections[chatId] ?: return@callbackQuery

            selection.addressInput?.let { BotState.userLastAddresses[chatId] = it.copy() }
            selection.state = UserState.AWAITING_DATETIME
            activeMenus[chatId]?.let { menuId ->
                bot.editMessageText(
                    chatId = ChatId.fromId(chatId), messageId = menuId,
                    text = "📅 Отлично! Теперь напишите <b>Дату и Время доставки</b>:\n" +
                            "⚠️ <b>ФОРМАТ:</b> Число месяц время\n" +
                            "👉 <i>Например: 16 апреля 18:30</i>\n\n" ,
                    parseMode = ParseMode.HTML,
                    replyMarkup = InlineKeyboardMarkup.create(
                        listOf(listOf(InlineKeyboardButton.CallbackData("🛒 Вернуться к корзине", "view_cart")))
                    )
                )
            }
        }

        callbackQuery("use_saved_address") {
            if (!checkSubAndReturn(bot, callbackQuery)) return@callbackQuery
            val chatId = callbackQuery.message?.chat?.id ?: return@callbackQuery
            val msgId = callbackQuery.message?.messageId ?: return@callbackQuery
            val selection = currentSelections[chatId] ?: return@callbackQuery

            val savedAddr = BotState.userLastAddresses[chatId] ?: return@callbackQuery

            selection.isDelivery = true
            selection.deliveryType = "courier"
            selection.addressInput = savedAddr.copy()
            selection.addressStep = AddressStep.CONFIRM
            selection.state = UserState.AWAITING_DATETIME

            bot.editMessageText(
                chatId = ChatId.fromId(chatId), messageId = msgId,
                text = " Используем прошлый адрес: <b>${savedAddr.formatForUser()}</b>\n\n" +
                        "📅 Напишите <b>Дату и Время доставки</b>:\n" +
                        "⚠️ <b>ФОРМАТ:</b> Число месяц время\n" +
                        "👉 <i>Например: 16 апреля 18:30</i>",
                parseMode = ParseMode.HTML,
                replyMarkup = InlineKeyboardMarkup.create(
                    listOf(listOf(InlineKeyboardButton.CallbackData("🛒 Вернуться к корзине", "view_cart")))
                )
            )
        }

        callbackQuery("addr_skip_flat") {
            if (!checkSubAndReturn(bot, callbackQuery)) return@callbackQuery
            val chatId = callbackQuery.message?.chat?.id ?: return@callbackQuery
            val selection = currentSelections[chatId] ?: return@callbackQuery
            selection.addressInput = selection.addressInput?.copy(flat = null)
            selection.addressStep = AddressStep.CONFIRM
            activeMenus[chatId]?.let { menuId ->
                finalizeAddressInput(bot, chatId, menuId, selection, userCarts[chatId])
            }
        }

        callbackQuery("refSystemBtn") {
            if (!checkSubAndReturn(bot, callbackQuery)) return@callbackQuery

            val chatId = callbackQuery.message?.chat?.id ?: return@callbackQuery
            val msgId = callbackQuery.message?.messageId ?: return@callbackQuery
            val userId = callbackQuery.from.id
            val username = callbackQuery.from.username

            val userCode = getOrCreateReferralCode(userId, username)

            val refText = """
                🎁 <b>Твой уникальный код:</b>
                👇 Нажми, чтобы скопировать 👇
                
                <code>$userCode</code>
                
                Поделись им с друзьями! Если <b>5 человек</b> введут твой код при оформлении заказа, ты получишь <b>скидку 8%</b> на следующую покупку! 
            """.trimIndent()

            bot.editMessageText(
                chatId = ChatId.fromId(chatId),
                messageId = msgId,
                text = refText,
                parseMode = ParseMode.HTML,
                replyMarkup = InlineKeyboardMarkup.create(
                    listOf(
                        listOf(InlineKeyboardButton.CallbackData("🔙 Назад в меню", "backToMenuBtn"))
                    )
                )
            )
        }

        callbackQuery("enterRefCodeBtn") {
            if (!checkSubAndReturn(bot, callbackQuery)) return@callbackQuery
            val chatId = callbackQuery.message?.chat?.id ?: return@callbackQuery
            val msgId = callbackQuery.message?.messageId ?: return@callbackQuery
            val selection = currentSelections[chatId] ?: return@callbackQuery

            selection.state = UserState.AWAITING_REFERRAL_CODE

            bot.editMessageText(
                chatId = ChatId.fromId(chatId), messageId = msgId,
                text = "✍️ <b>Введите реферальный код вашего друга:</b>\n\n",
                parseMode = ParseMode.HTML,
                replyMarkup = InlineKeyboardMarkup.create(
                    listOf(
                        listOf(InlineKeyboardButton.CallbackData("⏭️ Пропустить", "skip_referral")),
                        listOf(InlineKeyboardButton.CallbackData("🔙 Назад к корзине", "view_cart"))
                    )
                )
            )
        }

        callbackQuery("skip_referral") {
            if (!checkSubAndReturn(bot, callbackQuery)) return@callbackQuery
            val chatId = callbackQuery.message?.chat?.id ?: return@callbackQuery
            val msgId = callbackQuery.message?.messageId ?: return@callbackQuery
            val selection = currentSelections[chatId] ?: return@callbackQuery

            selection.state = UserState.IDLE
            renderCheckout(bot, chatId, msgId, userCarts[chatId], selection)
        }

        callbackQuery {
            val data = callbackQuery.data
            if (!data.startsWith("admin_paid_")) return@callbackQuery

            val orderId = data.removePrefix("admin_paid_")
            val pendingOrder = BotState.pendingOrders[orderId] ?: return@callbackQuery

            val chatId = callbackQuery.message?.chat?.id ?: return@callbackQuery
            val msgId = callbackQuery.message?.messageId ?: return@callbackQuery

            if (pendingOrder.discountUsed) {
                consumeUserDiscount(pendingOrder.userId)
            }

            if (pendingOrder.referralCode != null) {
                val referralResult = applyReferralCode(pendingOrder.referralCode!!)
                if (referralResult != null) {
                    val ownerId = referralResult.first
                    val currentInvites = referralResult.second

                    val congratulation = if (currentInvites == 5) {
                        "🎉 <b>УРА!</b> По вашему коду оформили 5-й заказ!\nВам доступна <b>скидка 8% на следующий заказ</b>!"
                    } else if (currentInvites < 5) {
                        "🎉 Поздравляем! По вашему коду только что оформили заказ!\nДо получения скидки 8% осталось пригласить: <b>${5 - currentInvites} чел.</b> ($currentInvites/5)"
                    } else {
                        "🎉 По вашему промокоду оформили еще один заказ! Ваша скидка 8% активна."
                    }

                    bot.sendMessage(ChatId.fromId(ownerId), congratulation, parseMode = ParseMode.HTML)
                }
            }

            val itemsText = pendingOrder.items.joinToString(", ") { "${it.brand} (${it.flavor})" }
            saveOrderToHistory(
                orderId = orderId,
                userId = pendingOrder.userId,
                username = pendingOrder.username,
                items = itemsText,
                totalAmount = pendingOrder.totalAmount,
                deliveryType = pendingOrder.deliveryType,
                referralCode = pendingOrder.referralCode
            )

            updateInventoryForOrder(pendingOrder.items)

            BotState.pendingOrders.remove(orderId)

            bot.editMessageText(
                chatId = ChatId.fromId(chatId),
                messageId = msgId,
                text = "✅ <b>Заказ #$orderId подтверждён как успешный!</b>\n\nРеферальные бонусы начислены.",
                parseMode = ParseMode.HTML
            )

            bot.sendMessage(
                ChatId.fromId(pendingOrder.chatId),
                text = "✅ <b>Ваш заказ #$orderId подтверждён как успешный!</b>\n\nЕсли у вас остались вопросы, пожалуйста, свяжитесь с менеджером: @ERS_rrs",
                parseMode = ParseMode.HTML
            )
        }

        callbackQuery {
            val data = callbackQuery.data
            if (!data.startsWith("admin_refused_")) return@callbackQuery

            val orderId = data.removePrefix("admin_refused_")
            val pendingOrder = BotState.pendingOrders[orderId] ?: return@callbackQuery

            val chatId = callbackQuery.message?.chat?.id ?: return@callbackQuery
            val msgId = callbackQuery.message?.messageId ?: return@callbackQuery

            BotState.pendingOrders.remove(orderId)

            bot.editMessageText(
                chatId = ChatId.fromId(chatId),
                messageId = msgId,
                text = "❌ <b>Заказ #$orderId отменён.</b>\n\n Бонусы не были начислены.",
                parseMode = ParseMode.HTML
            )

            bot.sendMessage(
                ChatId.fromId(pendingOrder.chatId),
                text = "❌ <b>К сожалению, ваш заказ #$orderId был отменён.</b>\n\nЕсли у вас есть вопросы, свяжитесь с менеджером: @ERS_rrs",
                parseMode = ParseMode.HTML
            )
        }
    }
}

private fun checkSubAndReturn(bot: Bot, callbackQuery: com.github.kotlintelegrambot.entities.CallbackQuery): Boolean {
    val chatId = callbackQuery.message?.chat?.id ?: return false
    val userId = callbackQuery.from.id
    return SubscriptionGuard.requireSubscriptionForCallback(bot, chatId, userId)
}

package ersbot.config

import ersbot.models.*
import services.fetchCatalogFromSheets

object BotState {
    val userCarts = mutableMapOf<Long, MutableList<CartItem>>()
    val currentSelections = mutableMapOf<Long, CurrentSelection>()
    val activeMenus = mutableMapOf<Long, Long>()
    val pendingOrders = mutableMapOf<String, PendingOrder>()
    val userLastAddresses = mutableMapOf<Long, AddressInput>()
    val mixedPaymentState = mutableMapOf<Long, Pair<String, Long>>()

    var catalog: Map<String, Map<String, ProductDetails>> = emptyMap()
        private set

    fun reloadCatalog() {
        catalog = fetchCatalogFromSheets()
        println("✅ Каталог обновлён: ${catalog.size} категорий")
    }
}
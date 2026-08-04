package ersbot.models
import java.time.LocalDateTime
import java.time.Duration

enum class UserState { IDLE, AWAITING_ADDRESS, AWAITING_DATETIME, AWAITING_REFERRAL_CODE }
enum class AddressStep { SELECT_CITY, ENTER_STREET, ENTER_HOUSE, ENTER_FLAT, CONFIRM }

data class CartItem(
    val category: String,
    val brand: String,
    val flavor: String,
    val price: Int
)

data class AddressInput(
    val city: String? = null,
    val street: String? = null,
    val house: String? = null,
    val flat: String? = null,
    val comment: String? = null
) {
    fun isComplete(): Boolean = city != null && street != null && house != null

    fun formatForUser(): String = buildString {
        append("📍 Адрес доставки:\n")
        city?.let { append("🏙️ $it\n") }
        street?.let { append("🛣️ $it") }
        house?.let { append(", д. $it") }
        flat?.let { append(", кв. $it") }
        comment?.let { append("\n📝 $it") }
    }.trimEnd()

    fun formatForAdmin(): String = buildString {
        append("🏙️ ${city ?: "Не указан"}\n")
        append("🛣️ ${street ?: "Не указана"}, д. ${house ?: "Не указан"}")
        flat?.let { append(", кв. $it") }
        comment?.let { append("\n📝 $it") }
    }
}

data class CurrentSelection(
    var category: String = "",
    var brand: String = "",
    var price: Int = 0,
    var isDelivery: Boolean = false,
    var deliveryType: String = "",
    var addressInput: AddressInput? = null,
    var datetime: String = "",
    var state: UserState = UserState.IDLE,
    var addressStep: AddressStep = AddressStep.SELECT_CITY,
    var itemsConfirmed: Boolean = false,
    var enteredReferralCode: String? = null,
    var currentFlavors: List<String> = emptyList()
)

data class ProductDetails(
    val price: Int,
    val photoUrl: String,
    val flavors: List<String>,
    val description: String = ""
)

data class ValidationResult(
    val isValid: Boolean,
    val errorMessage: String? = null
)

data class ReferralInfo(
    val userId: Long,
    val referralCode: String,
    val invitesCount: Int,
    val hasDiscount: Boolean
)

data class PendingOrder(
    val userId: Long,
    val chatId: Long,
    val username: String?,
    val orderId: String,
    val items: List<CartItem>,
    val totalAmount: Int,
    val discountUsed: Boolean,
    val referralCode: String?,
    val deliveryType: String?,
    val address: String?,
    val datetime: String?,

    val createdAt: LocalDateTime = LocalDateTime.now(),
    val answeredAt: LocalDateTime? = null,
    val assembledAt: LocalDateTime? = null,
    val handedOverAt: LocalDateTime? = null,
    val completedAt: LocalDateTime? = null,

    val answerRating: Int? = null,
    val assemblyRating: Int? = null,

    val paymentType: String? = null,
    val cashAmount: Int? = null,
    val transferAmount: Int? = null
) {
    fun getAnswerTime(): Long? {
        return if (answeredAt != null) Duration.between(createdAt, answeredAt).toMinutes() else null
    }

    fun getAssemblyTime(): Long? {
        return if (assembledAt != null && answeredAt != null)
            Duration.between(answeredAt, assembledAt).toMinutes()
        else null
    }
}

object OrderEvaluator {

    fun evaluateAnswerTime(minutes: Long): Int {
        return when {
            minutes <= 4 -> 1
            minutes <= 6 -> 0
            else -> -1
        }
    }

    fun evaluateAssemblyTime(minutes: Long): Int {
        return when {
            minutes <= 2 -> 1
            minutes <= 4 -> 0
            else -> -1
        }
    }

    fun getRatingText(rating: Int): String {
        return when (rating) {
            1 -> "🟢 +"
            0 -> "🟡 Нейтрально"
            -1 -> "🔴 -"
            else -> "❓"
        }
    }
}


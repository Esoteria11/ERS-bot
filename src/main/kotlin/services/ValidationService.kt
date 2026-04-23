package ersbot.services
import ersbot.models.*

fun validateAddressPart(input: String, step: AddressStep): ValidationResult {
    val trimmed = input.trim()
    if (trimmed.isEmpty()) return ValidationResult(false, "Поле не может быть пустым")
    if (Regex("(.)\\1{6,}").containsMatchIn(trimmed)) return ValidationResult(false, "Не повторяйте символы")
    if (Regex("[^а-яА-Яa-zA-Z0-9\\s\\-.,№]{3,}").containsMatchIn(trimmed)) return ValidationResult(
        false,
        "Недопустимые символы"
    )

    val blacklist = listOf("жопа", "дерьм", "говн", "хуй", "пидр", "ебан", "мудак", "гондон", "чмо", "хуесос", "очко", "ебл")
    if (blacklist.any { trimmed.lowercase().contains(it) }) return ValidationResult(false, "Некорректное содержание")

    return when (step) {
        AddressStep.SELECT_CITY -> ValidationResult(true)
        AddressStep.ENTER_STREET -> {
            when {
                trimmed.any { it.isDigit() } -> ValidationResult(false, "Название улицы не должно содержать цифр")
                trimmed.length < 2 -> ValidationResult(false, "Название улицы слишком короткое")
                else -> ValidationResult(true)
            }
        }
        AddressStep.ENTER_HOUSE -> {
            val houseRegex = Regex("^(\\d+)([а-яА-Я]?)(-(\\d+)([а-яА-Я]?))?$")
            val match = houseRegex.matchEntire(trimmed)
            if (match == null) {
                ValidationResult(false, "Пример: 1, 13, 17А")
            } else {
                val mainNum = match.groupValues[1].toIntOrNull() ?: 0
                if (mainNum < 1 || mainNum > 999) return ValidationResult(false, "Номер дома должен быть от 1 до 999")
                ValidationResult(true)
            }
        }
        AddressStep.ENTER_FLAT -> {
            if (trimmed == "-") ValidationResult(true)
            else {
                val flatRegex = Regex("^(\\d+)([а-яА-Я]?)$")
                val match = flatRegex.matchEntire(trimmed)
                if (match == null) {
                    ValidationResult(false, "Пример: 5, 12Б")
                } else {
                    val num = match.groupValues[1].toIntOrNull() ?: 0
                    if (num < 1 || num > 999) return ValidationResult(false, "Номер квартиры должен быть от 1 до 999")
                    ValidationResult(true)
                }
            }
        }
        AddressStep.CONFIRM -> ValidationResult(true)
    }
}

fun normalizeStreetName(street: String): String {
    return street.trim().replace(Regex("\\s+"), " ")
}
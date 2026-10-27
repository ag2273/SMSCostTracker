package com.example.smscosttracker.parsing

import com.example.smscosttracker.ml.MessageType

/** What a labelled part of an SMS is about. */
enum class FieldRole {
    AMOUNT, MERCHANT, FROM, TO, IN, CARD, ACCOUNT, BALANCE, FEE, DATE, REFERENCE, CODE, PURPOSE, IGNORE, NONE
}

/** What a value looks like, independent of its label. */
enum class ValueShape { AMOUNT, MASKED_DIGITS, DATE, TEXT, OTHER }

data class SmsField(val role: FieldRole, val label: String, val value: String, val shape: ValueShape, val range: IntRange)

/**
 * The words banks use to label parts of a transaction SMS, in Arabic and English. Nothing here
 * names a bank: supporting new wording means adding a word to the right role.
 */
object SmsLexicon {
    val labels: Map<FieldRole, List<String>> = mapOf(
        FieldRole.AMOUNT to listOf(
            "amount", "paid amount", "transaction amount", "total amount", "amt", "used for",
            "مبلغ", "المبلغ", "بمبلغ", "بقيمة", "قيمة"
        ),
        FieldRole.MERCHANT to listOf(
            "at", "merchant", "used at", "payee", "biller", "@",
            "لدى", "عند", "مفوتر", "الجهة"
        ),
        FieldRole.FROM to listOf("from", "من"),
        FieldRole.TO to listOf("to", "beneficiary", "إلى", "الى", "المستفيد", "لصالح"),
        FieldRole.IN to listOf("on", "في"),
        FieldRole.CARD to listOf(
            "card", "card number", "card no", "ending with", "ending", "via", "by",
            "بطاقة", "بطاقتك", "البطاقة", "لبطاقة", "المنتهية برقم", "المنتهية ب", "المنتهية"
        ),
        FieldRole.ACCOUNT to listOf(
            "account", "account number", "account no", "a/c", "acct", "iban", "finance account no",
            "حساب", "حسابك", "الحساب", "لحسابك", "بحسابك", "الآيبان", "آيبان", "الايبان"
        ),
        FieldRole.BALANCE to listOf(
            "balance", "available balance", "current balance", "available bal", "avl bal",
            "available limit", "credit limit", "limit", "total outstanding amount", "outstanding",
            "الرصيد", "رصيد", "الرصيد المتوفر", "الرصيد الحالي", "الحد المتوفر", "الحد"
        ),
        FieldRole.FEE to listOf("fees", "fee", "commission", "الرسوم", "رسوم", "عمولة"),
        FieldRole.DATE to listOf("date", "time", "تاريخ", "التاريخ", "بتاريخ", "الوقت", "الوقت والتاريخ"),
        FieldRole.REFERENCE to listOf(
            "reference", "ref", "with reference", "with ref", "reference number", "transaction id", "txn id",
            "رقم المرجع", "المرجع", "رقم الفاتورة"
        ),
        FieldRole.CODE to listOf("code", "otp", "password", "رمز", "الرمز"),
        FieldRole.PURPOSE to listOf(
            "for", "service", "transaction type", "wallet name", "finance type",
            "لـ", "الغرض", "لخدمة", "الخدمة"
        ),
        FieldRole.IGNORE to listOf(
            "sender", "country", "currency", "exchange rate",
            "مكان السحب", "الدولة", "العملة", "سعر الصرف"
        )
    )

    /** Words that are never a merchant on their own. */
    val genericText = setOf(
        "your account", "account", "card", "contact", "wallet", "your card",
        "حسابك", "حسابكم", "حساب", "بطاقة", "بطاقتك", "خلال"
    )

    /** Evidence for each message type; the heaviest type present wins. */
    val typeCues: List<Pair<MessageType, Pair<Int, Regex>>> = listOf(
        MessageType.NOTICE to (10 to Regex(
            "(?i)\\b(otp|one[- ]time password|verification code|security code|activation code|" +
                "authentication code|logged in|use pin|pin\\s*[0-9]{4,8}\\s*to|(?:your )?code is)\\b|كلمة مرور صالحة لمرة واحدة|رمز التحقق|رمز التفعيل|" +
                "الرمز\\s*[0-9]+\\s*ل|تم تفعيل|تسجيل الدخول"
        )),
        MessageType.NOTICE to (6 to Regex(
            "(?i)\\b(offer|promo(?:tion)?|discount|enjoy|cashback offer|daily limit|limit increase)\\b|" +
                "استفد|استمتع|عرض|خصومات|خصم حصري|للمزيد|الحد اليومي|تم قبول طلبك"
        )),
        MessageType.DECLINED to (8 to Regex(
            "(?i)\\b(declined|failed|not processed|rejected|unsuccessful)\\b|مرفوضة|لم تتم|فشل"
        )),
        MessageType.INTERNAL to (7 to Regex(
            "(?i)\\b(between your accounts|between own accounts|internal transfer|own account transfer|" +
                "credit card\\s*:?\\s*payment|card\\s*:\\s*payment|card repayment|" +
                "payment to (?:your )?(?:credit )?card|wallet top ?up|top[- ]?up|" +
                "adding money to (?:your )?(?:account|wallet)|your payment of [^\\n]{0,30}? was successful)\\b|" +
                "بين حساباتك|بين حساباتي|بين الحسابات|تم سداد بطاقتك|تسديد بطاقة|لحساب بطاقتك|لمحفظتك|إلى محفظتك|" +
                "(?:إئتمانية|ائتمانية|الائتمانية)\\s*:\\s*(?:تسديد|تأكيد السداد)"
        )),
        MessageType.STATEMENT to (6 to Regex(
            "(?i)\\b(amount due|minimum due|total due|payment due|due date|statement(?: has been)? issued|" +
                "statement date|closing balance|next billing date|monthly installment)\\b|" +
                "كشف حساب|المبلغ المستحق|تاريخ الاستحقاق"
        )),
        MessageType.CREDIT to (5 to Regex(
            "(?i)\\b(refund|refunded|credited|credit transfer|deposited|deposit received|deposit of|salary|" +
                "cashback|reversal|reversed|returned|received from|incoming|inward|you have received|" +
                "received amount)\\b|واردة|وارده|إلى حسابك|الى حسابك|ايداع|إيداع|استرداد|إسترداد|استرجاع|راتب|معادة|مرتجعة|عكسية"
        )),
        MessageType.WITHDRAWAL to (3 to Regex("(?i)\\b(atm|cash withdrawal|withdraw(?:al|n)?)\\b|سحب|صراف")),
        MessageType.BILL_OR_TRANSFER to (3 to Regex(
            "(?i)\\b(transfer(?:red)?|bill payment|bill|sadad|sarie|standing order|direct debit|cheque|" +
                "loan|installment|finance)\\b|حوالة|حواله|تحويل|سداد فاتورة|فاتورة|مدفوعات|قرض|تمويل"
        )),
        MessageType.PURCHASE to (3 to Regex(
            "(?i)\\b(purchase|pos|paid|payment|debited|deducted|spent|charged|used|transaction of)\\b|" +
                "شراء|نقاط البيع|خصم|استخدمت|دفع"
        ))
    )

    /** Advice and requests; only used to tell a notice from a transaction whose amount is missing. */
    val advisory = Regex(
        "(?i)\\b(you can|please (?:visit|update|collect|pay)|dear valued|limited time|we are|will be)\\b|" +
            "يرجى|يرجي|رجاء|نذكركم|يمكنك|لفترة محدودة|تطبق الشروط|بصدد|حرصا|حرصاً"
    )

    /** Precedence when two types carry the same weight. */
    val typePrecedence = listOf(
        MessageType.NOTICE, MessageType.DECLINED, MessageType.INTERNAL, MessageType.STATEMENT,
        MessageType.CREDIT, MessageType.WITHDRAWAL, MessageType.BILL_OR_TRANSFER, MessageType.PURCHASE
    )
}

/** Splits an SMS into labelled values: "label: value" lines and inline "marker value" phrases. */
object SmsFieldReader {
    private const val CURRENCY =
        "EGP|SAR|AED|USD|EUR|GBP|TRY|INR|KWD|BHD|QAR|OMR|JOD|CNY|JPY|CAD|AUD|CHF|ريال|جنيه|درهم|ج\\.م|جم"
    private const val NUMBER = "(?:[0-9][0-9,]*(?:\\.[0-9]{1,3})?|\\.[0-9]{1,3})"
    private val amountShape = Regex(
        "(?i)(?<![A-Za-z])(?:($CURRENCY)[\\s\\u00A0:=]*($NUMBER)|(?<![0-9*xX#•\\-/.])($NUMBER)[\\s\\u00A0:=]*($CURRENCY))" +
            "(?![A-Za-z])"
    )
    private val bareNumber = Regex("^[0-9][0-9,]*(?:\\.[0-9]{1,2})?$")
    private val maskedShape = Regex(
        "[*xX#•]+\\s*[0-9]{2,6}|[0-9]{2,6}\\s*[*xX#•]+|[0-9]{2,4}-[0-9*xX]+-[0-9]{2,6}|\\(\\s*[0-9]{3,6}\\s*\\)|" +
            "^[^0-9]{0,12}[0-9]{3,6}(?:\\s*[;:,(]|\\s*$)"
    )
    private val dateShape = Regex(
        "^\\s*(?:[0-9]{1,4}[-/.][0-9]{1,2}(?:[-/.][0-9]{1,4})?|[0-9]{1,2}:[0-9]{2}|" +
            "[0-9]{2}(?:JAN|FEB|MAR|APR|MAY|JUN|JUL|AUG|SEP|OCT|NOV|DEC)[0-9]{2})",
        RegexOption.IGNORE_CASE
    )
    private val valueEnd = Regex("\\n|,(?=\\s|\\p{L})|\\.\\s+(?=[\\p{Lu}\\u0600-\\u06FF])")

    private val markers: Regex
    private val roleByLabel: Map<String, FieldRole>

    init {
        val entries = SmsLexicon.labels.flatMap { (role, words) -> words.map { it to role } }
        roleByLabel = entries.toMap()
        // Word labels must not touch other letters; a symbol label like "@" may.
        val alternatives = entries.map { it.first }.distinct().sortedByDescending { it.length }
            .joinToString("|") { label ->
                val escaped = Regex.escape(label).replace(" ", "\\E\\s+\\Q")
                if (label.last().isLetter()) "$escaped(?![\\p{L}])" else escaped
            }
        markers = Regex("(?i)(?<![\\p{L}\\p{N}])(?:$alternatives)")
    }

    fun read(cleanBody: String): List<SmsField> {
        val found = markers.findAll(cleanBody).toList()
        val fields = mutableListOf<SmsField>()
        found.forEachIndexed { index, marker ->
            val label = marker.value.lowercase().replace(Regex("\\s+"), " ")
            val role = roleByLabel[label] ?: return@forEachIndexed
            var start = marker.range.last + 1
            while (start < cleanBody.length && cleanBody[start] in " \t:=：-–") start++
            val nextMarker = found.getOrNull(index + 1)?.range?.first ?: cleanBody.length
            val stop = valueEnd.find(cleanBody, start)?.range?.first?.coerceAtMost(nextMarker) ?: nextMarker
            if (stop <= start) return@forEachIndexed
            val value = cleanBody.substring(start, stop).trim().trimEnd('.', ',', '-', '×')
            if (value.isNotEmpty()) fields += SmsField(role, label, value, shapeOf(value), start until stop)
        }
        return fields
    }

    private val leadingMasked = Regex("^(?:[*xX#•]+\\s*[0-9]{2,6}|[0-9]{2,4}-[0-9*xX]+-[0-9]{2,6})(?![0-9])")

    fun shapeOf(value: String): ValueShape = when {
        leadingMasked.containsMatchIn(value) -> ValueShape.MASKED_DIGITS
        amountShape.containsMatchIn(value) -> ValueShape.AMOUNT
        dateShape.containsMatchIn(value) -> ValueShape.DATE
        maskedShape.containsMatchIn(value) -> ValueShape.MASKED_DIGITS
        bareNumber.matches(value) -> ValueShape.OTHER
        value.count { it.isLetter() } >= 2 -> ValueShape.TEXT
        else -> ValueShape.OTHER
    }

    /** Every currency-and-number pair in [text], with its position. */
    fun amounts(text: String): List<Triple<String, String, IntRange>> =
        amountShape.findAll(text).map { m ->
            val currency = m.groupValues[1].ifEmpty { m.groupValues[4] }
            val number = m.groupValues[2].ifEmpty { m.groupValues[3] }
            Triple(number, currency, m.range)
        }.toList()

    /** Instrument digits written before any amount or date in [value]. */
    fun instrumentDigits(value: String): String? {
        val cut = listOfNotNull(amountShape.find(value)?.range?.first, dateShape.find(value)?.range?.first).minOrNull()
        return digitsOf(if (cut != null && cut > 0) value.substring(0, cut) else value)
    }

    fun digitsOf(value: String): String? {
        leadingMasked.find(value)?.let { masked ->
            return Regex("([0-9]{2,6})$").find(masked.value)?.groupValues?.get(1)?.takeIf { it.length >= 3 }
        }
        return Regex("([0-9]{3,6})(?![0-9])").findAll(value).lastOrNull()?.groupValues?.get(1)
            ?.takeUnless { maskedYear(value, it) }
    }

    private fun maskedYear(value: String, digits: String) =
        digits.length == 4 && digits.toIntOrNull() in 1990..2100 && !value.contains(Regex("[*xX#•(]"))
}

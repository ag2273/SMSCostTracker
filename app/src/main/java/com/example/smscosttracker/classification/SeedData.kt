package com.example.smscosttracker.classification

object SeedData {
    val examples: Map<Category, List<String>> = mapOf(
        Category.GROCERIES to listOf(
            "carrefour", "panda", "spinneys", "seoudi", "metro market", "hypermarket",
            "supermarket", "grocery", "bazaar", "كارفور", "بنده", "بقالة", "سوبر ماركت"
        ),
        Category.DINING to listOf(
            "restaurant", "cafe", "mcdonalds", "kfc", "pizza hut", "talabat",
            "hungerstation", "elmenus", "coffee", "مطعم", "كافيه", "مقهى"
        ),
        Category.TRANSPORT to listOf(
            "uber", "careem", "taxi", "metro", "bus", "train", "أوبر", "كريم", "تاكسي", "مترو"
        ),
        Category.FUEL to listOf(
            "aramco", "petrol", "fuel", "gas station", "adnoc", "بنزين", "بترول", "محطة وقود"
        ),
        Category.UTILITIES to listOf(
            "electricity", "water bill", "gas bill", "sewer", "كهرباء", "مياه", "غاز"
        ),
        Category.TELECOM to listOf(
            "stc", "mobily", "zain", "vodafone", "etisalat", "orange", "internet",
            "phone bill", "اتصالات", "فودافون", "موبايلي", "زين"
        ),
        Category.RENT to listOf("rent", "lease", "إيجار"),
        Category.HEALTH to listOf(
            "pharmacy", "hospital", "clinic", "doctor", "lab", "صيدلية", "مستشفى", "عيادة", "طبيب"
        ),
        Category.EDUCATION to listOf(
            "school", "university", "tuition", "course", "مدرسة", "جامعة", "تعليم"
        ),
        Category.SHOPPING to listOf(
            "amazon", "noon", "souq", "mall", "zara", "h&m", "clothes", "shopping",
            "تسوق", "مول", "ملابس"
        ),
        Category.ENTERTAINMENT to listOf(
            "cinema", "netflix", "spotify", "game", "entertainment", "سينما", "ترفيه"
        ),
        Category.TRAVEL to listOf(
            "airline", "hotel", "booking", "travel", "flight", "طيران", "فندق", "سفر"
        ),
        Category.FEES to listOf(
            "fee", "commission", "charge", "atm fee", "bank fee", "رسوم", "عمولة"
        ),
        Category.TRANSFERS to listOf("transfer", "sent to", "تحويل"),
        Category.OTHER to listOf("misc", "other", "أخرى")
    )
}

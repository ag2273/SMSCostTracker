package com.example.smscosttracker.classification

import java.util.Locale

/** Reduces the many ways a bank writes one shop ("AMAZON.SA", "Amazon SA Riyadh") to one key. */
object MerchantNames {
    private val arabicMarks = Regex("[\\u064B-\\u0652\\u0640]")
    private val domainSuffix = Regex("\\.(com|net|org)(\\.[a-z]{2})?\\b|\\.(sa|eg|ae)\\b")
    private val separators = Regex("[^\\p{L}\\p{N}]+")
    private val prefixes = setOf("pos", "ecom", "purchase", "sq", "www", "pay")
    private val trailingNoise = setOf(
        "riyadh", "jeddah", "jedda", "dammam", "khobar", "alkhobar", "makkah", "mecca", "madinah",
        "medina", "taif", "abha", "tabuk", "cairo", "giza", "alexandria", "dubai", "sa", "ksa", "sau",
        "saudi", "arabia", "eg", "egy", "egypt", "ae", "uae", "branch", "br", "mktp", "marketplace",
        "الرياض", "جده", "الدمام", "الخبر", "مكه", "المدينه", "القاهره", "الجيزه", "السعوديه", "مصر", "فرع"
    )
    private val brandAliases = mapOf("amzn" to "amazon")

    fun key(merchant: String): String {
        val lowered = arabicMarks.replace(merchant.trim().lowercase(Locale.ROOT), "")
            .replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا')
            .replace('ة', 'ه').replace('ى', 'ي')
        val tokens = separators.split(domainSuffix.replace(lowered, " "))
            .filter { it.isNotEmpty() }
            .map { brandAliases[it] ?: it }
            .toMutableList()
        while (tokens.size > 1 && tokens.first() in prefixes) tokens.removeAt(0)
        while (tokens.size > 1 && (tokens.last() in trailingNoise || tokens.last().all(Char::isDigit))) {
            tokens.removeAt(tokens.lastIndex)
        }
        return tokens.joinToString(" ")
    }
}

/**
 * Groups merchant names whose keys are equal, nearly equal (one typo in a long name) or merged by
 * the user, and picks one display name per group: the user's merge target, else the most used name.
 */
class MerchantGroups(names: List<String>, aliases: Map<String, String> = emptyMap()) {
    private val parent = HashMap<String, String>()
    private val displayByRoot = HashMap<String, String>()

    init {
        val keys = (names + aliases.values).map { MerchantNames.key(it) }.filter { it.isNotEmpty() }
            .distinct()
            .sorted()
        keys.forEach { parent[it] = it }
        for (i in keys.indices) {
            for (j in i + 1 until keys.size) {
                if (similar(keys[i], keys[j])) union(keys[i], keys[j])
            }
        }
        for ((aliasKey, target) in aliases) {
            val targetKey = MerchantNames.key(target)
            if (targetKey.isEmpty()) continue
            parent.putIfAbsent(aliasKey, aliasKey)
            union(aliasKey, targetKey)
        }
        val counts = names.filter { MerchantNames.key(it).isNotEmpty() }.groupingBy { it }.eachCount()
        counts.keys.groupBy { find(MerchantNames.key(it)) }.forEach { (root, members) ->
            displayByRoot[root] = members.sortedWith(
                compareByDescending<String> { counts[it] ?: 0 }.thenBy { it.length }.thenBy { it }
            ).first()
        }
        for (target in aliases.values) {
            val targetKey = MerchantNames.key(target)
            if (targetKey.isNotEmpty()) displayByRoot[find(targetKey)] = target
        }
    }

    private val keyCache = HashMap<String, String>()

    /** The display name for [merchant]'s group, or [merchant] itself when it has no usable key. */
    fun displayName(merchant: String): String {
        val key = keyCache.getOrPut(merchant) { MerchantNames.key(merchant) }
        if (key.isEmpty() || key == "unknown" || key !in parent) return merchant
        return displayByRoot[find(key)] ?: merchant
    }

    /** The display name for a stored key, e.g. a category correction's merchant key. */
    fun displayNameForKey(key: String): String? = parent[key]?.let { displayByRoot[find(key)] }

    private fun find(key: String): String {
        var root = key
        while (parent[root] != root) root = parent[root] ?: return root
        var node = key
        while (parent[node] != root) {
            val next = parent[node]!!
            parent[node] = root
            node = next
        }
        return root
    }

    private fun union(a: String, b: String) {
        val rootA = find(a)
        val rootB = find(b)
        if (rootA != rootB) parent[rootB] = rootA
    }

    private fun similar(a: String, b: String): Boolean {
        if (a == "unknown" || b == "unknown") return false
        if (a.length < 6 || b.length < 6 || kotlin.math.abs(a.length - b.length) > 1) return false
        return withinOneEdit(a, b)
    }

    private fun withinOneEdit(a: String, b: String): Boolean {
        if (a == b) return true
        val (short, long) = if (a.length <= b.length) a to b else b to a
        var i = 0
        var j = 0
        var edits = 0
        while (i < short.length && j < long.length) {
            if (short[i] == long[j]) {
                i++
                j++
                continue
            }
            if (++edits > 1) return false
            if (short.length == long.length) i++
            j++
        }
        return edits + (long.length - j) + (short.length - i) <= 1
    }
}

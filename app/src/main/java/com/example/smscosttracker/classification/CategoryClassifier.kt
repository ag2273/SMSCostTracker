package com.example.smscosttracker.classification

import java.util.Locale
import kotlin.math.ln

class CategoryClassifier(
    seed: Map<Category, List<String>> = SeedData.examples,
    corrections: Map<String, String> = emptyMap()
) {
    private val tokenCounts = mutableMapOf<Category, MutableMap<String, Int>>()
    private val documentCounts = mutableMapOf<Category, Int>()
    private val vocabulary = mutableSetOf<String>()
    private val overrides = mutableMapOf<String, Category>()

    init {
        for ((category, examples) in seed) {
            for (example in examples) train(example, category)
        }
        for ((merchant, categoryName) in corrections) {
            val category = Category.fromStorage(categoryName)
            overrides[normalizeMerchant(merchant)] = category
            train(merchant, category)
        }
    }

    fun classify(merchant: String): Category {
        val normalized = normalizeMerchant(merchant)
        if (normalized.isEmpty()) return Category.OTHER
        overrides[normalized]?.let { return it }

        val tokens = tokenize(normalized)
        if (tokens.isEmpty()) return Category.OTHER
        if (tokens.none { it in vocabulary }) return Category.OTHER

        val totalDocuments = documentCounts.values.sum().coerceAtLeast(1)
        val categoryCount = Category.entries.size
        var best = Category.OTHER
        var bestScore = Double.NEGATIVE_INFINITY

        for (category in Category.entries) {
            val vocabularySize = vocabulary.size.coerceAtLeast(1)
            val categoryTokens = tokenCounts[category]?.values?.sum() ?: 0
            var score = ln(
                ((documentCounts[category] ?: 0) + 1.0) / (totalDocuments + categoryCount)
            )
            for (token in tokens) {
                val count = tokenCounts[category]?.get(token) ?: 0
                score += ln((count + 1.0) / (categoryTokens + vocabularySize))
            }
            if (score > bestScore) {
                bestScore = score
                best = category
            }
        }
        return best
    }

    private fun train(text: String, category: Category) {
        val tokens = tokenize(text)
        if (tokens.isEmpty()) return
        val counts = tokenCounts.getOrPut(category) { mutableMapOf() }
        for (token in tokens) {
            counts[token] = (counts[token] ?: 0) + 1
            vocabulary += token
        }
        documentCounts[category] = (documentCounts[category] ?: 0) + 1
    }

    private fun tokenize(text: String): List<String> =
        TOKEN.findAll(text.lowercase(Locale.ROOT)).map { it.value }.toList()

    companion object {
        private val TOKEN = Regex("[\\p{L}\\p{N}]+")

        fun normalizeMerchant(text: String): String = text.trim().lowercase(Locale.ROOT)
    }
}

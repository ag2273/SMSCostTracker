package com.example.smscosttracker.classification

import java.util.Locale
import kotlin.math.exp
import kotlin.math.ln

data class Classification(val category: Category, val confidence: Double)

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

    fun classify(merchant: String): Category = classifyWithConfidence(merchant).category

    /** Category plus a 0..1 confidence; corrections are certain, merchants with no known words score 0. */
    fun classifyWithConfidence(merchant: String): Classification {
        val normalized = normalizeMerchant(merchant)
        if (normalized.isEmpty()) return Classification(Category.OTHER, 0.0)
        overrides[normalized]?.let { return Classification(it, 1.0) }

        val tokens = tokenize(normalized)
        if (tokens.isEmpty()) return Classification(Category.OTHER, 0.0)
        if (tokens.none { it in vocabulary }) return Classification(Category.OTHER, 0.0)

        val totalDocuments = documentCounts.values.sum().coerceAtLeast(1)
        val categoryCount = Category.entries.size
        val vocabularySize = vocabulary.size.coerceAtLeast(1)
        val scores = Category.entries.associateWith { category ->
            val categoryTokens = tokenCounts[category]?.values?.sum() ?: 0
            var score = ln(
                ((documentCounts[category] ?: 0) + 1.0) / (totalDocuments + categoryCount)
            )
            for (token in tokens) {
                val count = tokenCounts[category]?.get(token) ?: 0
                score += ln((count + 1.0) / (categoryTokens + vocabularySize))
            }
            score
        }
        val ranked = scores.entries.sortedByDescending { it.value }
        val (best, runnerUp) = ranked[0] to ranked[1]
        // How clearly the best category beats the runner-up: about 0.5 when torn between two.
        return Classification(best.key, 1.0 / (1.0 + exp(runnerUp.value - best.value)))
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

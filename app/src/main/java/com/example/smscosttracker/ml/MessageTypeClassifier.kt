package com.example.smscosttracker.ml

import kotlin.math.exp
import kotlin.math.ln

data class TypePrediction(val type: MessageType, val probability: Double)

class MessageTypeClassifier {
    private val featureWeights = mutableMapOf<MessageType, MutableMap<String, Double>>()
    private val featureTotals = mutableMapOf<MessageType, Double>()
    private val documentWeights = mutableMapOf<MessageType, Double>()
    private val vocabulary = mutableSetOf<String>()

    val knownTypes: Set<MessageType>
        get() = documentWeights.keys

    fun train(features: List<String>, type: MessageType, weight: Double = 1.0) {
        if (features.isEmpty()) return
        val weights = featureWeights.getOrPut(type) { mutableMapOf() }
        for (feature in features) {
            weights.merge(feature, weight, Double::plus)
            vocabulary += feature
        }
        featureTotals.merge(type, features.size * weight, Double::plus)
        documentWeights.merge(type, weight, Double::plus)
    }

    fun predict(features: List<String>): TypePrediction? {
        if (documentWeights.isEmpty()) return null
        val totalDocuments = documentWeights.values.sum()
        val vocabularySize = vocabulary.size.coerceAtLeast(1)
        val types = documentWeights.keys.toList()
        val scores = types.map { type ->
            val weights = featureWeights[type].orEmpty()
            val total = featureTotals[type] ?: 0.0
            var score = ln((documentWeights[type] ?: 0.0) / totalDocuments)
            for (feature in features) {
                score += ln(((weights[feature] ?: 0.0) + 1.0) / (total + vocabularySize))
            }
            score
        }
        val max = scores.max()
        val normalizer = scores.sumOf { exp(it - max) }
        val best = scores.indices.maxBy { scores[it] }
        return TypePrediction(types[best], 1.0 / normalizer)
    }
}

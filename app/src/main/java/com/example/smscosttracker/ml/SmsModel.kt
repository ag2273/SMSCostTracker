package com.example.smscosttracker.ml

import com.example.smscosttracker.data.RawSms

enum class PredictionSource { USER_LABEL, TEMPLATE, MODEL }

data class SmsPrediction(
    val type: MessageType,
    val confidence: Double,
    val source: PredictionSource,
    val merchant: String? = null,
    val amount: String? = null,
    val instrument: String? = null
)

/**
 * Learns from the user's message labels, on the device. A label applies exactly to its own
 * message, to every message of the same sender with the same template, and, through the type
 * classifier, to similar wording. Without labels the model stays silent and the rules decide.
 */
class SmsModel private constructor(
    private val exact: Map<String, MessageLabel>,
    private val templates: List<SmsTemplate>,
    private val classifier: MessageTypeClassifier,
    private val labelledTypes: Set<MessageType>
) {
    val isEmpty: Boolean
        get() = exact.isEmpty()

    fun predict(sender: String, cleanBody: String, ruleType: MessageType): SmsPrediction? {
        if (isEmpty) return null
        exact[key(sender, cleanBody)]?.let { label ->
            return SmsPrediction(
                type = label.type,
                confidence = 1.0,
                source = PredictionSource.USER_LABEL,
                merchant = label.merchant?.trim()?.takeIf { it.isNotEmpty() },
                amount = label.amount,
                instrument = label.instrument
            )
        }
        val tokens = SmsTokenizer.tokenize(cleanBody)
        for (template in templates.asReversed()) {
            val match = template.match(sender, tokens, cleanBody) ?: continue
            return SmsPrediction(
                type = match.type,
                confidence = TEMPLATE_CONFIDENCE,
                source = PredictionSource.TEMPLATE,
                merchant = match.merchant,
                amount = match.amount,
                instrument = match.instrument
            )
        }
        val prediction = classifier.predict(SmsTokenizer.features(sender, tokens)) ?: return null
        val overrides = prediction.type != ruleType &&
            ruleType in classifier.knownTypes &&
            prediction.type in labelledTypes &&
            prediction.probability >= OVERRIDE_PROBABILITY
        return if (overrides) {
            SmsPrediction(prediction.type, prediction.probability, PredictionSource.MODEL)
        } else {
            null
        }
    }

    companion object {
        const val USER_WEIGHT = 10.0
        const val OVERRIDE_PROBABILITY = 0.9
        private const val TEMPLATE_CONFIDENCE = 0.95

        val EMPTY = SmsModel(emptyMap(), emptyList(), MessageTypeClassifier(), emptySet())

        /** [labels] oldest first, so a newer label of the same template wins. */
        fun train(
            messages: List<RawSms>,
            labels: List<MessageLabel>,
            ruleType: (String) -> MessageType
        ): SmsModel {
            if (labels.isEmpty()) return EMPTY
            val exact = labels.associateBy { key(it.address, SmsText.clean(it.body)) }
            val classifier = MessageTypeClassifier()
            for (message in messages) {
                val body = SmsText.clean(message.body)
                if (key(message.address, body) in exact) continue
                classifier.train(
                    SmsTokenizer.features(message.address, SmsTokenizer.tokenize(body)),
                    ruleType(body)
                )
            }
            for (label in exact.values) {
                val body = SmsText.clean(label.body)
                classifier.train(
                    SmsTokenizer.features(label.address, SmsTokenizer.tokenize(body)),
                    label.type,
                    USER_WEIGHT
                )
            }
            return SmsModel(
                exact = exact,
                templates = labels.mapNotNull { SmsTemplate.learn(it) },
                classifier = classifier,
                labelledTypes = labels.map { it.type }.toSet()
            )
        }

        private fun key(sender: String, cleanBody: String) =
            "${SmsText.senderKey(sender)}\n${cleanBody.trim()}"
    }
}

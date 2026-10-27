package com.example.smscosttracker.classification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoryClassifierTest {
    private val classifier = CategoryClassifier()

    @Test
    fun `classifies a grocery merchant`() {
        assertEquals(Category.GROCERIES, classifier.classify("Carrefour Market"))
    }

    @Test
    fun `classifies a transport merchant`() {
        assertEquals(Category.TRANSPORT, classifier.classify("Uber Trip"))
    }

    @Test
    fun `classifies a telecom merchant`() {
        assertEquals(Category.TELECOM, classifier.classify("STC Payment"))
    }

    @Test
    fun `classifies an arabic grocery merchant`() {
        assertEquals(Category.GROCERIES, classifier.classify("كارفور"))
    }

    @Test
    fun `classifies a saudi fuel merchant`() {
        assertEquals(Category.FUEL, classifier.classify("محطة وقود"))
    }

    @Test
    fun `classifies an unknown merchant as other`() {
        assertEquals(Category.OTHER, classifier.classify("Zzxq Wibble"))
    }

    @Test
    fun `exact correction overrides the bundled model`() {
        val corrected = CategoryClassifier(corrections = mapOf("carrefour" to Category.HEALTH.name))

        assertEquals(Category.HEALTH, corrected.classify("Carrefour"))
    }

    @Test
    fun `correction teaches similar merchants`() {
        val corrected = CategoryClassifier(
            corrections = mapOf("blorp market" to Category.SHOPPING.name)
        )

        assertEquals(Category.SHOPPING, corrected.classify("Blorp Store"))
    }

    @Test
    fun `confidence is a probability for seeded merchants`() {
        val result = CategoryClassifier().classifyWithConfidence("Carrefour Hypermarket")

        assertEquals(Category.GROCERIES, result.category)
        assertTrue("confidence ${result.confidence}", result.confidence > 0.5 && result.confidence < 1.0)
    }

    @Test
    fun `unknown merchants have zero confidence`() {
        val result = CategoryClassifier().classifyWithConfidence("Zxqv Ltd")

        assertEquals(Category.OTHER, result.category)
        assertEquals(0.0, result.confidence, 0.0)
    }

    @Test
    fun `corrected merchants are certain`() {
        val corrected = CategoryClassifier(corrections = mapOf("jarir bookstore" to Category.SHOPPING.name))

        assertEquals(1.0, corrected.classifyWithConfidence("Jarir Bookstore").confidence, 0.0)
    }

    @Test
    fun `merchants pointing at two categories get low confidence`() {
        val result = CategoryClassifier().classifyWithConfidence("Carrefour Pharmacy")

        assertTrue("confidence ${result.confidence}", result.confidence < 0.6)
    }
}

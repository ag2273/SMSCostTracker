package com.example.smscosttracker.classification

import org.junit.Assert.assertEquals
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
}

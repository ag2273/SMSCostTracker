package com.example.smscosttracker.ml

import com.example.smscosttracker.data.RawSms
import com.example.smscosttracker.parsing.TransactionParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsModelTest {
    private fun sms(body: String, address: String = "SAB") = RawSms(address, body, 0L, 1L)

    private fun train(labels: List<MessageLabel>, messages: List<RawSms> = emptyList()) =
        SmsModel.train(messages, labels, TransactionParser::ruleType)

    private fun predict(model: SmsModel, body: String, address: String = "SAB") =
        model.predict(address, SmsText.clean(body), TransactionParser.ruleType(body))

    private val labelled = MessageLabel(
        address = "SAB",
        body = "Mada POS purchase\nAmount: 1,249.00\nFrom: JARIR BOOKSTORE\nCard: *4432\n12/10/26 14:05",
        type = MessageType.PURCHASE,
        merchant = "JARIR BOOKSTORE",
        amount = "1,249.00",
        instrument = "4432"
    )

    @Test
    fun `without labels the model stays silent`() {
        val model = train(emptyList(), listOf(sms("Purchase of SAR 10.00 at Panda")))

        assertTrue(model.isEmpty)
        assertNull(predict(model, "Purchase of SAR 10.00 at Panda"))
    }

    @Test
    fun `a label applies exactly to its own message`() {
        val model = train(listOf(labelled))

        val prediction = predict(model, labelled.body)!!

        assertEquals(PredictionSource.USER_LABEL, prediction.source)
        assertEquals("JARIR BOOKSTORE", prediction.merchant)
        assertEquals("4432", prediction.instrument)
    }

    @Test
    fun `a label teaches every message with the same template`() {
        val model = train(listOf(labelled))

        val prediction = predict(
            model,
            "Mada POS purchase\nAmount: 86.50\nFrom: Al Baik Restaurant Olaya\nCard: *9981\n13/10/26 21:40"
        )!!

        assertEquals(PredictionSource.TEMPLATE, prediction.source)
        assertEquals(MessageType.PURCHASE, prediction.type)
        assertEquals("Al Baik Restaurant Olaya", prediction.merchant)
        assertEquals("86.50", prediction.amount)
        assertEquals("9981", prediction.instrument)
    }

    @Test
    fun `templates are scoped to the sender and the exact wording`() {
        val model = train(listOf(labelled))

        assertNull(
            predict(
                model,
                "Mada POS purchase\nAmount: 86.50\nFrom: Al Baik\nCard: *9981\n13/10/26 21:40",
                address = "AlRajhi"
            )
        )
        assertNull(
            predict(model, "Mada POS refund\nAmount: 86.50\nFrom: Al Baik\nCard: *9981\n13/10/26 21:40")
        )
    }

    @Test
    fun `a label without a merchant span still fixes the whole template`() {
        val model = train(
            listOf(
                MessageLabel(
                    address = "STC",
                    body = "Your STC bill of SAR 230.00 has been paid. Thank you",
                    type = MessageType.BILL_OR_TRANSFER,
                    merchant = "STC Telecom"
                )
            )
        )

        val prediction = predict(model, "Your STC bill of SAR 199.00 has been paid. Thank you", "STC")!!

        assertEquals("STC Telecom", prediction.merchant)
        assertNull(prediction.amount)
    }

    @Test
    fun `the newest label of a template wins`() {
        val notice = labelled.copy(
            body = "Mada POS purchase\nAmount: 5.00\nFrom: TEST MERCHANT\nCard: *4432\n12/10/26 14:06",
            type = MessageType.NOTICE,
            merchant = "TEST MERCHANT"
        )
        val model = train(listOf(labelled, notice))

        val prediction = predict(
            model,
            "Mada POS purchase\nAmount: 7.00\nFrom: Other Shop\nCard: *4432\n12/10/26 15:00"
        )!!

        assertEquals(MessageType.NOTICE, prediction.type)
    }

    @Test
    fun `the classifier overrides the rules only for labelled types it is confident about`() {
        val promo = "Get 20% off on purchase at Noon with your SAB card"
        val history = (1..30).map { sms("Purchase of SAR $it.00 at Panda with card **1214") }
        val model = train(
            labels = listOf(MessageLabel("SAB", promo, MessageType.NOTICE)),
            messages = history + sms(promo)
        )

        val overridden = predict(
            model,
            "Get 15% off on purchase at Jarir with your SAB card, ends soon"
        )
        val untouched = predict(model, "Purchase of SAR 44.00 at Danube with card **1214")

        assertEquals(MessageType.NOTICE, overridden?.type)
        assertEquals(PredictionSource.MODEL, overridden?.source)
        assertNull(untouched)
    }
}

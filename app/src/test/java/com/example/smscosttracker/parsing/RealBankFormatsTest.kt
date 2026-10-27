package com.example.smscosttracker.parsing

import com.example.smscosttracker.data.RawSms
import com.example.smscosttracker.data.TransactionChannel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Message formats seen in real SAB, SABB, HSBC Egypt, STC Pay, QNB, urpay, Tabby, Emirates NBD,
 * Mobily Pay, D360 and Al Ahli SMS. Wording is kept exactly; every name, number, amount and
 * reference is invented.
 */
class RealBankFormatsTest {
    private fun sms(sender: String, body: String) = RawSms(sender, body, 1_700_000_000_000L, 1L)

    private fun parsed(sender: String, body: String) =
        TransactionParser.parse(sms(sender, body), defaultCurrency = "SAR")
            ?: throw AssertionError("expected a transaction from: $body")

    private fun assertSkipped(sender: String, body: String) {
        val outcome = TransactionParser.analyze(sms(sender, body), defaultCurrency = "SAR")
        assertTrue("expected skip, got $outcome for: $body", outcome is ParseOutcome.Skipped)
    }

    @Test
    fun `one-time passwords and verification codes are never spending`() {
        assertSkipped(
            "SAB",
            "كلمة مرور صالحة لمرة واحدة\nرمز: 4417\nلـ: تأكيد العملية الشرائية عبر الإنترنت\n" +
                "المبلغ: SAR 212.0\nلدى: Jarir BookStore\nتاريخ: 2025-03-02 10:11:12"
        )
        assertSkipped("SAB", "رمز التحقق: 7730\nلـ :حوالة محلية صادرة\nمن: ***4410\nمبلغ: 2100.00 SAR ")
        assertSkipped("SABB", "عزيزي حامل بطاقة ساب الإئتمانيه، يرجى استخدام الرمز 551203 لتأكيد العملية الشرائية عبر الإنترنت")
        assertSkipped(
            "STCPAY",
            "8812 is your security code \nfor: Transfer to contact\nAmount: 150.00\nDate: 2024-02-08, 23:05\n\n" +
                "Please do not share it with anyone."
        )
        assertSkipped("Tabby", "6210 is the verification code for your SAR 410.00 Amazon.sa order with Tabby.")
        assertSkipped(
            "D360 Bank",
            "OTP: 3391\nAmount: SAR 88.10\nMerchant: TRENDYOL\nFor: Internet purchase\nwith card ending *5520\nOn: 2026-02-04 21:18"
        )
    }

    @Test
    fun `own-account moves, card repayments and wallet top-ups are not spending`() {
        assertSkipped(
            "SAB",
            "حوالة صادرة: بين حساباتك\nمن: ***220\nمبلغ: 9,000.00 SAR\nإلى: ***331\nفي: 2026-01-21 10:10:23"
        )
        assertSkipped(
            "SAB",
            "حوالة واردة: بين حساباتك\nمن: ***220\nمبلغ: 9,000.00 SAR\nإلى: ***331\nفي: 2026-01-21 10:10:23"
        )
        assertSkipped(
            "SAB",
            "بطاقة إئتمانية: تسديد\nبطاقة: ***7720;MASTER\nمبلغ: 4,081.03 SAR\nمن: ***220\nفي: 2026-01-21 10:09:33"
        )
        assertSkipped(
            "SABB",
            "عزيزي حامل بطاقة ساب الائتمانية , شكرا لقد تم استلام من خلال ساب نت مبلغ 1200.00 ريال " +
                "لحساب بطاقتك الائتمانية VISA المنتهية برقم 9081 الحد المتوفر 20000.00 ريال"
        )
        assertSkipped(
            "STCPAY",
            "Transaction type: wallet topup\nWallet name: stc pay \nAmount: 500.00SAR Via: MasterCard \n" +
                "Card Number: ****4402 \nDate: 2024-03-06 \nTime: 09:15 PM"
        )
        assertSkipped("STC Bank", "Adding money to account\nAmount: 100.00 SAR\nVia: *4821\nAt: 2026-06-14 03:22")
        assertSkipped("Tabby", "Your payment of 120.43 SAR was successful. Contact help@tabby.ai to report if this was not performed by you.")
    }

    @Test
    fun `incoming money, salary, refunds and statements are not spending`() {
        assertSkipped("SABB", "تم تحويل الى حسابك 330 بمبلغ SAR 450.00 بتاريخ 01/27 14:55 شكرا")
        assertSkipped("SAB", "حوالة واردة: راتب\nمبلغ: 12,500.00 SAR\nإلى: ***220\nفي: 2025-11-25 09:54:28")
        assertSkipped("AlAhliSMS", "حوالة محلية واردة OMAR SALEH\nمبلغ ريال 3000\nحساب 210*117 \nفي 02/28 11:00 ")
        assertSkipped(
            "QNBALAHLI",
            "Dear Client, you have received amount of EGP 2500.00 on 1216 from OMAR KHALED. Your transaction reference number is 9a1b2c3d."
        )
        assertSkipped(
            "SAB",
            "بطاقة إئتمانية: إسترداد مبلغ\nبطاقة: MASTER\nرقم: 4402\nمن: AMAZON SA\nمبلغ: 59.90 SAR\nرصيد: 9000.00 SAR"
        )
        assertSkipped(
            "SAB",
            "عزيزي العميل،\nتم إصدار كشف حساب بطاقتك الائتمانية.\nبطاقة: MASTER\nالمبلغ المستحق: 1,217.00 ريال"
        )
        assertSkipped(
            "EmiratesNBD",
            "Finance Type:VEHICLE IJARAH\nMonthly Installment:1,288.06\nTotal Outstanding Amount:40,544.15\nNext Billing Date:01-10-2026"
        )
        assertSkipped(
            "SABB",
            "لقد تم تفعيل خدمة الشراء عبر الانترنت لبطاقة مدى الخاصة بحسابك 330 والحد اليومي الخاص بك هو 5,000 ريال سعودي"
        )
    }

    @Test
    fun `SAB card purchases read merchant and card`() {
        val pos = parsed(
            "SAB",
            "شراء عبر نقاط البيع\nبطاقة: ***4821;mada(Apple Pay);\nمبلغ: SAR 70.00\nلدى: GREEN LEAF CAFE\nفي: 2025-12-18 14:03:05"
        )
        assertEquals(7000L, pos.amountMinor)
        assertEquals("GREEN LEAF CAFE", pos.merchant)
        assertEquals("SAB 4821", pos.account)
        assertEquals(TransactionChannel.CARD, pos.channel)

        val online = parsed(
            "SAB",
            "شراء إنترنت\nبطاقة: ***4821;مدى\nمن: ***220\nمبلغ: 39.20 SAR\nلدى: Hungerstation××Riya\nفي: 2026-01-05 13:13:47"
        )
        assertEquals("Hungerstation Riya", online.merchant)
        assertEquals("SAB 4821", online.account)

        val credit = parsed(
            "SAB",
            "شراء عبر الإنترنت\nباستخدام بطاقة الأول Mastercard Premier الائتمانية (7720) بمبلغ 126.05 SAR لدى Amazon Now\n" +
                "تاريخ: 2026-01-06 14:53:19\nالرصيد: SAR 9725.90"
        )
        assertEquals("Amazon Now", credit.merchant)
        assertEquals("SAB 7720", credit.account)

        val duty = parsed(
            "SAB",
            "شراء عبر نقاط البيع\nباستخدام بطاقة الأول Mastercard Premier الائتمانية (7720) لدى Airport Duty Free Co " +
                "بمبلغ SAR 169.00 من خلال Samsung Pay\nتاريخ: 2026-01-01 19:14:57"
        )
        assertEquals("Airport Duty Free Co", duty.merchant)
    }

    @Test
    fun `SAB withdrawals, bills and transfers`() {
        val atm = parsed(
            "SAB",
            "سحب: صراف آلي\nمكان السحب: الرياض\nبطاقة: ***مدى;4821\nمن: ***220\nمبلغ: 450.00 SAR\nفي: 2026-01-02 16:33:30"
        )
        assertEquals("ATM withdrawal", atm.merchant)
        assertEquals("SAB 4821", atm.account)

        val bill = parsed(
            "SAB",
            "سداد فاتورة\nمن: ***220\nمفوتر: 005  موبايلي - المفوتره \nلخدمة: PHONE\nمبلغ: 210.35 SAR\nفي: 2026-02-03 22:32:11"
        )
        assertEquals("موبايلي - المفوتره", bill.merchant)
        assertEquals("SAB 220", bill.account)

        val transfer = parsed(
            "SAB",
            "حوالة صادرة مقبولة\nمن: *****4410\nإلى: Omar Saleh Ali\nالآيبان: *****1188\nمصرف الراجحي\n" +
                "المبلغ: SAR 175.00\nالتاريخ: 2025-08-29 19:16:36"
        )
        assertEquals("Omar Saleh Ali", transfer.merchant)
        assertEquals(17500L, transfer.amountMinor)
    }

    @Test
    fun `SABB older formats`() {
        val card = parsed(
            "SABB",
            "بطاقتك ساب MASTER المنتهية برقم 9081 استخدمت بقيمة SAR 8.52 في Uber BV الرصيد المتوفر 2607.39 الوقت والتاريخ 23/05/18 14:23 وشكرا"
        )
        assertEquals("Uber BV", card.merchant)
        assertEquals("SABB 9081", card.account)
        assertEquals(TransactionChannel.CARD, card.channel)

        val mada = parsed(
            "SABB",
            "تمت عملية دفع عبر نقاط البيع بمبلغ 73.24 ريال من بطاقة مدى 5523 في City Care Clinic بتاريخ 13/01/2019 الوقت 10:00:00"
        )
        assertEquals("City Care Clinic", mada.merchant)
        assertEquals("SABB 5523", mada.account)

        val debit = parsed("SABB", "تم تحويل من حسابك 330 بمبلغ SAR 1,391.91 بتاريخ 07/30 09:33 شكرا")
        assertEquals("Transfer", debit.merchant)
        assertEquals("SABB 330", debit.account)
    }

    @Test
    fun `HSBC Egypt formats`() {
        val card = parsed(
            "HSBC",
            "From HSBC: Your Credit Card ending with *** 6610 has been used for EGP 572.00 on 03/04/2025 at Coffee Corner Cairo. " +
                "Your available limit is EGP 104990.80"
        )
        assertEquals("EGP", card.currency)
        assertEquals("Coffee Corner Cairo", card.merchant)
        assertEquals("HSBC 6610", card.account)

        val transfer = parsed(
            "HSBC",
            "Your HSBC Account ********4402 was debited with IPN outward transfer for EGP 1,000.00 on 31-03-2025 14:16 " +
                "to Mona Adel - telda with reference 7f00aa11. For further details, please contact HSBC call centre"
        )
        assertEquals("Mona Adel - telda", transfer.merchant)
        assertEquals("HSBC 4402", transfer.account)

        val purchase = parsed(
            "HSBC",
            "From HSBC: 11MAY23 Global Travel Svc Purchase from 004-118***-002 EGP 2,790.70- Your available balance is EGP 7,084.10"
        )
        assertEquals("Global Travel Svc", purchase.merchant)
        assertEquals("HSBC 002", purchase.account)

        assertSkipped(
            "HSBC",
            "Your HSBC Account ********4402 was credited with IPN inward transfer for EGP 4,500.00 on 31-03-2025 01:05 " +
                "from SAMIR NABIL with reference 1c35beba."
        )
    }

    @Test
    fun `wallets and card issuers`() {
        val stc = parsed(
            "STCPAY",
            "Online Purchase\nCard: ***7714; VISA\nAmount: 92.01 SAR\nAt: Hungerstation\nAccount: ***3301\nDate: 2024/08/19 20:43:08"
        )
        assertEquals("Hungerstation", stc.merchant)
        assertEquals("STCPAY 7714", stc.account)

        val visa = parsed("STCPAY", "VISA Purchase\nVia: *7714\nAmount: 3 SAR\nFrom: Hungerstation \nAt: 04/03/25 17:30")
        assertEquals("Hungerstation", visa.merchant)
        assertEquals("STCPAY 7714", visa.account)

        val qnb = parsed(
            "QNBALAHLI",
            "Your Debit Card **3302 had a Successful transaction of EGP 75.02 @Amazon Retail,your available bal.EGP2622.95 " +
                "for lost/stolen card call 19700"
        )
        assertEquals("Amazon Retail", qnb.merchant)
        assertEquals("QNBALAHLI 3302", qnb.account)

        val urpay = parsed("urpay", "Online Purchase\nCard: 6610:MADA\nAmount: SAR 6.46\nAt: Jeeny\nDate: 26-11-2024 17:12")
        assertEquals("urpay 6610", urpay.account)
        assertEquals("Jeeny", urpay.merchant)

        val mobily = parsed(
            "Mobily Pay",
            "عملية شراء دولية \nبمبلغ: SAR 17.79 \nلدى: Google One \nبطاقة: VISA****8800 \nفي: 22-09-2026 18:53:52"
        )
        assertEquals("Google One", mobily.merchant)
        assertEquals("Mobily Pay 8800", mobily.account)

        val tabby = parsed(
            "Tabby",
            "تم تأكيد عملية الشراء بقيمة 554.00 SAR من Rose Boutique - Shopify KSA. تتبع مدفوعاتك القادمة في تطبيق تابي"
        )
        assertEquals("Rose Boutique - Shopify KSA", tabby.merchant)
    }
}

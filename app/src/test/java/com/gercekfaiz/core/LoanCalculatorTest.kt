package com.gercekfaiz.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LoanCalculatorTest {

    // ---------- Tek teklif ----------

    @Test
    fun zeroInterestNoFees_rateIsZero() {
        val r = LoanCalculator.monthlyIrr(net = 12000.0, payment = 1000.0, months = 12)
        assertEquals(0.0, r, 1e-12)
    }

    @Test
    fun knownAnnuity_matchesExcelPmt() {
        // Excel: =PMT(0.03;12;-100000) -> 10046,21
        val a = LoanCalculator.installmentFromRate(100_000.0, 12, 0.03)
        assertEquals(10046.21, a, 0.01)
    }

    @Test
    fun roundTrip_irrRecoversTheRate() {
        val a = LoanCalculator.installmentFromRate(100_000.0, 12, 0.03)
        val r = LoanCalculator.monthlyIrr(100_000.0, a, 12)
        assertEquals(0.03, r, 1e-9)
    }

    @Test
    fun upfrontFee_raisesEffectiveRate() {
        val a = LoanCalculator.installmentFromRate(100_000.0, 12, 0.03)
        val withFee = LoanCalculator.analyze(LoanOffer(100_000.0, 12, a, upfrontFees = 1000.0))
        assertTrue(withFee.monthlyEffectiveRate > 0.03)
        assertEquals(0.031688, withFee.monthlyEffectiveRate, 1e-5)
    }

    @Test
    fun taxes_multiplyTheEffectiveRate() {
        val a = LoanCalculator.installmentFromRate(100_000.0, 12, 0.03, kkdfRate = 0.15, bsmvRate = 0.10)
        // 0.03 * (1 + 0.15 + 0.10) = 0.0375
        assertEquals(LoanCalculator.installmentFromRate(100_000.0, 12, 0.0375), a, 1e-9)
        val r = LoanCalculator.monthlyIrr(100_000.0, a, 12)
        assertEquals(0.0375, r, 1e-9)
    }

    @Test
    fun annualCompounding() {
        assertEquals(0.12682503, LoanCalculator.annualFromMonthly(0.01), 1e-8)
    }

    @Test
    fun monthlyFromAnnual_isInverse() {
        val m = LoanCalculator.monthlyFromAnnual(LoanCalculator.annualFromMonthly(0.03))
        assertEquals(0.03, m, 1e-12)
    }

    @Test
    fun realRate_fisher() {
        assertEquals(0.2, LoanCalculator.realRate(0.5, 0.25), 1e-12)
    }

    @Test
    fun schedule_balanceReachesZero() {
        val rows = LoanCalculator.schedule(100_000.0, 12, 0.03, 0.15, 0.10)
        assertEquals(12, rows.size)
        assertEquals(0.0, rows.last().balance, 1e-4)
        assertEquals(100_000.0, rows.sumOf { it.principalPart }, 1e-4)
    }

    @Test
    fun analyze_totalsAreConsistent() {
        val o = LoanOffer(50_000.0, 24, 2800.0, upfrontFees = 500.0, monthlyFees = 10.0)
        val r = LoanCalculator.analyze(o)
        assertEquals(49_500.0, r.netReceived, 1e-9)
        assertEquals(2810.0 * 24, r.totalPaid, 1e-9)
        assertEquals(2810.0 * 24 + 500.0 - 50_000.0, r.totalCost, 1e-9)
    }

    // ---------- Bugünkü değer ve karşılaştırma ----------

    private val offerA = LoanOffer(100_000.0, 12, 10046.21)
    private val offerB = LoanOffer(100_000.0, 24, 5600.0, upfrontFees = 1500.0)

    @Test
    fun costToday_atZeroDiscount_equalsTotalCost() {
        val cost = LoanCalculator.costToday(offerA, 0.0)
        assertEquals(LoanCalculator.analyze(offerA).totalCost, cost, 1e-6)
    }

    @Test
    fun costToday_atOwnEffectiveRate_isZero() {
        // IRR'nin tanımı: kendi efektif faiziyle iskonto edilince maliyet sıfırlanır
        val annual = LoanCalculator.analyze(offerB).annualEffectiveRate
        assertEquals(0.0, LoanCalculator.costToday(offerB, annual), 1e-4)
    }

    @Test
    fun ranking_flipsWithInflation() {
        // B'nin faizi daha düşük ama vadesi uzun.
        // Enflasyon yokken toplam TL'ye bakılır: A daha ucuz.
        val noInflation = LoanCalculator.compare(offerA, offerB, 0.0)
        assertEquals('B', noInflation.lowerRate)
        assertEquals('A', noInflation.cheaper)
        assertEquals(20554.52, noInflation.costTodayA, 0.01)
        assertEquals(35900.0, noInflation.costTodayB, 0.01)

        // %40 enflasyonda geç ödenen TL daha az değerli: B daha ucuz.
        val highInflation = LoanCalculator.compare(offerA, offerB, 0.40)
        assertEquals('B', highInflation.cheaper)
        assertEquals(940.01, highInflation.costTodayA, 0.01)
        assertEquals(-2043.32, highInflation.costTodayB, 0.01)
    }

    @Test
    fun identicalOffers_noWinner() {
        val c = LoanCalculator.compare(offerA, offerA, 0.3)
        assertNull(c.cheaper)
        assertNull(c.lowerRate)
        assertTrue(c.samePrincipal)
    }

    @Test
    fun differentPrincipals_areFlagged() {
        val c = LoanCalculator.compare(offerA, offerB.copy(principal = 80_000.0), 0.0)
        assertFalse(c.samePrincipal)
    }

    // ---------- Erken kapama ----------

    @Test
    fun remainingBalance_matchesSchedule() {
        // Faiz + vergi tablosundaki bakiye ile taksitten geri hesaplanan bakiye aynı olmalı
        val rows = LoanCalculator.schedule(100_000.0, 24, 0.03, 0.15, 0.10)
        val a = rows.first().payment
        val bal = LoanCalculator.remainingBalance(100_000.0, 24, a, paidMonths = 10)
        assertEquals(rows[9].balance, bal, 1e-4)
        assertEquals(68646.47, bal, 0.01)
    }

    @Test
    fun remainingBalance_beforeFirstPayment_isPrincipal() {
        val a = LoanCalculator.installmentFromRate(100_000.0, 12, 0.03)
        assertEquals(100_000.0, LoanCalculator.remainingBalance(100_000.0, 12, a, 0), 1e-4)
    }

    @Test
    fun earlyPayoff_withoutFee_returnsContractRate() {
        val a = LoanCalculator.installmentFromRate(100_000.0, 24, 0.0375)
        val p = LoanCalculator.earlyPayoff(100_000.0, 24, a, paidMonths = 10)
        assertEquals(14, p.remainingPayments)
        assertEquals(20839.99, p.saving, 0.01)
        assertEquals(LoanCalculator.annualFromMonthly(0.0375), p.annualReturn, 1e-6)
    }

    @Test
    fun earlyPayoff_feeLowersReturn() {
        val a = LoanCalculator.installmentFromRate(100_000.0, 24, 0.0375)
        val p = LoanCalculator.earlyPayoff(100_000.0, 24, a, paidMonths = 10, feeRate = 0.02)
        assertEquals(1372.93, p.fee, 0.01)
        assertEquals(0.5030018, p.annualReturn, 1e-6)
    }

    // ---------- Yeniden yapılandırma ----------

    private val oldA = LoanCalculator.installmentFromRate(100_000.0, 24, 0.0375)

    @Test
    fun refinance_identicalLoan_noGain() {
        // Yeni kredi eskinin kalanının birebir aynısıysa kazanç sıfır olmalı (her iskontoda)
        val bal = LoanCalculator.remainingBalance(100_000.0, 24, oldA, 10)
        val same = LoanOffer(bal, 14, oldA)
        for (d in listOf(0.0, 0.3, 0.6)) {
            val r = LoanCalculator.refinance(100_000.0, 24, oldA, 10, 0.0, same, d)
            assertEquals(0.0, r.gain, 1e-6)
        }
    }

    @Test
    fun refinance_lowerRateSameTerm_gains() {
        val bal = LoanCalculator.remainingBalance(100_000.0, 24, oldA, 10)
        val newA = LoanCalculator.installmentFromRate(bal, 14, 0.03)
        val r = LoanCalculator.refinance(100_000.0, 24, oldA, 10, 0.0, LoanOffer(bal, 14, newA), 0.0)
        assertEquals(4408.17, r.gain, 0.01)
        assertEquals(0, r.extraMonths)
        assertEquals(0.0, r.cashToday, 1e-6)
    }

    @Test
    fun refinance_longerTermWithFee_dependsOnInflation() {
        // Faiz düşük (%2,5) ama vade 36 ay ve 2.000 TL masraf var
        val bal = LoanCalculator.remainingBalance(100_000.0, 24, oldA, 10)
        val newA = LoanCalculator.installmentFromRate(bal + 2000, 36, 0.025)
        val offer = LoanOffer(bal + 2000, 36, newA, upfrontFees = 2000.0)

        val flat = LoanCalculator.refinance(100_000.0, 24, oldA, 10, 0.0, offer, 0.0)
        assertEquals(-18479.48, flat.gain, 0.01)     // düz TL'de zararlı
        assertEquals(22, flat.extraMonths)

        val infl = LoanCalculator.refinance(100_000.0, 24, oldA, 10, 0.0, offer, 0.40)
        assertEquals(5948.00, infl.gain, 0.01)       // %40 enflasyonda kârlı
    }

    // ---------- YMO kontrolü ----------

    @Test
    fun ymoCheck() {
        assertEquals(YmoCheck.MATCH, LoanCalculator.checkYmo(0.4258, 0.4250))
        assertEquals(YmoCheck.OURS_HIGHER, LoanCalculator.checkYmo(0.45, 0.4258))
        assertEquals(YmoCheck.OURS_LOWER, LoanCalculator.checkYmo(0.40, 0.4258))
    }

    // ---------- Peşin mi taksit mi ----------

    @Test
    fun cashVsInstallment_hiddenRate() {
        val c = LoanCalculator.cashVsInstallment(10_800.0, 12_000.0, 12, 0.40)
        assertEquals(1000.0, c.monthlyPayment, 1e-9)
        assertEquals(1200.0, c.extraPaid, 1e-9)
        assertEquals(0.218341, c.hiddenAnnualRate, 1e-5)
        assertEquals(-752.43, c.costToday, 0.01)   // eksi: taksit daha kârlı
    }

    @Test
    fun zeroInterestInstallment_alwaysWinsUnderInflation() {
        val c = LoanCalculator.cashVsInstallment(12_000.0, 12_000.0, 6, 0.30)
        assertEquals(0.0, c.hiddenAnnualRate, 1e-12)
        assertTrue(c.costToday < 0)
    }

    // ---------- Türkçe sayı girişi ----------

    @Test
    fun trNumber_parsesTurkishFormats() {
        assertEquals(100000.0, TrNumber.parse("100.000")!!, 1e-9)
        assertEquals(1000000.0, TrNumber.parse("1.000.000")!!, 1e-9)
        assertEquals(10046.21, TrNumber.parse("10.046,21")!!, 1e-9)
        assertEquals(10046.21, TrNumber.parse("10046,21")!!, 1e-9)
        assertEquals(3.5, TrNumber.parse("3.5")!!, 1e-9)
        assertEquals(40.0, TrNumber.parse(" 40 ")!!, 1e-9)
        assertNull(TrNumber.parse(""))
        assertNull(TrNumber.parse("abc"))
    }
}

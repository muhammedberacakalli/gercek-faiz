package com.gercekfaiz.core

import kotlin.math.pow

/**
 * Tüm oranlar KESİR olarak verilir: %3,5 -> 0.035
 */

/** Bankanın verdiği teklif. */
data class LoanOffer(
    val principal: Double,            // anlaşılan kredi tutarı
    val months: Int,                  // vade (ay)
    val monthlyInstallment: Double,   // bankanın söylediği aylık taksit (vergiler dahil)
    val upfrontFees: Double = 0.0,    // başta kesilenler: dosya masrafı, sigorta vb.
    val monthlyFees: Double = 0.0     // her ay taksite ek ödenenler
)

data class LoanAnalysis(
    val netReceived: Double,          // eline gerçekten geçen tutar
    val totalPaid: Double,            // vade boyunca ödediğin toplam
    val totalCost: Double,            // toplam ödeme + peşin masraf - çekilen kredi
    val monthlyEffectiveRate: Double, // aylık efektif faiz (IRR)
    val annualEffectiveRate: Double   // yıllık bileşik efektif faiz
)

data class ScheduleRow(
    val month: Int,
    val payment: Double,
    val principalPart: Double,
    val interest: Double,
    val taxes: Double,
    val balance: Double
)

/** İki teklifin karşılaştırma sonucu. */
data class OfferComparison(
    val a: LoanAnalysis,
    val b: LoanAnalysis,
    val costTodayA: Double,           // A'nın bugünkü parayla maliyeti
    val costTodayB: Double,           // B'nin bugünkü parayla maliyeti
    val discountRate: Double,         // kullanılan yıllık iskonto (enflasyon) oranı
    val samePrincipal: Boolean        // iki teklifte de aynı tutar mı çekiliyor
) {
    /** Bugünkü değere göre daha ucuz olan: 'A', 'B' ya da eşitse null. */
    val cheaper: Char? get() = when {
        kotlin.math.abs(costTodayA - costTodayB) < 0.005 -> null
        costTodayA < costTodayB -> 'A'
        else -> 'B'
    }

    /** Yıllık efektif faizi düşük olan. */
    val lowerRate: Char? get() = when {
        kotlin.math.abs(a.annualEffectiveRate - b.annualEffectiveRate) < 1e-9 -> null
        a.annualEffectiveRate < b.annualEffectiveRate -> 'A'
        else -> 'B'
    }

    val difference: Double get() = kotlin.math.abs(costTodayA - costTodayB)
}

data class EarlyPayoff(
    val balance: Double,              // kalan anapara
    val fee: Double,                  // erken ödeme ücreti
    val payoffAmount: Double,         // bugün ödenmesi gereken toplam
    val remainingPayments: Int,       // kalan taksit sayısı
    val remainingTotal: Double,       // kapatmazsan ödeyeceğin toplam
    val saving: Double,               // kalan toplam - bugün ödenen
    val annualReturn: Double          // erken kapamanın yıllık getirisi
)

data class CashVsInstallment(
    val monthlyPayment: Double,
    val extraPaid: Double,            // taksitli toplam - peşin fiyat
    val hiddenAnnualRate: Double,     // taksitin içindeki gizli yıllık faiz
    val costToday: Double,            // taksitin bugünkü parayla ek maliyeti (eksiyse taksit kârlı)
    val discountRate: Double
)

data class Refinance(
    val payoff: EarlyPayoff,          // eski kredinin kapama hesabı
    val newAnalysis: LoanAnalysis,    // yeni kredinin analizi
    val oldAnnualRate: Double,        // eski kredinin YMO'su (masrafsız, taksitten)
    val cashToday: Double,            // yeni krediden net gelen − kapama tutarı (eksiyse cepten)
    val stayTotal: Double,            // kalırsan ödeyeceğin toplam
    val refiTotal: Double,            // yapılandırırsan ödeyeceğin toplam (bugünkü fark dahil)
    val stayCostToday: Double,
    val refiCostToday: Double,
    val gain: Double,                 // artıysa yapılandırmak kârlı
    val discountRate: Double,
    val extraMonths: Int              // yeni vade − kalan vade (artıysa borç uzuyor)
)

enum class YmoCheck { MATCH, OURS_HIGHER, OURS_LOWER }

object LoanCalculator {

    /**
     * Eşit taksitli kredinin taksiti.
     * KKDF ve BSMV faize uygulandığı için etkin aylık oran: r * (1 + kkdf + bsmv)
     * A = P * r' / (1 - (1 + r')^-n)
     */
    fun installmentFromRate(
        principal: Double,
        months: Int,
        monthlyRate: Double,
        kkdfRate: Double = 0.0,
        bsmvRate: Double = 0.0
    ): Double {
        require(principal > 0 && months > 0 && monthlyRate >= 0) { "Geçersiz giriş" }
        val r = monthlyRate * (1 + kkdfRate + bsmvRate)
        if (r == 0.0) return principal / months
        return principal * r / (1 - (1 + r).pow(-months))
    }

    /** Faiz oranından ödeme planı (anapara / faiz / vergi ayrımı ile). */
    fun schedule(
        principal: Double,
        months: Int,
        monthlyRate: Double,
        kkdfRate: Double = 0.0,
        bsmvRate: Double = 0.0
    ): List<ScheduleRow> {
        val a = installmentFromRate(principal, months, monthlyRate, kkdfRate, bsmvRate)
        var balance = principal
        return (1..months).map { t ->
            val interest = balance * monthlyRate
            val taxes = interest * (kkdfRate + bsmvRate)
            val principalPart = a - interest - taxes
            balance -= principalPart
            ScheduleRow(t, a, principalPart, interest, taxes, balance)
        }
    }

    /** n ay boyunca her ay ödenen sabit tutarın bugünkü değeri. */
    private fun presentValue(payment: Double, months: Int, monthlyRate: Double): Double {
        val d = 1.0 / (1.0 + monthlyRate)
        var v = 1.0
        var pv = 0.0
        repeat(months) {
            v *= d
            pv += payment * v
        }
        return pv
    }

    /**
     * f(r) = net - Σ ödeme / (1+r)^t
     * Ödemeler pozitif olduğu için r büyüdükçe toplam iskonto edilmiş ödeme küçülür,
     * yani f(r) kesin artandır -> kök varsa tektir ve bisection kesin yakınsar.
     */
    private fun npv(rate: Double, net: Double, payment: Double, months: Int): Double =
        net - presentValue(payment, months, rate)

    /** Aylık IRR: eline geçen tutarı, aylık ödemelere eşitleyen oran. */
    fun monthlyIrr(net: Double, payment: Double, months: Int): Double {
        require(net > 0 && payment > 0 && months > 0) { "Geçersiz giriş" }
        // Toplam ödeme eline geçenden fazla değilse faiz yoktur (negatif oran kırpılır).
        if (payment * months <= net) return 0.0

        var lo = 0.0
        var hi = 1.0
        while (npv(hi, net, payment, months) < 0.0) {
            hi *= 2
            check(hi < 1e6) { "Çözüm bulunamadı" }
        }
        repeat(200) {
            val mid = (lo + hi) / 2
            if (npv(mid, net, payment, months) < 0.0) lo = mid else hi = mid
        }
        return (lo + hi) / 2
    }

    fun analyze(offer: LoanOffer): LoanAnalysis {
        require(offer.principal > 0) { "Kredi tutarı sıfırdan büyük olmalı" }
        require(offer.months > 0) { "Vade en az 1 ay olmalı" }
        require(offer.monthlyInstallment > 0) { "Taksit sıfırdan büyük olmalı" }
        val net = offer.principal - offer.upfrontFees
        require(net > 0) { "Peşin masraflar kredi tutarını aşamaz" }

        val payment = offer.monthlyInstallment + offer.monthlyFees
        val monthly = monthlyIrr(net, payment, offer.months)
        val totalPaid = payment * offer.months

        return LoanAnalysis(
            netReceived = net,
            totalPaid = totalPaid,
            totalCost = totalPaid + offer.upfrontFees - offer.principal,
            monthlyEffectiveRate = monthly,
            annualEffectiveRate = annualFromMonthly(monthly)
        )
    }

    /**
     * Teklifin bugünkü parayla maliyeti:
     *   Σ ödeme / (1+d)^t  -  eline geçen tutar
     * d, yıllık iskonto oranının aylık bileşik karşılığıdır.
     * İskonto 0 ise sonuç düz toplam maliyete eşittir.
     * İskonto teklifin kendi efektif faizine eşitse sonuç 0 olur (IRR'nin tanımı).
     */
    fun costToday(offer: LoanOffer, annualDiscountRate: Double): Double {
        require(annualDiscountRate > -1.0) { "İskonto oranı -%100'den büyük olmalı" }
        val net = offer.principal - offer.upfrontFees
        val d = monthlyFromAnnual(annualDiscountRate)
        return presentValue(offer.monthlyInstallment + offer.monthlyFees, offer.months, d) - net
    }

    fun compare(a: LoanOffer, b: LoanOffer, annualDiscountRate: Double): OfferComparison =
        OfferComparison(
            a = analyze(a),
            b = analyze(b),
            costTodayA = costToday(a, annualDiscountRate),
            costTodayB = costToday(b, annualDiscountRate),
            discountRate = annualDiscountRate,
            samePrincipal = kotlin.math.abs(a.principal - b.principal) < 0.005
        )

    // ---------- Erken kapama ----------

    /**
     * k taksit ödendikten sonra kalan anapara.
     * Vergili taksitte bakiye her ay b(t+1) = b(t)·(1+r') - A ile ilerler; r' = r·(1+KKDF+BSMV).
     * Bu, r' oranındaki bir annüitenin aynısıdır. r' de tam olarak sözleşmenin IRR'si olduğu için
     * kalan borç = kalan taksitlerin r' ile bugünkü değeri. Vergi oranlarını bilmeye gerek kalmaz.
     */
    fun remainingBalance(principal: Double, months: Int, installment: Double, paidMonths: Int): Double {
        require(paidMonths in 0 until months) { "Ödenen taksit sayısı 0 ile ${months - 1} arasında olmalı" }
        val r = monthlyIrr(principal, installment, months)
        return presentValue(installment, months - paidMonths, r)
    }

    fun earlyPayoff(
        principal: Double,
        months: Int,
        installment: Double,
        paidMonths: Int,
        feeRate: Double = 0.0              // erken ödeme ücreti, kalan anaparanın oranı
    ): EarlyPayoff {
        require(feeRate >= 0) { "Ücret oranı negatif olamaz" }
        val balance = remainingBalance(principal, months, installment, paidMonths)
        val fee = balance * feeRate
        val payoff = balance + fee
        val remaining = months - paidMonths
        val remainingTotal = installment * remaining
        // Erken kapamanın "getirisi": bugün payoff ödeyip, kalan taksitlerden kurtulmak.
        val monthlyReturn = monthlyIrr(payoff, installment, remaining)
        return EarlyPayoff(
            balance = balance,
            fee = fee,
            payoffAmount = payoff,
            remainingPayments = remaining,
            remainingTotal = remainingTotal,
            saving = remainingTotal - payoff,
            annualReturn = annualFromMonthly(monthlyReturn)
        )
    }

    // ---------- Yeniden yapılandırma (refinans) ----------

    /**
     * Eski krediyi bugün kapatıp yeni bir krediyle devam etmek.
     *
     * Kalmak:      kalan eski taksitler  → bugünkü değeri  PV(eski)
     * Yapılandırmak: bugün kapama tutarını öde, yeni krediden eline net geçeni al,
     *               sonra yeni taksitleri öde → (kapama − yeni net) + PV(yeni)
     *
     * Kazanç = kalmanın maliyeti − yapılandırmanın maliyeti  (artıysa yapılandırmak kârlı)
     * İskonto 0 ise düz TL toplamları karşılaştırılmış olur.
     */
    fun refinance(
        oldPrincipal: Double,
        oldMonths: Int,
        oldInstallment: Double,
        paidMonths: Int,
        payoffFeeRate: Double,
        newOffer: LoanOffer,
        annualDiscountRate: Double
    ): Refinance {
        val payoff = earlyPayoff(oldPrincipal, oldMonths, oldInstallment, paidMonths, payoffFeeRate)
        val newAnalysis = analyze(newOffer)
        val d = monthlyFromAnnual(annualDiscountRate)

        val cashToday = newAnalysis.netReceived - payoff.payoffAmount   // eksiyse cebinden eklersin
        val newPayment = newOffer.monthlyInstallment + newOffer.monthlyFees

        val stayCost = presentValue(oldInstallment, payoff.remainingPayments, d)
        val refiCost = -cashToday + presentValue(newPayment, newOffer.months, d)

        return Refinance(
            payoff = payoff,
            newAnalysis = newAnalysis,
            oldAnnualRate = annualFromMonthly(monthlyIrr(oldPrincipal, oldInstallment, oldMonths)),
            cashToday = cashToday,
            stayTotal = oldInstallment * payoff.remainingPayments,
            refiTotal = -cashToday + newPayment * newOffer.months,
            stayCostToday = stayCost,
            refiCostToday = refiCost,
            gain = stayCost - refiCost,
            discountRate = annualDiscountRate,
            extraMonths = newOffer.months - payoff.remainingPayments
        )
    }

    // ---------- YMO kontrolü ----------

    /**
     * Hesapladığımız YMO ile bankanın açıkladığını karşılaştırır.
     * Tolerans: ay sayımı ve gün farkları yüzünden küçük sapmalar normaldir.
     */
    fun checkYmo(computed: Double, bankDeclared: Double, tolerance: Double = 0.005): YmoCheck = when {
        computed - bankDeclared > tolerance -> YmoCheck.OURS_HIGHER
        bankDeclared - computed > tolerance -> YmoCheck.OURS_LOWER
        else -> YmoCheck.MATCH
    }

    // ---------- Peşin mi taksit mi ----------

    /**
     * Mağazanın taksit teklifi, gizli bir kredidir: peşin fiyatı "çekip" taksitlerle geri ödersin.
     * Bu yüzden LoanOffer ile aynı hesaba girer. İlk taksit 1 ay sonra ödenir varsayılır.
     */
    fun cashVsInstallment(
        cashPrice: Double,
        installmentTotal: Double,
        count: Int,
        annualDiscountRate: Double
    ): CashVsInstallment {
        require(cashPrice > 0 && installmentTotal > 0 && count > 0) { "Tüm alanlar sıfırdan büyük olmalı" }
        val offer = LoanOffer(cashPrice, count, installmentTotal / count)
        val a = analyze(offer)
        return CashVsInstallment(
            monthlyPayment = installmentTotal / count,
            extraPaid = installmentTotal - cashPrice,
            hiddenAnnualRate = a.annualEffectiveRate,
            costToday = costToday(offer, annualDiscountRate),
            discountRate = annualDiscountRate
        )
    }

    /** Bileşik yıllık oran: (1+r)^12 - 1  (r*12 basit hesabı yanıltır). */
    fun annualFromMonthly(monthlyRate: Double): Double = (1 + monthlyRate).pow(12) - 1

    /** Yıllık orandan aylık bileşik oran: (1+R)^(1/12) - 1 */
    fun monthlyFromAnnual(annualRate: Double): Double = (1 + annualRate).pow(1.0 / 12) - 1

    /** Fisher ilişkisi: reel = (1+nominal)/(1+enflasyon) - 1 */
    fun realRate(nominalAnnual: Double, inflationAnnual: Double): Double =
        (1 + nominalAnnual) / (1 + inflationAnnual) - 1
}

/**
 * Türkçe sayı girişini okur.
 * "100.000" -> 100000   (nokta binlik ayırıcı)
 * "10.046,21" -> 10046.21
 * "10046,21" -> 10046.21
 * "3.5" -> 3.5          (binlik kalıbına uymayan nokta ondalık sayılır)
 */
object TrNumber {
    private val thousandsOnly = Regex("""^\d{1,3}(\.\d{3})+$""")

    fun parse(input: String): Double? {
        val s = input.trim().replace(" ", "")
        if (s.isEmpty()) return null
        val normalized = when {
            s.contains(',') -> s.replace(".", "").replace(',', '.')
            thousandsOnly.matches(s) -> s.replace(".", "")
            else -> s
        }
        return normalized.toDoubleOrNull()
    }
}

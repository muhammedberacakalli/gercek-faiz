package com.gercekfaiz

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gercekfaiz.core.CashVsInstallment
import com.gercekfaiz.core.EarlyPayoff
import com.gercekfaiz.core.LoanAnalysis
import com.gercekfaiz.core.LoanCalculator
import com.gercekfaiz.core.LoanOffer
import com.gercekfaiz.core.OfferComparison
import com.gercekfaiz.core.Refinance
import com.gercekfaiz.core.YmoCheck
import com.gercekfaiz.core.TrNumber
import com.gercekfaiz.ui.theme.GercekFaizTheme
import java.io.Serializable
import java.text.NumberFormat
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // Marka teması: lacivert ve altın; telefonun açık/koyu moduna uyar
            GercekFaizTheme {
                AppRoot()
            }
        }
    }
}

// =====================================================================
// Sekmeler
// =====================================================================

private data class Tab(val label: String, val icon: String)

private val tabs = listOf(
    Tab("Karşılaştır", "⚖"),
    Tab("Peşin/Taksit", "🛒"),
    Tab("Mevcut Kredi", "⏩")
)

@Composable
fun AppRoot() {
    var selected by rememberSaveable { mutableIntStateOf(0) }
    // Her sekmenin girişleri, sekmeden çıkınca da saklansın
    val stateHolder = rememberSaveableStateHolder()

    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { i, tab ->
                    NavigationBarItem(
                        selected = selected == i,
                        onClick = { selected = i },
                        icon = { Text(tab.icon) },
                        label = { Text(tab.label, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    )
                }
            }
        }
    ) { innerPadding ->
        stateHolder.SaveableStateProvider(key = selected) {
            when (selected) {
                0 -> CompareScreen(innerPadding)
                1 -> CashScreen(innerPadding)
                2 -> PayoffScreen(innerPadding)
            }
        }
    }
}

/** Tüm sekmelerin ortak kaydırılabilir sayfa iskeleti. */
@Composable
private fun Page(
    padding: PaddingValues,
    title: String,
    subtitle: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium)
        content()
        Text(
            "Bu bir hesaplama aracıdır, finansal tavsiye değildir.",
            style = MaterialTheme.typography.bodySmall
        )
    }
}

// =====================================================================
// Biçimlendirme, doğrulama ve paylaşma
// =====================================================================

private val trLocale: Locale = Locale.forLanguageTag("tr-TR")
private fun money(v: Double): String = NumberFormat.getCurrencyInstance(trLocale).format(v)

/** Dar tablo sütunları için kuruşsuz tutar: ₺100.000 (kuruşlu hali sütuna sığmayıp satır kırıyordu). */
private fun moneyShort(v: Double): String =
    NumberFormat.getCurrencyInstance(trLocale).apply { maximumFractionDigits = 0 }.format(v)
private fun pct(v: Double): String = String.format(trLocale, "%%%.2f", v * 100)

/** Doğrulanacak bir giriş alanı. */
private class Check(val label: String, val value: String, val isInt: Boolean = false, val required: Boolean = true)

/**
 * Girişlerdeki sorunları sade cümlelerle döndürür.
 * Kullanıcı hiçbir alana dokunmadıysa uyarı göstermeyiz (boş form kırmızıyla açılmasın).
 */
private fun problems(vararg checks: Check): List<String> {
    if (checks.all { it.value.isBlank() }) return emptyList()
    return checks.mapNotNull { c ->
        val v = c.value.trim()
        when {
            v.isEmpty() -> if (c.required) "${c.label} girilmedi" else null
            c.isInt && v.toIntOrNull() == null -> "${c.label} tam sayı olmalı"
            !c.isInt && TrNumber.parse(v) == null -> "${c.label} sayı olarak okunamadı"
            else -> null
        }
    }
}

@Composable
private fun Problems(list: List<String>) {
    list.forEach {
        Text("• $it", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    }
}

/** Android'in paylaşma menüsünü açar (WhatsApp, e-posta vb.). İzin gerektirmez. */
private fun shareText(context: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, "$text\n\nGerçek Faiz ile hesaplandı.")
    }
    context.startActivity(Intent.createChooser(send, "Sonucu paylaş"))
}

@Composable
private fun ShareButton(text: () -> String) {
    val context = LocalContext.current
    OutlinedButton(onClick = { shareText(context, text()) }, modifier = Modifier.fillMaxWidth()) {
        Text("Sonucu paylaş")
    }
}

// =====================================================================
// Ortak bileşenler
// =====================================================================

@Composable
private fun Field(label: String, value: String, decimal: Boolean = true, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number
        ),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun ErrorText(e: Throwable) {
    Text(e.message ?: "Girişi kontrol et", color = MaterialTheme.colorScheme.error)
}

/**
 * Etiket – değer satırı. help verilirse etiketin yanında ⓘ çıkar,
 * dokununca altında açıklama açılır.
 */
@Composable
private fun InfoRow(label: String, value: String, help: String? = null) {
    var open by remember { mutableStateOf(false) }
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (help != null) Modifier.clickable { open = !open } else Modifier),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(if (help != null) "$label ⓘ" else label, modifier = Modifier.weight(1f))
            Text(value, fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
        }
        if (help != null) {
            AnimatedVisibility(visible = open) {
                Text(
                    help,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun ResultCard(highlight: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = if (highlight)
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        else CardDefaults.cardColors()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content
        )
    }
}

@Composable
private fun Verdict(title: String, body: String) {
    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    Text(body, style = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun Note(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall)
}

/**
 * Mantık kontrolü: Türkiye'de en yüksek kredi kartı faizleri bile yıllık %100'ün altında kalır.
 * Yıllık maliyet %200'ü aşıyorsa büyük ihtimalle bir rakam yanlış girilmiştir
 * (örneğin kredi tutarında eksik sıfır). Hesap matematiksel olarak doğrudur ama anlamsızdır.
 */
@Composable
private fun SanityCheck(vararg annualRates: Double) {
    if (annualRates.any { it > 2.0 }) {
        Text(
            "⚠ Hesaplanan yıllık oran %200'ün üzerinde. Bu gerçek bir kredi için olağan dışı; " +
                    "rakamları kontrol et. Örneğin kredi tutarında bir sıfır eksik olabilir.",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.error
        )
    }
}

@Composable
private fun HelpHint() {
    Note("ⓘ işaretli satırlara dokunarak ne anlama geldiklerini görebilirsin.")
}

// ---------- Açıklama metinleri ----------

private object Help {
    const val NET = "Kredi tutarından peşin kesilen masraflar (dosya, sigorta) düşülünce eline kalan para. " +
            "Taksitleri tam tutar üzerinden ödersin ama bu kadarını kullanırsın; gerçek faizi yükselten de bu fark."
    const val TOTAL_COST = "Vade boyunca ödediğin her şey + peşin masraflar − çektiğin kredi. " +
            "Bugün ödenen 1 TL ile iki yıl sonra ödenen 1 TL'yi aynı sayar, yani enflasyonu hesaba katmaz."
    const val MONTHLY = "Eline geçen parayı, ödediğin taksitlere eşitleyen aylık oran (iç verim oranı, IRR). " +
            "Bankanın söylediği faizden farklıysa aradaki fark masraflardan ve vergilerden gelir."
    const val ANNUAL = "Yıllık maliyet oranı: faiz, masraf ve vergiler dahil, kredinin gerçek yıllık maliyeti. " +
            "Bankaların sözleşmede göstermek zorunda olduğu oran budur. 12 ile çarpılmaz, bileşik hesaplanır: " +
            "(1 + aylık)¹² − 1, çünkü her ay ödenmeyen faiz sonraki ay faiz doğurur."
    const val OLD_RATE = "Eski kredinin taksitten hesaplanan yıllık maliyeti. Eski kredinin başta ödenmiş masrafları " +
            "artık geri gelmez, bu yüzden karara etkileri yoktur; hesaba katılmaz."
    const val CASH_TODAY = "Yeni krediden eline net geçen para − eski krediyi kapatma tutarı. " +
            "Artıysa yapılandırmadan sonra elinde para kalır, eksiyse aradaki farkı cebinden eklemen gerekir."
    const val REAL = "Enflasyondan arındırılmış faiz: (1 + faiz) ÷ (1 + enflasyon) − 1. " +
            "Eksiyse, borcun gerçek değeri zamanla azalıyor demektir."
    const val COST_TODAY = "Her taksit, enflasyonla bugünkü değerine indirgenir. Örneğin %40 enflasyonda bir yıl sonra " +
            "ödenecek 1.400 TL, bugünkü 1.000 TL'ye denktir. Böylece farklı vadeler adil karşılaştırılır."
    const val HIDDEN = "Taksitli satış gizli bir kredidir: mağaza sana peşin fiyat kadar borç verir, sen taksitlerle " +
            "geri ödersin. Bu oran o kredinin yıllık efektif faizidir."
    const val EXTRA = "Taksitli toplam fiyat − peşin fiyat. Enflasyonu hesaba katmaz; asıl karar için gizli faize bak."
    const val INST_COST_TODAY = "Taksitlerin bugünkü değeri − peşin fiyat. Eksiyse taksit, peşin ödemekten kârlıdır."
    const val BALANCE = "Kalan taksitlerin, kredinin kendi faiziyle bugüne indirgenmiş değeri. Banka krediyi " +
            "kapatırken (ücret hariç) senden bu tutarı ister. Gelecekteki faizler bu tutara dahil değildir."
    const val PAYOFF_RETURN = "Bugün bir miktar para ödeyip ileride ödeyeceğin taksitlerden kurtuluyorsun. Bu, o paranın " +
            "sana kazandırdığı yıllık faizdir. Erken ödeme ücreti yoksa kredinin kendi faizine eşittir."
}

// =====================================================================
// 1) KARŞILAŞTIR
// =====================================================================

/** Bir teklifin ekrandaki ham girişleri (döndürmede kaybolmasın diye Serializable). */
data class OfferInput(
    val principal: String = "",
    val months: String = "",
    val installment: String = "",
    val upfront: String = "",
    val monthlyFee: String = "",
    val bankYmo: String = ""
) : Serializable {
    val isBlank: Boolean
        get() = listOf(principal, months, installment, upfront, monthlyFee, bankYmo).all { it.isBlank() }

    fun toOffer(): LoanOffer? {
        val p = TrNumber.parse(principal) ?: return null
        val n = months.trim().toIntOrNull() ?: return null
        val a = TrNumber.parse(installment) ?: return null
        return LoanOffer(p, n, a, TrNumber.parse(upfront) ?: 0.0, TrNumber.parse(monthlyFee) ?: 0.0)
    }

    fun issues(): List<String> = problems(
        Check("Kredi tutarı", principal),
        Check("Vade", months, isInt = true),
        Check("Aylık taksit", installment),
        Check("Peşin masraf", upfront, required = false),
        Check("Aylık ücret", monthlyFee, required = false),
        Check("Bankanın YMO'su", bankYmo, required = false)
    )

    /** Bankanın açıkladığı YMO, kesir olarak (yoksa null). */
    val bankYmoRate: Double? get() = TrNumber.parse(bankYmo)?.div(100)
}

@Composable
private fun CompareScreen(padding: PaddingValues) {
    var inputA by rememberSaveable { mutableStateOf(OfferInput()) }
    var inputB by rememberSaveable { mutableStateOf(OfferInput()) }
    var inflation by rememberSaveable { mutableStateOf("") }

    val offerA = inputA.toOffer()
    val offerB = inputB.toOffer()
    val discount = (TrNumber.parse(inflation) ?: 0.0) / 100

    Page(padding, "Teklifleri karşılaştır", "İki kredi teklifini gir, hangisinin gerçekten ucuz olduğunu gör.") {
        OfferForm("Teklif A", inputA) { inputA = it }
        OfferForm("Teklif B", inputB) { inputB = it }

        Field("Yıllık enflasyon beklentisi (%)", inflation) { inflation = it }
        Problems(problems(Check("Enflasyon", inflation, required = false)))
        Note("Boş bırakırsan karşılaştırma düz toplam TL üzerinden yapılır.")

        when {
            offerA != null && offerB != null ->
                runCatching { LoanCalculator.compare(offerA, offerB, discount) }
                    .onSuccess { ComparisonCard(it, inputA.bankYmoRate, inputB.bankYmoRate) }
                    .onFailure { ErrorText(it) }
            offerA != null && inputB.isBlank ->
                runCatching { LoanCalculator.analyze(offerA) }
                    .onSuccess { SingleCard("Teklif A", it, discount, inputA.bankYmoRate) }
                    .onFailure { ErrorText(it) }
            offerB != null && inputA.isBlank ->
                runCatching { LoanCalculator.analyze(offerB) }
                    .onSuccess { SingleCard("Teklif B", it, discount, inputB.bankYmoRate) }
                    .onFailure { ErrorText(it) }
        }
    }
}

@Composable
private fun OfferForm(title: String, input: OfferInput, onChange: (OfferInput) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Field("Kredi tutarı (TL)", input.principal) { onChange(input.copy(principal = it)) }
            Field("Vade (ay)", input.months, decimal = false) { onChange(input.copy(months = it)) }
            Field("Aylık taksit (TL)", input.installment) { onChange(input.copy(installment = it)) }
            Field("Peşin masraflar: dosya, sigorta vb. (TL)", input.upfront) { onChange(input.copy(upfront = it)) }
            Field("Taksite ek aylık ücret (TL)", input.monthlyFee) { onChange(input.copy(monthlyFee = it)) }
            Field("Bankanın açıkladığı YMO (%, isteğe bağlı)", input.bankYmo) { onChange(input.copy(bankYmo = it)) }
            Problems(input.issues())
        }
    }
}

@Composable
private fun SingleCard(title: String, r: LoanAnalysis, discount: Double, bankYmo: Double?) {
    ResultCard {
        SanityCheck(r.annualEffectiveRate)
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        InfoRow("Eline geçen", money(r.netReceived), Help.NET)
        InfoRow("Toplam ödeme", money(r.totalPaid))
        InfoRow("Toplam maliyet", money(r.totalCost), Help.TOTAL_COST)
        InfoRow("Aylık efektif faiz", pct(r.monthlyEffectiveRate), Help.MONTHLY)
        InfoRow("Yıllık maliyet oranı (YMO)", pct(r.annualEffectiveRate), Help.ANNUAL)
        YmoNote(title, r.annualEffectiveRate, bankYmo)
        if (discount != 0.0) {
            InfoRow("Reel yıllık faiz", pct(LoanCalculator.realRate(r.annualEffectiveRate, discount)), Help.REAL)
        }
        HelpHint()
        ShareButton {
            "$title: eline ${money(r.netReceived)} geçiyor, toplam ${money(r.totalPaid)} ödüyorsun.\n" +
                    "Yıllık maliyet oranı (YMO): ${pct(r.annualEffectiveRate)}"
        }
    }
}

@Composable
private fun ComparisonCard(c: OfferComparison, bankYmoA: Double?, bankYmoB: Double?) {
    val title = when (val w = c.cheaper) {
        null -> "İki teklif aynı maliyette"
        else -> "Teklif $w daha ucuz: ${money(c.difference)} fark"
    }
    val reason = if (c.discountRate == 0.0)
        "Enflasyon girilmediği için vade boyunca ödenen toplam TL karşılaştırıldı."
    else
        "Yıllık ${pct(c.discountRate)} enflasyonla, ileride ödenen her taksit bugünkü değerine indirgendi."

    ResultCard(highlight = true) {
        SanityCheck(c.a.annualEffectiveRate, c.b.annualEffectiveRate)
        Verdict(title, reason)

        HorizontalDivider()

        CompareRow("", "A", "B", better = null, header = true)
        CompareRow("Eline geçen", moneyShort(c.a.netReceived), moneyShort(c.b.netReceived), better = null)
        CompareRow("Vade boyunca ödeme", moneyShort(c.a.totalPaid), moneyShort(c.b.totalPaid), better = null)
        CompareRow("Toplam maliyet", moneyShort(c.a.totalCost), moneyShort(c.b.totalCost),
            better = lowerOf(c.a.totalCost, c.b.totalCost), help = Help.TOTAL_COST)
        CompareRow("Yıllık maliyet oranı (YMO)", pct(c.a.annualEffectiveRate), pct(c.b.annualEffectiveRate),
            better = c.lowerRate, help = Help.ANNUAL)
        if (c.discountRate != 0.0) {
            CompareRow("Bugünkü parayla maliyet", moneyShort(c.costTodayA), moneyShort(c.costTodayB),
                better = c.cheaper, help = Help.COST_TODAY)
        }

        val rate = c.lowerRate
        val cheap = c.cheaper
        if (rate != null && cheap != null && rate != cheap) {
            Note("Dikkat: Teklif $rate'nin faizi daha düşük ama bu koşullarda daha pahalı. " +
                    "Faiz, liranın kaça kiralandığını söyler; ne kadar süre kiralandığını söylemez.")
        }
        if (c.costTodayA < 0 || c.costTodayB < 0) {
            Note("Eksi maliyet: Bu enflasyon varsayımında taksitlerin bugünkü değeri, eline geçen paradan az.")
        }
        YmoNote("Teklif A", c.a.annualEffectiveRate, bankYmoA)
        YmoNote("Teklif B", c.b.annualEffectiveRate, bankYmoB)
        if (!c.samePrincipal) {
            Note("Kredi tutarları farklı. Daha büyük kredinin maliyeti doğal olarak yüksek çıkar; faiz satırına da bak.")
        }
        HelpHint()
        ShareButton {
            "$title\n$reason\n" +
                    "A: YMO ${pct(c.a.annualEffectiveRate)}, toplam ${money(c.a.totalPaid)}\n" +
                    "B: YMO ${pct(c.b.annualEffectiveRate)}, toplam ${money(c.b.totalPaid)}"
        }
    }
}

/** Hesapladığımız YMO ile bankanınkini karşılaştırıp sonucu açıklar. */
@Composable
private fun YmoNote(title: String, computed: Double, bank: Double?) {
    if (bank == null) return
    val diff = String.format(trLocale, "%.2f", kotlin.math.abs(computed - bank) * 100)
    when (LoanCalculator.checkYmo(computed, bank)) {
        YmoCheck.MATCH -> Note("✓ $title: Hesapladığımız YMO bankanın açıkladığıyla uyumlu (${pct(bank)}).")
        YmoCheck.OURS_LOWER -> Text(
            "⚠ $title: Bankanın YMO'su ($diff puan) daha yüksek. Bankanın hesaba kattığı ama senin girmediğin " +
                    "bir masraf olabilir (ör. sigorta, hesap işletim ücreti). Sözleşmedeki ücretleri kontrol et.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error
        )
        YmoCheck.OURS_HIGHER -> Text(
            "⚠ $title: Senin rakamlarınla hesaplanan YMO, bankanınkinden $diff puan yüksek. Ya bir rakam yanlış " +
                    "girildi ya da ödediğin tutarda sözleşmenin YMO'suna yansıtılmamış bir maliyet var.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error
        )
    }
}

private fun lowerOf(a: Double, b: Double): Char? = when {
    kotlin.math.abs(a - b) < 0.005 -> null
    a < b -> 'A'
    else -> 'B'
}

@Composable
private fun CompareRow(
    label: String,
    a: String,
    b: String,
    better: Char?,
    header: Boolean = false,
    help: String? = null
) {
    var open by remember { mutableStateOf(false) }
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (help != null) Modifier.clickable { open = !open } else Modifier),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(if (help != null) "$label ⓘ" else label,
                modifier = Modifier.weight(1.3f), style = MaterialTheme.typography.bodySmall)
            Text(a, modifier = Modifier.weight(1f), textAlign = TextAlign.End, maxLines = 1, softWrap = false,
                fontWeight = if (header || better == 'A') FontWeight.Bold else FontWeight.Normal)
            Text(b, modifier = Modifier.weight(1f), textAlign = TextAlign.End, maxLines = 1, softWrap = false,
                fontWeight = if (header || better == 'B') FontWeight.Bold else FontWeight.Normal)
        }
        if (help != null) {
            AnimatedVisibility(visible = open) {
                Text(help, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 4.dp))
            }
        }
    }
}

// =====================================================================
// 2) PEŞİN Mİ TAKSİT Mİ
// =====================================================================

@Composable
private fun CashScreen(padding: PaddingValues) {
    var cash by rememberSaveable { mutableStateOf("") }
    var total by rememberSaveable { mutableStateOf("") }
    var count by rememberSaveable { mutableStateOf("") }
    var inflation by rememberSaveable { mutableStateOf("") }

    Page(padding, "Peşin mi, taksit mi?", "Mağazanın peşin fiyatını ve taksitli toplam fiyatını gir.") {
        Field("Peşin fiyat (TL)", cash) { cash = it }
        Field("Taksitli toplam fiyat (TL)", total) { total = it }
        Field("Taksit sayısı", count, decimal = false) { count = it }
        Field("Yıllık enflasyon beklentisi (%)", inflation) { inflation = it }
        Problems(problems(
            Check("Peşin fiyat", cash),
            Check("Taksitli toplam fiyat", total),
            Check("Taksit sayısı", count, isInt = true),
            Check("Enflasyon", inflation, required = false)
        ))
        Note("İlk taksitin bir ay sonra ödendiği varsayılır (kredi kartı taksitlerinde olduğu gibi).")

        val c = TrNumber.parse(cash)
        val t = TrNumber.parse(total)
        val n = count.trim().toIntOrNull()
        val infl = TrNumber.parse(inflation)
        if (c != null && t != null && n != null) {
            runCatching { LoanCalculator.cashVsInstallment(c, t, n, (infl ?: 0.0) / 100) }
                .onSuccess { CashCard(it, inflationGiven = infl != null) }
                .onFailure { ErrorText(it) }
        }
    }
}

@Composable
private fun CashCard(r: CashVsInstallment, inflationGiven: Boolean) {
    val (title, body) = when {
        r.extraPaid < 0 -> "Taksitli fiyat peşinden ucuz" to
                "Taksit hem daha ucuz hem de ödemeyi zamana yayıyor. Peşin almak için bir sebep yok."
        !inflationGiven -> "Taksitin gizli faizi: yıllık ${pct(r.hiddenAnnualRate)}" to
                "Taksit, mağazadan bu faizle kredi almak demek. Karar için enflasyon beklentini gir."
        r.costToday < -0.005 -> "Taksit daha kârlı: bugünkü parayla ${money(-r.costToday)}" to
                ("Taksitin gizli faizi (${pct(r.hiddenAnnualRate)}), beklediğin enflasyondan " +
                        "(${pct(r.discountRate)}) düşük. İleride ödeyeceğin liralar bugünkünden az değerli olacak.")
        r.costToday > 0.005 -> "Peşin daha kârlı: bugünkü parayla ${money(r.costToday)}" to
                ("Taksitin gizli faizi (${pct(r.hiddenAnnualRate)}), beklediğin enflasyondan " +
                        "(${pct(r.discountRate)}) yüksek. Taksitle ödemek pahalıya geliyor.")
        else -> "İki seçenek eşit" to "Gizli faiz, beklediğin enflasyona eşit."
    }

    ResultCard(highlight = true) {
        SanityCheck(r.hiddenAnnualRate)
        Verdict(title, body)

        HorizontalDivider()

        InfoRow("Aylık taksit", money(r.monthlyPayment))
        InfoRow("Taksitle fazladan ödenen", money(r.extraPaid), Help.EXTRA)
        InfoRow("Gizli yıllık faiz", pct(r.hiddenAnnualRate), Help.HIDDEN)
        if (inflationGiven) {
            InfoRow("Taksitin bugünkü parayla ek maliyeti", money(r.costToday), Help.INST_COST_TODAY)
        }

        if (r.hiddenAnnualRate == 0.0 && r.extraPaid >= 0) {
            Note("Faizsiz taksit: enflasyon sıfırdan büyükse taksit her zaman avantajlıdır.")
        }
        Note("Peşin ödeyeceğin parayı bu sürede faizde değerlendirebiliyorsan, enflasyon yerine o getiriyi yaz.")
        HelpHint()
        ShareButton {
            "$title\n$body\nAylık taksit ${money(r.monthlyPayment)}, gizli yıllık faiz ${pct(r.hiddenAnnualRate)}"
        }
    }
}

// =====================================================================
// 3) MEVCUT KREDİ: erken kapama ve yeniden yapılandırma
// =====================================================================

@Composable
private fun PayoffScreen(padding: PaddingValues) {
    var principal by rememberSaveable { mutableStateOf("") }
    var months by rememberSaveable { mutableStateOf("") }
    var installment by rememberSaveable { mutableStateOf("") }
    var paid by rememberSaveable { mutableStateOf("") }
    var fee by rememberSaveable { mutableStateOf("") }
    var alt by rememberSaveable { mutableStateOf("") }
    var newInput by rememberSaveable { mutableStateOf(OfferInput()) }

    Page(padding, "Mevcut kredi", "Erken kapatmak mı, yeni bir krediyle yapılandırmak mı, devam etmek mi?") {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Şu anki kredin", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Field("Çekilen kredi tutarı (TL)", principal) { principal = it }
                Field("Vade (ay)", months, decimal = false) { months = it }
                Field("Aylık taksit (TL)", installment) { installment = it }
                Field("Ödediğin taksit sayısı", paid, decimal = false) { paid = it }
                Field("Erken ödeme ücreti (%, kalan anaparaya)", fee) { fee = it }
                Problems(problems(
                    Check("Kredi tutarı", principal),
                    Check("Vade", months, isInt = true),
                    Check("Aylık taksit", installment),
                    Check("Ödenen taksit sayısı", paid, isInt = true),
                    Check("Erken ödeme ücreti", fee, required = false)
                ))
            }
        }

        Field("Paranın alternatif yıllık getirisi veya enflasyon (%)", alt) { alt = it }
        Problems(problems(Check("Alternatif getiri", alt, required = false)))
        Note("Kapatmazsan bu parayı nerede değerlendirirdin? Örneğin mevduatın net (vergi sonrası) faizi. " +
                "Yapılandırma karşılaştırmasında ileride ödenecek taksitler de bu oranla bugüne indirgenir.")

        val p = TrNumber.parse(principal)
        val n = months.trim().toIntOrNull()
        val a = TrNumber.parse(installment)
        val k = paid.trim().toIntOrNull()
        val f = (TrNumber.parse(fee) ?: 0.0) / 100
        val altRate = TrNumber.parse(alt)?.div(100)

        if (p != null && n != null && a != null && k != null) {
            val payoff = runCatching { LoanCalculator.earlyPayoff(p, n, a, k, f) }
            payoff.onFailure { ErrorText(it) }
            payoff.onSuccess { r ->
                PayoffCard(r, altRate)

                Text("Yeni bir teklifle yapılandır", style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold)
                Note("Bankan ya da başka bir banka yeni kredi teklif ettiyse gir. Eski krediyi kapatmak için " +
                        "${money(r.payoffAmount)} gerekiyor; yeni kredinin tutarını buna göre gir.")
                OfferForm("Yeni kredi teklifi", newInput) { newInput = it }

                val newOffer = newInput.toOffer()
                if (newOffer != null) {
                    runCatching { LoanCalculator.refinance(p, n, a, k, f, newOffer, altRate ?: 0.0) }
                        .onSuccess { RefinanceCard(it, newInput.bankYmoRate) }
                        .onFailure { ErrorText(it) }
                }
            }
        }
    }
}

@Composable
private fun PayoffCard(r: EarlyPayoff, altRate: Double?) {
    val title = "Bugün kapatırsan ${money(r.payoffAmount)} ödersin"
    val body = "Kalan ${r.remainingPayments} taksiti ödemekten kurtulursun. Tasarruf: ${money(r.saving)}."

    ResultCard(highlight = true) {
        SanityCheck(r.annualReturn)
        Verdict(title, body)

        HorizontalDivider()

        InfoRow("Kalan anapara", money(r.balance), Help.BALANCE)
        if (r.fee > 0) InfoRow("Erken ödeme ücreti", money(r.fee))
        InfoRow("Bugün ödenecek toplam", money(r.payoffAmount))
        InfoRow("Kapatmazsan ödeyeceğin", money(r.remainingTotal))
        InfoRow("Erken kapamanın yıllık getirisi", pct(r.annualReturn), Help.PAYOFF_RETURN)

        HorizontalDivider()

        val decision = when {
            altRate == null -> null
            r.annualReturn > altRate -> "Kapatmak daha kârlı" to
                    ("Kapatmanın getirisi (${pct(r.annualReturn)}), paranın alternatif getirisinden " +
                            "(${pct(altRate)}) yüksek.")
            else -> "Kapatmamak daha kârlı" to
                    ("Paranı ${pct(altRate)} getiriyle değerlendirebiliyorsan, ${pct(r.annualReturn)} maliyetli " +
                            "krediyi sürdürmek daha mantıklı. Tasarruf rakamı büyük görünse de para elinde kalırsa daha çok kazandırır.")
        }
        if (decision == null) {
            Note("Krediyi kapatmak, parayı yıllık ${pct(r.annualReturn)} getirili bir yatırıma koymakla aynıdır. " +
                    "Karşılaştırmak için paranın alternatif getirisini gir.")
        } else {
            Verdict(decision.first, decision.second)
        }
        Note("Bankanın vereceği kapama tutarı, ay içindeki gün faizine göre birkaç lira farklı olabilir.")
        HelpHint()
        ShareButton {
            "$title\n$body\nErken kapamanın yıllık getirisi: ${pct(r.annualReturn)}" +
                    (decision?.let { "\n${it.first}" } ?: "")
        }
    }
}

@Composable
private fun RefinanceCard(r: Refinance, bankYmo: Double?) {
    val flat = r.discountRate == 0.0
    val basis = if (flat) "toplam TL olarak" else "bugünkü parayla"
    val (title, body) = when {
        r.gain > 0.005 -> "Yapılandırmak kârlı: $basis ${money(r.gain)}" to
                "Eski krediyi kapatıp yeni teklifle devam etmek, mevcut krediyi sürdürmekten daha ucuz."
        r.gain < -0.005 -> "Mevcut krediyle devam et" to
                "Yapılandırmak $basis ${money(-r.gain)} daha pahalıya geliyor."
        else -> "İki yol eşit" to "Yapılandırmak ile devam etmek arasında maliyet farkı yok."
    }
    val reason = if (flat)
        "Getiri ya da enflasyon girilmediği için ödenecek TL toplamları karşılaştırıldı."
    else
        "Yıllık ${pct(r.discountRate)} oranla, ileride ödenecek taksitler bugünkü değerine indirgendi."

    ResultCard(highlight = true) {
        SanityCheck(r.oldAnnualRate, r.newAnalysis.annualEffectiveRate)
        Verdict(title, body)
        Note(reason)

        HorizontalDivider()

        CompareRow("", "Devam", "Yapılandır", better = null, header = true)
        CompareRow("Yıllık maliyet oranı (YMO)", pct(r.oldAnnualRate), pct(r.newAnalysis.annualEffectiveRate),
            better = when {
                kotlin.math.abs(r.oldAnnualRate - r.newAnalysis.annualEffectiveRate) < 1e-9 -> null
                r.newAnalysis.annualEffectiveRate < r.oldAnnualRate -> 'B'
                else -> 'A'
            }, help = Help.OLD_RATE)
        CompareRow("Kalan vade (ay)", "${r.payoff.remainingPayments}",
            "${r.payoff.remainingPayments + r.extraMonths}", better = null)
        CompareRow("Ödenecek toplam", moneyShort(r.stayTotal), moneyShort(r.refiTotal),
            better = lowerOf(r.stayTotal, r.refiTotal),
            help = Help.TOTAL_COST)
        if (!flat) {
            CompareRow("Bugünkü parayla maliyet", moneyShort(r.stayCostToday), moneyShort(r.refiCostToday),
                better = lowerOf(r.stayCostToday, r.refiCostToday), help = Help.COST_TODAY)
        }

        HorizontalDivider()

        InfoRow(
            if (r.cashToday >= 0) "Kapamadan sonra eline kalan" else "Cebinden eklemen gereken",
            money(kotlin.math.abs(r.cashToday)),
            Help.CASH_TODAY
        )

        if (r.extraMonths > 0) {
            Note("Borç ${r.extraMonths} ay uzuyor. Faizi düşük olsa bile daha uzun süre ödemek toplamı artırır; " +
                    "bunu ancak enflasyon ya da paranın getirisi telafi edebilir.")
        }
        if (r.newAnalysis.annualEffectiveRate < r.oldAnnualRate && r.gain < -0.005) {
            Note("Dikkat: Yeni teklifin YMO'su daha düşük ama vade ve masraflar yüzünden bu koşullarda daha pahalı.")
        }
        YmoNote("Yeni teklif", r.newAnalysis.annualEffectiveRate, bankYmo)
        HelpHint()
        ShareButton {
            "$title\n$body\nMevcut kredi YMO ${pct(r.oldAnnualRate)}, yeni teklif YMO " +
                    "${pct(r.newAnalysis.annualEffectiveRate)}\n$reason"
        }
    }
}

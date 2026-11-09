<p align="center">
  <img src="docs/ikon.png" width="96" alt="Gerçek Faiz ikonu">
</p>

<h1 align="center">Gerçek Faiz</h1>

<p align="center"><b>Banka %3 diyor. Sen gerçekte ne ödüyorsun?</b></p>

Gerçek Faiz, kredi ve taksit tekliflerinin **masraflar, vergiler ve enflasyon dahil** gerçek maliyetini hesaplayan bir Android uygulamasıdır. Sadece rakam vermez, "hangisi daha ucuz?" sorusuna net bir cevap verir.

Kotlin · Jetpack Compose · Material 3 · JUnit · minSdk 26

## Ekranlar

| Karşılaştır | Enflasyonla | Peşin / Taksit | Mevcut Kredi |
| :---: | :---: | :---: | :---: |
| <img src="docs/ekranlar/ekran_1_karsilastir.jpg" width="200"> | <img src="docs/ekranlar/ekran_2_enflasyon.jpg" width="200"> | <img src="docs/ekranlar/ekran_3_pesin_taksit.jpg" width="200"> | <img src="docs/ekranlar/ekran_4_mevcut_kredi.jpg" width="200"> |

## Özellikler

- **Teklifleri karşılaştır:** İki kredi teklifinin masraflar dahil yıllık maliyet oranını (YMO), toplam maliyetini ve enflasyona göre bugünkü değerini hesaplar. Faizi düşük görünen ama vadesi yüzünden pahalıya gelen teklifi uyarır. Bankanın açıkladığı YMO ile kendi hesabını karşılaştırır.
- **Peşin mi, taksit mi?** Mağazanın taksitli fiyatına gizlenmiş faizi bulur ve beklenen enflasyonla karşılaştırır.
- **Mevcut kredi:** Erken kapama tutarını ve getirisini hesaplar; yeni bir teklifle yeniden yapılandırmanın kâr mı zarar mı olduğunu gösterir.
- Sonuçtaki her terimin yanında ne anlama geldiğini anlatan kısa açıklamalar, sonucu paylaşma, eksik ve olağan dışı girişler için uyarılar.
- Türkçe sayı biçimi: `100.000`, `10.046,21` ve `10046,21` doğru okunur.
- Çevrimdışı çalışır, veri toplamaz, hiçbir izin istemez.

## Arkasındaki matematik

**Gerçek faiz (iç verim oranı).** Eline geçen net tutarı taksitlerin bugünkü değerine eşitleyen aylık oran $r$ aranır:

$$0 = \text{Net} - \sum_{t=1}^{n} \frac{A}{(1+r)^t}$$

Denklemin kapalı bir çözümü yoktur. Sağ taraf $r$'ye göre kesin artan olduğu için kök tektir; bu yüzden ikiye bölme (bisection) yöntemi her girdide yakınsar.

**Yıllık maliyet oranı:** $\text{YMO} = (1+r)^{12} - 1$. Bileşik hesaplanır, 12 ile çarpılmaz.

**Enflasyonla bugünkü değer.** Farklı vadeli teklifler, her taksit enflasyonla bugüne indirgenerek karşılaştırılır:

$$\text{Maliyet}_{\text{bugün}} = \sum_{t=1}^{n} \frac{A}{(1+d)^t} - \text{Net}, \qquad d = (1+\pi)^{1/12} - 1$$

**Erken kapama.** Vergili kredide bakiye $r' = r\,(1 + \text{KKDF} + \text{BSMV})$ oranlı bir annüite gibi ilerler. $r'$ taksitten geri hesaplandığı için kullanıcının vergi oranlarını bilmesine gerek yoktur:

$$\text{Bakiye}_k = \sum_{t=1}^{n-k} \frac{A}{(1+r')^t}$$

## Proje yapısı

```
app/src/main/java/com/gercekfaiz/
├── core/LoanCalculator.kt   # Arayüzden bağımsız hesap çekirdeği (saf Kotlin)
├── ui/theme/                # Marka renkleri, açık ve koyu tema
└── MainActivity.kt          # Üç sekmeli Compose arayüzü
app/src/test/java/com/gercekfaiz/core/
└── LoanCalculatorTest.kt    # 26 birim testi
```

Hesap çekirdeği Android'e bağımlı değildir. Testler, sonuçları Excel'in `PMT` ve `RATE` fonksiyonlarıyla karşılaştırır ve sıfır faiz, peşin masraf, vergili ödeme planı, enflasyonla sıralamanın tersine dönmesi gibi durumları kapsar.

## Çalıştırma

1. Projeyi Android Studio ile açın ve Gradle senkronizasyonunun bitmesini bekleyin.
2. Testler: `./gradlew testDebugUnitTest`
3. Uygulama: bir emülatör veya Android 8.0+ cihaz seçip **Run**.

## Geliştirici

**Muhammed Bera Çakallı** · beracakalli@gmail.com

# ÇEVRİMDIŞI KULLANIM — Yapılacaklar (sadece 4 adım)

Bu sürümde uygulama **internetsiz** kamyon rotası bulur (şimdilik **Türkiye**).
Bilgisayarına hiçbir şey kurmana gerek yok — her şeyi GitHub'ın sunucusu yapar.

> Not: ORS / API anahtarı ARTIK GEREKMİYOR. O dert bitti.

---

## ADIM 1 — Bu dosyaları GitHub'daki repona yükle
1. `truck-router.zip` içindeki **tüm dosya ve klasörleri** repona yükle
   (daha önce yaptığın gibi; `.github` klasörü dahil, üzerine yaz).
2. Commit et. (Bu, APK derlemesini de otomatik başlatır.)

## ADIM 2 — Türkiye verisini ürettir (tek tık, bir kez)
1. Repo'da üstte **Actions** sekmesi.
2. Soldan **"1 - Turkiye verisi uret (offline tiles)"** seç.
3. Sağda **Run workflow → Run workflow**.
4. Bekle: **30–90 dakika** sürebilir. Yeşil ✓ olunca bitmiştir.
   (Bittiğinde repo'nun **Releases** bölümünde `valhalla_tiles.tar` görünür.)

## ADIM 3 — APK'yi indir ve kur
1. **Actions** → **Build APK** → en son yeşil çalışmaya tıkla.
2. En altta **Artifacts → app-debug-apk** indir.
3. Zip'ten çıkan `app-debug.apk`'yi telefona kur (eskinin üstüne).

## ADIM 4 — Uygulamada veriyi indir (tek seferlik, ~1 GB)
1. Uygulamayı aç → haritada iki nokta seç → **ROTA BUL**.
2. "Çevrimdışı veri gerekli" sorusu çıkar → **İndir** de.
   - **Wi-Fi** kullan, indirme bitene kadar **uygulama açık kalsın**.
3. Bitince rota otomatik hesaplanır. Üst satırda **"· TR verisi ✓"** görürsün.
4. Artık **uçak modunda bile** rota bulur. 🎉

---

## Sık sorulanlar

**Sıra önemli mi?** Evet: ADIM 2 bitmeden uygulamadaki indirme çalışmaz
(çünkü indirilecek dosya henüz üretilmemiş olur).

**Repo gizli (private) olabilir mi?** Hayır — uygulamanın veriyi indirebilmesi
için repo **public** kalmalı. (İçinde artık gizli anahtar yok, sorun değil.)

**İndirme "404 döndürdü" diyor.** ADIM 2'deki workflow bitti mi ve yeşil mi?
Releases bölümünde `tiles-turkiye` altında `valhalla_tiles.tar` var mı?

**Rota "No suitable edges" diyor.** Seçtiğin nokta Türkiye dışında ya da
yoldan çok uzakta. Türkiye içinde, bir yola yakın noktalara dokun.

**Başka ülke eklemek istersem?** `build-tiles.yml` içindeki Geofabrik adresini
değiştir (örn. `europe/germany-latest.osm.pbf`), yeni bir tag'e yükle ve
`Config.kt` içindeki `TILES_URL`'i güncelle. (Bunu istediğinde birlikte yaparız.)

**Kamyon ölçüleri offline'da da geçerli mi?** Evet — yükseklik/genişlik/uzunluk/
ağırlık/dingil/tehlikeli madde her rotada cihazdaki motora verilir.

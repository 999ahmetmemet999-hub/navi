# Derlenmiş APK nasıl alınır

Bu projeyi Claude derleyemez (ortamda Android SDK ve Google/Maven erişimi yok).
Aşağıdaki iki yoldan biriyle **derlenmiş `app-debug.apk`** elde edebilirsin.

## Yol A — GitHub Actions (yerelde hiçbir şey kurmadan) ✅ önerilen

1. github.com'da yeni bir **repo** oluştur (private olabilir).
2. Bu klasörün tüm içeriğini repoya yükle
   (`.github/` klasörü dahil — sürükle-bırak ya da `git push`).
3. (İsteğe bağlı, önerilir) Anahtarını commit etmemek için:
   repo'da **Settings → Secrets and variables → Actions → New repository secret**,
   isim: `ORS_API_KEY`, değer: ORS anahtarın. (Eklemezsen APK yine derlenir ama
   anahtarı sonradan girene kadar rota çalışmaz.)
4. **Actions** sekmesine git. "Build APK" akışı push'ta otomatik başlar; ya da
   "Run workflow" ile elle çalıştır.
5. Yeşil tik gelince ilgili çalışmayı aç → **Artifacts → `app-debug-apk`** indir.
   Zip'in içinde `app-debug.apk` var.
6. APK'yi telefona at, "bilinmeyen kaynaklardan yükleme"ye izin ver, kur.

> Not: GitHub'a hesapla giriş gerekir; derleme GitHub'ın sunucusunda (internet + SDK
> orada hazır) yapılır. Ücretsiz dakikalar bu iş için fazlasıyla yeter.

## Yol B — Yerelde Android Studio (tek seferlik kurulum)

1. Android Studio'yu kur (güncel sürüm).
2. `File > Open` → bu klasör. Gradle sync biter.
3. `Config.kt` içine ORS anahtarını yaz.
4. `Build > Build APK(s)` → `app/build/outputs/apk/debug/app-debug.apk`.

## APK imzalama (yayınlamak istersen)
`assembleDebug` test/yan-yükleme için yeterli (debug anahtarıyla imzalı). Play Store
ya da geniş dağıtım için release imzası gerekir: `keytool` ile keystore üret,
`app/build.gradle.kts` içine `signingConfigs` ekle, `assembleRelease` çalıştır.

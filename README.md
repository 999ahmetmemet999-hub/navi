# Kamyon Rota (TruckRouter)

Kamyon sürücüleri için, **yalnızca kamyona uygun yollardan** rota çıkaran bir Android
uygulaması. Rota hesaplama OpenStreetMap verisi üzerinde kamyon (HGV) profiliyle yapılır;
böylece düşük köprüler, dar yollar, tonaj sınırları ve kamyon yasakları dikkate alınır.

> Dünyayı tek tek haritalaman gerekmiyor: OpenStreetMap zaten tüm dünyayı kapsıyor ve
> `maxheight`, `maxweight`, `maxwidth`, `maxlength`, `hgv=no`, `hazmat` gibi etiketleri
> içeriyor. Bu uygulama o veriyi kullanan hazır bir rota motorunu çağırır.

---

## Şu an ne çalışıyor?

| Özellik | Durum |
|---|---|
| **Online kamyon rotası** (boyut/ağırlık/tehlikeli madde dahil) | ✅ Çalışıyor — Avrupa ve aslında tüm dünya |
| Haritada başlangıç/varış seçme, konumdan başlama | ✅ |
| Yükseklik / genişlik / uzunluk / ağırlık / dingil yükü / hazmat girişi | ✅ |
| Adım adım yol tarifi, mesafe + süre | ✅ |
| **Offline harita** (kayıtlı OSM tile arşivi) | ✅ (kullanıcı arşiv ekler) |
| Son rotayı kaydedip **çevrimdışıyken gösterme** | ✅ |
| **Offline rota *hesaplama*** (cihazda) | ⏳ Faz 2 — aşağıya bakın |

Online tarafı **OpenRouteService**'in `driving-hgv` profilini kullanır: ücretsiz, küresel,
tam kamyon kısıtları. Harita **osmdroid** ile çizilir: ücretsiz, API anahtarı gerekmez.

---

## Kurulum (3 adım)

### 1) Ücretsiz OpenRouteService anahtarı al
- https://openrouteservice.org/dev/#/signup adresinden kayıt ol.
- Ücretsiz plan: günde ~2.000 rota isteği, `driving-hgv` (kamyon) profili dahil.

### 2) Anahtarı yapıştır
`app/src/main/java/com/example/truckrouter/Config.kt` içindeki
`ORS_API_KEY = "PUT_YOUR_ORS_KEY_HERE"` satırını kendi anahtarınla değiştir.

### 3) Derle ve APK üret
**Android Studio ile (önerilen):**
1. Android Studio'da `File > Open` → bu klasörü (`truck-router`) seç.
2. İlk açılışta Gradle senkronizasyonu çalışır (wrapper dosyalarını otomatik üretir).
3. `Build > Build App Bundle(s) / APK(s) > Build APK(s)`.
4. APK: `app/build/outputs/apk/debug/app-debug.apk`.

**Komut satırı ile** (Android Studio bir kez açıp wrapper'ı ürettikten sonra):
```bash
./gradlew assembleDebug
```

> Not: Bu paket Gradle wrapper'ın ikili `.jar`'ını içermez (metin değil). Android Studio
> ilk sync'te onu üretir; ya da `gradle wrapper --gradle-version 8.9` çalıştırabilirsin.

---

## Anahtarı sürüm kontrolünden uzak tutmak (opsiyonel ama önerilir)

Anahtarı commit etmemek için `Config.kt` yerine `local.properties`'ten okuyabilirsin:

`local.properties`:
```
ORS_API_KEY=senin_anahtarin
```
`app/build.gradle.kts` → `defaultConfig` içine:
```kotlin
val orsKey = (project.rootProject.file("local.properties").let {
    java.util.Properties().apply { if (it.exists()) load(it.inputStream()) }
}).getProperty("ORS_API_KEY") ?: "PUT_YOUR_ORS_KEY_HERE"
buildConfigField("String", "ORS_API_KEY", "\"$orsKey\"")
```
`android { buildFeatures { buildConfig = true } }` ekle, sonra kodda
`BuildConfig.ORS_API_KEY` kullan. (`.gitignore` zaten `local.properties`'i hariç tutuyor.)

---

## Offline nasıl çalışıyor (ve neden tam offline rota Faz 2)

**Bugün offline iken:**
- **Harita**: osmdroid çevrimdışı tile arşivlerini (`.mbtiles` / `.sqlite` / osmdroid zip)
  okuyabilir. Avrupa için bunlar ülke ülke indirilir (tek bir dev blob değil). Arşivi
  cihazdaki osmdroid klasörüne koyduğunda harita internet olmadan da çizilir.
- **Rota**: en son hesaplanan rota kaydedilir ve internet yokken ekranda gösterilir
  (yol sırasında bağlantı koparsa rotan kaybolmaz).

**Neden cihazda offline rota *hesaplama* tek seansta gelmiyor:**
- GraphHopper'ın kamyon boyut filtreleri (custom model) çalışma anında **Janino** ile
  Java derler. Android (DEX/ART) çalışma anında JVM bytecode yükleyemediği için bu
  Android'de düzgün çalışmaz.
- GraphHopper ekibinin kendisi Android offline desteğinin "idare eder" durumda olduğunu
  ve bırakılabileceğini belirtiyor (issue #1940).
- Ayrıca tüm Avrupa'nın rota grafiği **gigabaytlar** tutar ve bir sunucuda üretilmesi gerekir.

**Tam offline için doğru yol (Faz 2):**
- Motor: **Valhalla** (C++/JNI) — kamyon costing'i cihazda, çalışma anında derleme
  gerektirmeden çalışır; tile tabanlı olduğu için ülke ülke veri indirmeye uygundur.
- Mimari hazır: `RouteRepository` zaten online/offline ayrımını yapıyor; offline motor
  bağlanınca `Outcome.OfflineCached` yerine gerçek offline hesaplama döndürülür.

**Orta yol (çoğu kullanıcı için en pratik):** Kendi OpenRouteService sunucunu kur
(`scripts/docker-compose.yml`). Ücretsiz limit kalkar, kendi Avrupa örneğin olur ve
uygulama onu çağırır. Tam "internetsiz" değildir ama maliyetsiz ve limitsizdir.

---

## Kendi ORS sunucunu çalıştırma (ücretsiz limiti kaldırır)

`scripts/docker-compose.yml` hazır. Özet:
```bash
cd scripts
mkdir -p data
curl -L -o data/region.osm.pbf https://download.geofabrik.de/europe/germany-latest.osm.pbf
docker compose up -d
docker compose logs -f          # ilk açılışta grafik üretimi
# Hazır olunca: http://localhost:8080/ors/v2/health
```
Sonra `OrsClient.URL` değerini `http://SUNUCU_ADRESIN:8080/ors/v2/directions/driving-hgv/geojson`
olarak değiştir. (Avrupa geneli pbf çok daha büyük ve yavaştır; önce bir ülkeyle dene.)

---

## Proje yapısı

```
truck-router/
├── app/src/main/java/com/example/truckrouter/
│   ├── MainActivity.kt        Harita, dokunma, konum, diyaloglar, çizim
│   ├── Config.kt              ORS anahtarı + varsayılan harita merkezi
│   ├── Models.kt              LatLon, RouteResult, TruckSpec, Outcome
│   ├── OrsClient.kt           OpenRouteService driving-hgv çağrısı + GeoJSON parse
│   ├── RouteRepository.kt     Online/offline karar mantığı
│   ├── RouteStore.kt          Son rotayı + kamyon ölçülerini saklama
│   └── Connectivity.kt        Doğrulanmış internet kontrolü
├── app/src/main/res/...       Düzenler, stringler (Türkçe), ikon
├── scripts/
│   ├── docker-compose.yml     Kendi ORS sunucun (kamyon profili)
│   └── truck-custom-model.json GraphHopper kamyon profili referansı
└── README.md
```

---

## Bilinen kısıtlar / notlar
- **İlk derlemede** osmdroid/okhttp gibi bağımlılıkların yama sürümlerini Maven Central'a
  göre küçük ayarlamak gerekebilir (Android'de normaldir). Sürümler `app/build.gradle.kts`'te.
- `android.preference.PreferenceManager` kullanımdan kalkmış ama osmdroid yapılandırması
  için kullanılıyor; sorun değil.
- Ücretsiz ORS planında çok uzun rotalar ve günlük istek sayısı sınırlıdır
  (rota başına 6.000 km'ye kadar). Yoğun kullanım için kendi sunucunu kur.
- `minSdk = 26` (Android 8.0). Bu sayede ikon tamamen XML adaptive ikon olarak gelir.

## Lisans
Kendi projen olarak kullan. Bağımlılıklar kendi lisanslarına tabidir
(osmdroid: Apache-2.0, OpenRouteService verisi: OSM/ODbL — atıf gerekir).

# Valhalla Offline — Adım 1: Tile (rota verisi) üretimi

Cihazda offline kamyon rotası için **önce Valhalla tile'larını** üretmen gerekir.
Telefon bu tile'ları OSM'den ÜRETMEZ — sen bir PC'de üretir, telefona kopyalarsın.
Bu adım olmadan offline motor çalışmaz.

## Gereken
- **Docker kurulu bir bilgisayar.** (Windows 10'da: Docker Desktop + WSL2.)
- Bir ülke için **~10–20 GB boş disk** (tek ülke ile başla; tüm Avrupa çok daha ağır).
- İnternet (sadece tile üretirken; sonuç offline kullanılır).

## En kolay yol: gis-ops/docker-valhalla (turnkey imaj)
Bu imaj PBF'i kendisi indirir, tile'ları kurar ve test için HTTP sunucusu olarak da çalışır.

```bash
# 1) Çalışma klasörü
mkdir valhalla-build && cd valhalla-build && mkdir custom_files

# 2) Bir ülke seç (Geofabrik). Örnek: Türkiye.
#    Avrupa ülkeleri: https://download.geofabrik.de/europe.html
#    Konteyneri başlat — ilk açılışta PBF'i indirir ve tile üretir (uzun sürebilir).
docker run -dt --name valhalla -p 8002:8002 \
  -v "$PWD/custom_files:/custom_files" \
  -e tile_urls=https://download.geofabrik.de/europe/turkey-latest.osm.pbf \
  -e server_threads=4 \
  ghcr.io/gis-ops/docker-valhalla/docker-valhalla:latest

# 3) İlerlemeyi izle (graph üretimi bitene kadar)
docker logs -f valhalla
```

Bitince `custom_files/` içinde şunlar oluşur:
- `valhalla_tiles.tar`  ← **telefona gidecek olan budur** (tüm rota grafiği tek dosya)
- `valhalla.json`       ← motorun konfigürasyonu (tile yolunu söyler)

> Not: Tile'lar kamyona **özgü değildir** — bir kez üretilir, kamyon ölçüleri
> (yükseklik/ağırlık/...) her **istekte** verilir. Yani aynı tile ile farklı kamyon
> ölçüleri çalışır.

## Tile sağlam mı? (PC'de 1 dakikada test)
Aynı konteyner 8002'de HTTP sunuyor. Kamyon rotası iste:

```bash
curl http://localhost:8002/route --data '{
  "locations":[
    {"lat":41.015,"lon":28.979},
    {"lat":39.925,"lon":32.866}
  ],
  "costing":"truck",
  "costing_options":{"truck":{
    "height":4.0,"width":2.55,"length":16.5,
    "weight":40,"axle_load":11.5,"hazmat":false
  }}
}'
```
- JSON + `trip`/`legs`/`shape` dönerse → **tile'lar çalışıyor.** Bir sonraki adıma geç.
- Hata dönerse → noktalar ülke sınırları içinde mi, tile üretimi bitti mi kontrol et.

## Bu noktada iki yolun var

### A) Hızlı ara çözüm: "kendi Valhalla sunucun" (gerçek offline değil)
Yukarıdaki konteyner zaten bir kamyon-rota sunucusu. Telefon bu sunucuya (ev/VPS IP'si)
bağlanırsa, ORS bulut anahtarı/kotası derdi olmadan kamyon rotası alırsın. Tam internetsiz
değildir ama anahtar sorununu bitirir ve uygulamadaki `OrsClient.URL`'i değiştirmek yeterli.
Uygulama tarafını bunun için ben hazırlayabilirim (kolay).

### B) Gerçek cihaz-içi offline (asıl hedef)
`valhalla_tiles.tar`'ı telefona kopyalar, cihazdaki **native Valhalla** motoruna okutursun.
Bunun için native kütüphane gerekir:
- **CARTO Mobile SDK** — `ValhallaOfflineRoutingService(dbPath)` ile hazır bir AAR sunar
  (ücretsiz lisans anahtarı ister, biraz eski ama çalışır). Derleme gerektirmez.
- **valhalla-mobile** (Rallista) — Valhalla'nın `route()` fonksiyonunu mobilde açan
  topluluk sarmalayıcısı; tile setini okur.
- **Kaynaktan NDK derlemesi** — tam kontrol, en zor yol.

Tile elinde olunca, seçtiğin yola göre uygulamadaki `OfflineRouter`'a bağlama kodunu
ben yazarım. **Ama native kütüphaneyi bu sohbette derleyemem** — o, senin PC'nde
(ya da ayrı bir CI'da) yapılması gereken bir adım.

## Özet sıra
1. Docker'da tek ülke için tile üret (bu dosya).
2. PC'de curl ile kamyon rotasını doğrula.
3. Ara çözüm istersen (A): uygulamayı kendi sunucuna yönlendir — kod bende.
4. Tam offline (B): native motor + tile'ı telefona koy — entegrasyon kodu bende,
   native derleme sende/CI'da.

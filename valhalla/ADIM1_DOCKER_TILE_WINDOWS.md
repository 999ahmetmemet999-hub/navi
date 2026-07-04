# YOL B — DURAK 1: Docker kur + Tile üret (Windows 10, çok ayrıntılı)

Bu dosya, offline rota için gereken **tile (harita/rota verisi)**'ni kendi bilgisayarında
üretmeni sağlar. Telefon bu veriyi üretemez; sen bir kez üretirsin, sonra uygulama kullanır.
Bu durak bitmeden diğer adımlar çalışmaz. Acele etme, sırayla git.

## Bütün yolculuğun haritası (nerede olduğunu bil)
- **Durak 1 (BU DOSYA):** Docker kur, bir ülke için tile üret, doğrula.  ← şu an buradasın
- **Durak 2:** tiles.tar dosyasını GitHub'a (Release olarak) yükle.
- **Durak 3:** Ben `valhalla-mobile` motorunu + tile indiren kodu uygulamaya eklerim.
- **Durak 4:** GitHub offline APK'yi derler.
- **Durak 5:** APK'yi kur → ilk açılışta tile iner → internetsiz rota bulur.

---

## Gerekenler
- **Windows 10 64-bit** (sürüm 2004 veya üstü tercihen).
- **~20 GB boş disk** (tek ülke için). Tüm Avrupa'yı SEÇME, çok büyük.
- **İnternet** (sadece tile üretirken gerekir; sonuç offline kullanılır).

---

## KISIM 1 — Docker Desktop'ı kur

**1.1** Tarayıcıda aç: `https://www.docker.com/products/docker-desktop/`
→ **Download for Windows** ile kurulum dosyasını indir.

**1.2** İnen dosyayı çalıştır. Kurulumda **"Use WSL 2 instead of Hyper-V"** seçeneği çıkarsa
işaretli bırak. Kurulumu tamamla.

**1.3** Bilgisayarı **yeniden başlat** (kurulum isteyebilir).

**1.4** **Docker Desktop**'ı aç (masaüstü/başlat menüsünden). İlk açılışta sözleşmeyi kabul et.
Sağ altta (saat yanında) bir **balina 🐳 simgesi** çıkar. Simge sabit/duruyorsa Docker hazırdır.
(İlk açılış birkaç dakika sürebilir.)

**1.5 — Docker çalışıyor mu? Test et.**
- Başlat menüsüne `PowerShell` yaz → **Windows PowerShell**'i aç.
- Şunu yapıştır, Enter:
  ```
  docker run hello-world
  ```
- "Hello from Docker!" yazısı görürsen → **Docker çalışıyor.** ✅

> ⚠️ Hata alırsan (en sık iki sebep):
> - **"virtualization" / "WSL 2" hatası:** Bilgisayarın BIOS'unda **sanallaştırma (Virtualization / VT-x / SVM)** kapalı olabilir. Açman gerekir (üretici + model + "enable virtualization BIOS" diye ara).
> - **"WSL 2 installation is incomplete":** PowerShell'i **yönetici olarak** aç, şunu çalıştır: `wsl --update` — sonra Docker Desktop'ı yeniden başlat.

---

## KISIM 2 — Bir ülke için tile üret

**2.1** PowerShell'de bir çalışma klasörü oluştur (kopyala-yapıştır, her satır Enter):
```
cd $HOME
mkdir valhalla-build
cd valhalla-build
mkdir custom_files
```

**2.2** Tile üretimini başlat. Aşağıdaki komut Türkiye içindir; başka ülke istersen
`turkey-latest` yerine Geofabrik'teki dosya adını yaz (ülke listesi:
https://download.geofabrik.de/europe.html).

Komutun **tamamını tek satır** olarak yapıştır, Enter:
```
docker run -dt --name valhalla -p 8002:8002 -v "${PWD}/custom_files:/custom_files" -e tile_urls="https://download.geofabrik.de/europe/turkey-latest.osm.pbf" -e server_threads=4 ghcr.io/gis-ops/docker-valhalla/docker-valhalla:latest
```
Bu komut: imajı indirir, ülke haritasını (PBF) indirir, tile'ları üretir ve bir sunucu
başlatır. **İlk çalıştırma uzun sürer** (imaj + harita indirme + tile üretimi; makineye göre
10–60 dakika). Sabırlı ol.

**2.3 — İlerlemeyi izle:**
```
docker logs -f valhalla
```
Ekranda satırlar akar. **Bittiğinde** genelde şuna benzer görürsün:
`... finished ...` / `Running server ...` / tile sayıları.
İzlemeyi durdurmak için `Ctrl + C` (bu sadece izlemeyi durdurur, sunucuyu kapatmaz).

---

## KISIM 3 — Tile gerçekten çalışıyor mu? (kamyon rotası testi)

PowerShell'de şunu yapıştır (Türkiye içi iki nokta arasında **kamyon** rotası ister):
```
Invoke-RestMethod -Uri "http://localhost:8002/route" -Method Post -Body '{"locations":[{"lat":41.015,"lon":28.979},{"lat":39.925,"lon":32.866}],"costing":"truck"}'
```
- Ekrana **`trip`, `legs`, `summary`, `length`** gibi alanlar içeren bir cevap gelirse
  → **TILE ÇALIŞIYOR.** 🎉 Durak 1 bitti.
- Hata gelirse:
  - Noktalar ülke sınırları içinde mi? (Türkiye dışı nokta verirsen bulamaz.)
  - Tile üretimi bitti mi? (`docker logs valhalla` ile kontrol et.)
  - Sunucu ayakta mı? `docker ps` → listede `valhalla` görünmeli.

---

## KISIM 4 — Üretilen dosyaları bul

Dosya gezgininde şu klasöre git:
`C:\Users\<KULLANICI_ADIN>\valhalla-build\custom_files`

Orada şunları göreceksin (Durak 2'de bunları yükleyeceğiz):
- **`valhalla_tiles.tar`**  ← asıl veri dosyası (telefona bu gidecek)
- **`valhalla.json`**       ← ayar dosyası

---

## Bittiğinde bana ne yaz?
Sadece şunu yaz: **"tile hazır, kamyon rotası döndü"** ve `custom_files` içinde
`valhalla_tiles.tar` dosyasının **boyutunu** (örn. 350 MB) söyle.

O an **Durak 2 + 3**'e geçeriz: dosyayı GitHub'a yüklemeni anlatırım, ben de
`valhalla-mobile` motorunu ve tile indiren kodu uygulamaya eklerim.

## Faydalı komutlar (lazım olursa)
- Sunucuyu durdur: `docker stop valhalla`
- Tekrar başlat: `docker start valhalla`
- Tamamen sil (baştan üretmek için): `docker rm -f valhalla`
- Çalışanları gör: `docker ps`

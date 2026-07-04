package com.example.truckrouter

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Rota karar mantığı — ÇEVRİMDIŞI ÖNCELİKLİ:
 *
 *  1) Cihazda Türkiye verisi (tiles) varsa -> Valhalla ile CİHAZDA hesapla.
 *     İnternet gerekmez, anahtar gerekmez.
 *  2) Tiles yoksa ve internet + geçerli ORS anahtarı varsa -> ORS bulutu (yedek).
 *  3) Hiçbiri yoksa -> kayıtlı son rota ya da açıklayıcı hata.
 */
class RouteRepository(
    private val context: Context,
    private val store: RouteStore,
    private val offline: OfflineRouter
) {
    suspend fun route(
        apiKey: String,
        start: LatLon,
        end: LatLon,
        spec: TruckSpec
    ): Outcome = withContext(Dispatchers.IO) {
        var firstError: Exception? = null

        // 1) Cihaz içi çevrimdışı motor
        if (offline.isReady()) {
            try {
                val result = offline.route(start, end, spec)
                store.saveLast(result)
                return@withContext Outcome.OfflineRouted(result, "çevrimdışı motor · Türkiye")
            } catch (e: Exception) {
                firstError = e
            }
        }

        // 2) Yedek: ORS bulutu (yalnızca anahtar gerçekten ayarlıysa)
        val keyUsable = apiKey.isNotBlank() && apiKey != "PUT_YOUR_ORS_KEY_HERE"
        if (Connectivity.isOnline(context) && keyUsable) {
            try {
                val result = OrsClient.route(apiKey, start, end, spec)
                store.saveLast(result)
                return@withContext Outcome.Online(result)
            } catch (e: Exception) {
                if (firstError == null) firstError = e
            }
        }

        // 3) Kayıtlı rota ya da net hata mesajı
        val err = firstError
        if (err == null) {
            val cached = store.loadLast()
            if (cached != null) {
                return@withContext Outcome.OfflineCached(
                    cached, "Kayıtlı son rota gösteriliyor"
                )
            }
        }

        val msg = when {
            err != null ->
                "Rota bulunamadı: ${err.message}\n\n" +
                    "İpucu: Noktalar Türkiye sınırları içinde ve bir yola yakın olmalı."
            !offline.isReady() ->
                "Türkiye çevrimdışı verisi henüz indirilmemiş."
            else -> "Bilinmeyen hata"
        }
        Outcome.Error(msg)
    }
}

package com.example.truckrouter

/**
 * App configuration.
 *
 * 1) Get a FREE OpenRouteService API key:  https://openrouteservice.org/dev/#/signup
 *    (Free plan: ~2,000 routing requests/day. The "driving-hgv" truck profile is included.)
 * 2) Paste it below, replacing PUT_YOUR_ORS_KEY_HERE.
 *
 * For production, prefer reading this from local.properties via BuildConfig instead of
 * committing the key. See README.md ("Keeping the key out of version control").
 */
object Config {
    // ORS artık OPSİYONEL (yalnızca çevrimdışı veri yokken yedek olarak kullanılır).
    const val ORS_API_KEY: String = "PUT_YOUR_ORS_KEY_HERE"

    /**
     * Çevrimdışı Türkiye rota verisi (valhalla_tiles.tar).
     * GitHub'daki "1 - Turkiye verisi uret" workflow'u bu dosyayı repo'nun
     * Releases bölümüne koyar; uygulama ilk rotada buradan indirir.
     * Farklı bir repo veya ülke kullanırsan bu adresi güncelle.
     */
    const val TILES_URL =
        "https://github.com/999ahmetmemet999-hub/navi/releases/download/tiles-turkiye/valhalla_tiles.tar"

    // Varsayılan harita görünümü: Türkiye.
    const val DEFAULT_LAT = 39.0
    const val DEFAULT_LON = 35.24
    const val DEFAULT_ZOOM = 5.8
}

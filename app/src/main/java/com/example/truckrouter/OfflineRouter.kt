package com.example.truckrouter

import android.content.Context
import com.valhalla.api.models.CostingModel
import com.valhalla.api.models.CostingOptions
import com.valhalla.api.models.DistanceUnit
import com.valhalla.api.models.RouteRequest
import com.valhalla.api.models.RoutingWaypoint
import com.valhalla.api.models.TruckCostingOptions
import com.valhalla.config.ValhallaConfigBuilder
import com.valhalla.valhalla.Valhalla
import com.valhalla.valhalla.ValhallaResponse
import java.io.File

/**
 * CİHAZ İÇİ çevrimdışı kamyon rotası — Valhalla motoru.
 *
 * Veri: filesDir/valhalla_tiles.tar (Türkiye). TileManager indirir; bu sınıf okur.
 * Kamyon ölçüleri (yükseklik/genişlik/uzunluk/ağırlık/dingil/hazmat) her istekte
 * costing_options.truck olarak verilir — tile'lar araca özgü değildir.
 */
class OfflineRouter(private val context: Context) {

    /** Tile dosyası inmiş ve mantıklı bir boyutta mı? */
    fun isReady(): Boolean =
        tilesFile(context).let { it.exists() && it.length() > 10L * 1024 * 1024 }

    /** Blocking — IO dispatcher üzerinde çağır. Hata durumunda exception fırlatır. */
    fun route(start: LatLon, end: LatLon, spec: TruckSpec): RouteResult {
        val engine = obtainEngine(context)

        val request = RouteRequest(
            locations = listOf(
                RoutingWaypoint(lat = start.lat, lon = start.lon),
                RoutingWaypoint(lat = end.lat, lon = end.lon)
            ),
            costing = CostingModel.truck,
            costingOptions = CostingOptions(
                truck = TruckCostingOptions(
                    height = spec.heightM,
                    width = spec.widthM,
                    length = spec.lengthM,
                    weight = spec.weightT,
                    axleLoad = spec.axleLoadT,
                    hazmat = spec.hazmat
                )
            ),
            units = DistanceUnit.km,
            format = RouteRequest.Format.json
        )

        val response = engine.route(request)
        val trip = (response as? ValhallaResponse.Json)?.jsonResponse?.trip
            ?: throw IllegalStateException("Beklenmeyen yanıt biçimi")

        val points = ArrayList<LatLon>()
        val steps = ArrayList<RouteStep>()
        for (leg in trip.legs) {
            leg.shape?.let { points.addAll(decodePolyline6(it)) }
            leg.maneuvers?.forEach { m ->
                steps.add(
                    RouteStep(
                        instruction = m.instruction ?: "",
                        distanceM = (m.length ?: 0.0) * 1000.0,
                        durationS = m.time ?: 0.0
                    )
                )
            }
        }
        if (points.isEmpty()) throw IllegalStateException("Rota geometrisi boş döndü")

        return RouteResult(
            points = points,
            distanceM = (trip.summary.length ?: 0.0) * 1000.0,
            durationS = trip.summary.time ?: 0.0,
            steps = steps
        )
    }

    companion object {
        @Volatile
        private var engine: Valhalla? = null

        fun tilesFile(context: Context): File = File(context.filesDir, "valhalla_tiles.tar")

        /** Yeni tile indirildiğinde çağır — motor bir sonraki istekte tazeden açılır. */
        fun reset() {
            engine = null
        }

        private fun obtainEngine(context: Context): Valhalla {
            engine?.let { return it }
            synchronized(this) {
                engine?.let { return it }
                val config = ValhallaConfigBuilder()
                    .withTileExtract(tilesFile(context).absolutePath)
                    .build()
                val built = Valhalla(context.applicationContext, config)
                engine = built
                return built
            }
        }
    }

    /** Valhalla shape = 6 ondalık hassasiyetli encoded polyline. */
    private fun decodePolyline6(encoded: String): List<LatLon> {
        val out = ArrayList<LatLon>(encoded.length / 4)
        var index = 0
        var lat = 0
        var lon = 0
        while (index < encoded.length) {
            var result = 0
            var shift = 0
            var b: Int
            do {
                b = encoded[index++].code - 63
                result = result or ((b and 0x1f) shl shift)
                shift += 5
            } while (b >= 0x20)
            lat += if (result and 1 != 0) (result shr 1).inv() else result shr 1

            result = 0
            shift = 0
            do {
                b = encoded[index++].code - 63
                result = result or ((b and 0x1f) shl shift)
                shift += 5
            } while (b >= 0x20)
            lon += if (result and 1 != 0) (result shr 1).inv() else result shr 1

            out.add(LatLon(lat / 1e6, lon / 1e6))
        }
        return out
    }
}

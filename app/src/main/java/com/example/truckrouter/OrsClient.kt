package com.example.truckrouter

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * OpenRouteService Directions API, profile = driving-hgv (heavy goods vehicle).
 * Docs: https://openrouteservice.org/dev/#/api-docs/v2/directions
 *
 * The /geojson endpoint returns already-decoded coordinates, so no polyline
 * decoding is needed. Note ORS expects coordinates as [lon, lat].
 */
object OrsClient {

    private const val URL =
        "https://api.openrouteservice.org/v2/directions/driving-hgv/geojson"

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(40, TimeUnit.SECONDS)
        .build()

    /** Blocking call — invoke from a background dispatcher. */
    @Throws(IOException::class)
    fun route(apiKey: String, start: LatLon, end: LatLon, spec: TruckSpec): RouteResult {
        val restrictions = JSONObject().apply {
            if (spec.heightM > 0) put("height", spec.heightM)
            if (spec.widthM > 0) put("width", spec.widthM)
            if (spec.lengthM > 0) put("length", spec.lengthM)
            if (spec.weightT > 0) put("weight", spec.weightT)
            if (spec.axleLoadT > 0) put("axleload", spec.axleLoadT)
            put("hazmat", spec.hazmat)
        }

        val body = JSONObject().apply {
            put("coordinates", JSONArray().apply {
                put(JSONArray().put(start.lon).put(start.lat))
                put(JSONArray().put(end.lon).put(end.lat))
            })
            put("instructions", true)
            put("options", JSONObject().apply {
                put("vehicle_type", "hgv")
                put("profile_params", JSONObject().apply {
                    put("restrictions", restrictions)
                })
            })
        }

        val request = Request.Builder()
            .url(URL)
            .addHeader("Authorization", apiKey)
            .addHeader("Accept", "application/geo+json")
            .post(body.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        client.newCall(request).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                val hint = when (resp.code) {
                    401, 403 -> "Anahtar geçersiz/yetkisiz. Config.kt'deki ORS anahtarını kontrol et."
                    404 -> "Bu noktalar arasında kamyon rotası bulunamadı. Farklı noktalar dene."
                    413, 414 -> "İstek çok büyük. Noktaları birbirine yakın seç."
                    429 -> "İstek limiti doldu (günlük/dakikalık). Biraz sonra tekrar dene."
                    else -> ""
                }
                throw IOException("ORS ${resp.code}: ${text.take(200)} $hint".trim())
            }
            return parse(text)
        }
    }

    private fun parse(text: String): RouteResult {
        val root = JSONObject(text)
        val features = root.optJSONArray("features")
            ?: throw IOException("Beklenmeyen yanıt (features yok)")
        if (features.length() == 0) throw IOException("Rota bulunamadı")

        val feature = features.getJSONObject(0)
        val coords = feature.getJSONObject("geometry").getJSONArray("coordinates")
        val points = ArrayList<LatLon>(coords.length())
        for (i in 0 until coords.length()) {
            val c = coords.getJSONArray(i)
            // GeoJSON order is [lon, lat]
            points.add(LatLon(c.getDouble(1), c.getDouble(0)))
        }

        val props = feature.getJSONObject("properties")
        val summary = props.optJSONObject("summary") ?: JSONObject()
        val distance = summary.optDouble("distance", 0.0)   // meters
        val duration = summary.optDouble("duration", 0.0)   // seconds

        val steps = ArrayList<RouteStep>()
        val segments = props.optJSONArray("segments")
        if (segments != null) {
            for (s in 0 until segments.length()) {
                val stepArr = segments.getJSONObject(s).optJSONArray("steps") ?: continue
                for (j in 0 until stepArr.length()) {
                    val step = stepArr.getJSONObject(j)
                    steps.add(
                        RouteStep(
                            instruction = step.optString("instruction", ""),
                            distanceM = step.optDouble("distance", 0.0),
                            durationS = step.optDouble("duration", 0.0)
                        )
                    )
                }
            }
        }

        return RouteResult(points, distance, duration, steps)
    }
}

package com.example.truckrouter

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Lightweight persistence:
 *  - last computed route -> filesDir/last_route.json  (shown when offline)
 *  - truck spec          -> SharedPreferences
 */
class RouteStore(private val context: Context) {

    private val routeFile: File
        get() = File(context.filesDir, "last_route.json")

    private val prefs by lazy {
        context.getSharedPreferences("truck_router", Context.MODE_PRIVATE)
    }

    // ---- route ----

    fun saveLast(route: RouteResult) {
        val pts = JSONArray()
        for (p in route.points) {
            pts.put(JSONArray().put(p.lat).put(p.lon))
        }
        val steps = JSONArray()
        for (s in route.steps) {
            steps.put(
                JSONObject()
                    .put("i", s.instruction)
                    .put("d", s.distanceM)
                    .put("t", s.durationS)
            )
        }
        val root = JSONObject()
            .put("points", pts)
            .put("distance", route.distanceM)
            .put("duration", route.durationS)
            .put("steps", steps)
            .put("savedAt", System.currentTimeMillis())
        runCatching { routeFile.writeText(root.toString()) }
    }

    fun loadLast(): RouteResult? {
        if (!routeFile.exists()) return null
        return runCatching {
            val root = JSONObject(routeFile.readText())
            val ptsArr = root.getJSONArray("points")
            val points = ArrayList<LatLon>(ptsArr.length())
            for (i in 0 until ptsArr.length()) {
                val c = ptsArr.getJSONArray(i)
                points.add(LatLon(c.getDouble(0), c.getDouble(1)))
            }
            val stepsArr = root.optJSONArray("steps") ?: JSONArray()
            val steps = ArrayList<RouteStep>(stepsArr.length())
            for (i in 0 until stepsArr.length()) {
                val s = stepsArr.getJSONObject(i)
                steps.add(
                    RouteStep(
                        s.optString("i", ""),
                        s.optDouble("d", 0.0),
                        s.optDouble("t", 0.0)
                    )
                )
            }
            RouteResult(
                points = points,
                distanceM = root.optDouble("distance", 0.0),
                durationS = root.optDouble("duration", 0.0),
                steps = steps
            )
        }.getOrNull()
    }

    // ---- truck spec ----

    fun saveSpec(spec: TruckSpec) {
        prefs.edit()
            .putFloat("height", spec.heightM.toFloat())
            .putFloat("width", spec.widthM.toFloat())
            .putFloat("length", spec.lengthM.toFloat())
            .putFloat("weight", spec.weightT.toFloat())
            .putFloat("axle", spec.axleLoadT.toFloat())
            .putBoolean("hazmat", spec.hazmat)
            .apply()
    }

    fun loadSpec(): TruckSpec {
        if (!prefs.contains("height")) return TruckSpec.DEFAULT
        val d = TruckSpec.DEFAULT
        return TruckSpec(
            heightM = prefs.getFloat("height", d.heightM.toFloat()).toDouble(),
            widthM = prefs.getFloat("width", d.widthM.toFloat()).toDouble(),
            lengthM = prefs.getFloat("length", d.lengthM.toFloat()).toDouble(),
            weightT = prefs.getFloat("weight", d.weightT.toFloat()).toDouble(),
            axleLoadT = prefs.getFloat("axle", d.axleLoadT.toFloat()).toDouble(),
            hazmat = prefs.getBoolean("hazmat", d.hazmat)
        )
    }
}

package com.example.truckrouter

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.preference.PreferenceManager
import android.view.LayoutInflater
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import kotlinx.coroutines.Job
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.truckrouter.databinding.ActivityMainBinding
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import java.util.Locale
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var store: RouteStore
    private lateinit var repo: RouteRepository
    private lateinit var tileManager: TileManager

    private var spec: TruckSpec = TruckSpec.DEFAULT

    private var startPoint: GeoPoint? = null
    private var endPoint: GeoPoint? = null
    private var startMarker: Marker? = null
    private var endMarker: Marker? = null
    private var routeLine: Polyline? = null

    private var myLocationOverlay: MyLocationNewOverlay? = null

    private val locationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) enableMyLocation()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // osmdroid needs a user agent and a configured cache before any MapView is shown.
        Configuration.getInstance().load(
            applicationContext,
            PreferenceManager.getDefaultSharedPreferences(applicationContext)
        )
        Configuration.getInstance().userAgentValue = packageName

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        store = RouteStore(this)
        tileManager = TileManager(this)
        repo = RouteRepository(this, store, OfflineRouter(this))
        spec = store.loadSpec()

        setupMap()
        updateStatus()
        restoreLastRoute()

        binding.btnSpecs.setOnClickListener { showSpecsDialog() }
        binding.btnSteps.setOnClickListener { showStepsDialog() }
        binding.btnClear.setOnClickListener { clearAll() }
        binding.btnRoute.setOnClickListener { computeRoute() }

        ensureLocationPermission()
    }

    // ---------------- map ----------------

    private fun setupMap() {
        binding.map.setTileSource(TileSourceFactory.MAPNIK)
        binding.map.setMultiTouchControls(true)
        binding.map.controller.setZoom(Config.DEFAULT_ZOOM)
        binding.map.controller.setCenter(GeoPoint(Config.DEFAULT_LAT, Config.DEFAULT_LON))

        val receiver = object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                onMapTap(p)
                return true
            }

            override fun longPressHelper(p: GeoPoint): Boolean = false
        }
        // index 0 so taps are handled beneath markers
        binding.map.overlays.add(0, MapEventsOverlay(receiver))
    }

    private fun onMapTap(p: GeoPoint) {
        // First tap = start, second = destination, third resets to a new start.
        if (startPoint == null || endPoint != null) {
            clearRouteLine()
            endPoint = null
            endMarker?.let { binding.map.overlays.remove(it) }
            endMarker = null
            startPoint = p
            startMarker = placeMarker(startMarker, p, getString(R.string.start), true)
            binding.tvHint.text = getString(R.string.hint_pick_end)
        } else {
            endPoint = p
            endMarker = placeMarker(endMarker, p, getString(R.string.destination), false)
            binding.tvHint.text = getString(R.string.hint_ready)
        }
        binding.map.invalidate()
    }

    private fun placeMarker(existing: Marker?, p: GeoPoint, title: String, isStart: Boolean): Marker {
        existing?.let { binding.map.overlays.remove(it) }
        val color = if (isStart) Color.parseColor("#2E7D32") else Color.parseColor("#C62828")
        val m = Marker(binding.map).apply {
            position = p
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            this.title = title
            // Tint a private copy of the default marker so start/end stay distinct.
            val copy = icon?.constantState?.newDrawable()?.mutate()
            if (copy != null) {
                copy.setTint(color)
                icon = copy
            }
        }
        binding.map.overlays.add(m)
        return m
    }

    private fun ensureLocationPermission() {
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) enableMyLocation()
        else locationPermission.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    private fun enableMyLocation() {
        if (myLocationOverlay != null) return
        val overlay = MyLocationNewOverlay(GpsMyLocationProvider(this), binding.map)
        overlay.enableMyLocation()
        binding.map.overlays.add(overlay)
        myLocationOverlay = overlay
    }

    // ---------------- routing ----------------

    private fun computeRoute() {
        val key = cleanOrsKey(Config.ORS_API_KEY)

        // Çevrimdışı öncelikli: Türkiye verisi yoksa önce tek seferlik indirme öner.
        if (!tileManager.isReady()) {
            promptTileDownload(routeAfter = true)
            return
        }

        // Resolve start/end. If only a destination was tapped, use current location as start.
        val end = endPoint ?: startPoint
        if (end == null) {
            toast(getString(R.string.need_points))
            return
        }
        val start: GeoPoint? = if (endPoint != null) startPoint
        else myLocationOverlay?.myLocation

        if (start == null) {
            toast(getString(R.string.need_start))
            return
        }

        toast("Rota hesaplanıyor…")
        setLoading(true)
        lifecycleScope.launch {
            val outcome = repo.route(
                apiKey = key,
                start = LatLon(start.latitude, start.longitude),
                end = LatLon(end.latitude, end.longitude),
                spec = spec
            )
            setLoading(false)
            when (outcome) {
                is Outcome.Online -> {
                    drawRoute(outcome.route)
                    showSummary(outcome.route, null)
                }
                is Outcome.OfflineRouted -> {
                    drawRoute(outcome.route)
                    showSummary(outcome.route, outcome.note)
                }
                is Outcome.OfflineCached -> {
                    drawRoute(outcome.route)
                    showSummary(outcome.route, outcome.note)
                    toast(outcome.note)
                }
                is Outcome.Error -> {
                    binding.tvRouteInfo.text = outcome.message
                    binding.btnSteps.isEnabled = false
                    // Show the real reason loudly so it can't be missed.
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("Rota alınamadı")
                        .setMessage(outcome.message)
                        .setPositiveButton(android.R.string.ok, null)
                        .show()
                }
            }
        }
    }

    /**
     * Defensively clean a pasted ORS key: strip whitespace, an accidental
     * "api_key=" prefix, any "&..." suffix, and surrounding quotes. This auto-fixes
     * the most common copy/paste mistake from the ORS playground URL.
     */
    private fun cleanOrsKey(raw: String): String {
        var k = raw.trim()
        if (k.contains("api_key=")) k = k.substringAfter("api_key=")
        if (k.contains("&")) k = k.substringBefore("&")
        return k.trim().trim('"')
    }

    private var lastSteps: List<RouteStep> = emptyList()

    private fun drawRoute(route: RouteResult) {
        clearRouteLine()
        val line = Polyline(binding.map).apply {
            setPoints(route.points.map { GeoPoint(it.lat, it.lon) })
            outlinePaint.color = Color.parseColor("#1565C0")
            outlinePaint.strokeWidth = 12f
        }
        binding.map.overlays.add(line)
        routeLine = line

        // Zoom to the route (bounds computed manually to avoid version-specific API).
        if (route.points.isNotEmpty()) {
            var north = -90.0
            var south = 90.0
            var east = -180.0
            var west = 180.0
            for (p in route.points) {
                if (p.lat > north) north = p.lat
                if (p.lat < south) south = p.lat
                if (p.lon > east) east = p.lon
                if (p.lon < west) west = p.lon
            }
            runCatching {
                binding.map.zoomToBoundingBox(
                    org.osmdroid.util.BoundingBox(north, east, south, west), true, 64
                )
            }
        }
        binding.map.invalidate()
    }

    private fun showSummary(route: RouteResult, note: String?) {
        val km = route.distanceM / 1000.0
        val mins = (route.durationS / 60.0).roundToInt()
        val base = String.format(Locale.getDefault(), "%.1f km · %d dk", km, mins)
        binding.tvRouteInfo.text = if (note != null) "$base\n($note)" else base
        lastSteps = route.steps
        binding.btnSteps.isEnabled = route.steps.isNotEmpty()
    }

    private fun showStepsDialog() {
        if (lastSteps.isEmpty()) return
        val sb = StringBuilder()
        lastSteps.forEachIndexed { i, s ->
            val km = s.distanceM / 1000.0
            sb.append("${i + 1}. ${s.instruction}")
            if (s.distanceM > 0) sb.append(String.format(Locale.getDefault(), "  (%.1f km)", km))
            sb.append("\n")
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.directions)
            .setMessage(sb.toString().trim())
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    // ---------------- specs dialog ----------------

    private fun showSpecsDialog() {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_specs, null)
        val etHeight = view.findViewById<EditText>(R.id.etHeight)
        val etWidth = view.findViewById<EditText>(R.id.etWidth)
        val etLength = view.findViewById<EditText>(R.id.etLength)
        val etWeight = view.findViewById<EditText>(R.id.etWeight)
        val etAxle = view.findViewById<EditText>(R.id.etAxle)
        val cbHazmat = view.findViewById<CheckBox>(R.id.cbHazmat)

        etHeight.setText(spec.heightM.toString())
        etWidth.setText(spec.widthM.toString())
        etLength.setText(spec.lengthM.toString())
        etWeight.setText(spec.weightT.toString())
        etAxle.setText(spec.axleLoadT.toString())
        cbHazmat.isChecked = spec.hazmat

        AlertDialog.Builder(this)
            .setTitle(R.string.truck_specs)
            .setView(view)
            .setPositiveButton(R.string.save) { _, _ ->
                spec = TruckSpec(
                    heightM = etHeight.text.toString().toDoubleOrNull() ?: 0.0,
                    widthM = etWidth.text.toString().toDoubleOrNull() ?: 0.0,
                    lengthM = etLength.text.toString().toDoubleOrNull() ?: 0.0,
                    weightT = etWeight.text.toString().toDoubleOrNull() ?: 0.0,
                    axleLoadT = etAxle.text.toString().toDoubleOrNull() ?: 0.0,
                    hazmat = cbHazmat.isChecked
                )
                store.saveSpec(spec)
                toast(getString(R.string.specs_saved))
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    // ---------------- offline tiles (Türkiye) ----------------

    private fun promptTileDownload(routeAfter: Boolean) {
        AlertDialog.Builder(this)
            .setTitle(R.string.tiles_needed_title)
            .setMessage(R.string.tiles_needed_body)
            .setPositiveButton(R.string.download) { _, _ -> startTileDownload(routeAfter) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun startTileDownload(routeAfter: Boolean) {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_progress, null)
        val bar = view.findViewById<ProgressBar>(R.id.pbDownload)
        val txt = view.findViewById<TextView>(R.id.tvProgress)

        var job: Job? = null
        val dlg = AlertDialog.Builder(this)
            .setTitle(R.string.downloading_tiles)
            .setView(view)
            .setCancelable(false)
            .setNegativeButton(android.R.string.cancel) { _, _ -> job?.cancel() }
            .show()

        job = lifecycleScope.launch {
            try {
                tileManager.download(Config.TILES_URL) { done, total ->
                    runOnUiThread {
                        if (total > 0) {
                            val pct = ((done * 100) / total).toInt()
                            bar.isIndeterminate = false
                            bar.progress = pct
                            txt.text = "%$pct  ·  ${done / 1048576} / ${total / 1048576} MB"
                        } else {
                            txt.text = "${done / 1048576} MB indirildi"
                        }
                    }
                }
                OfflineRouter.reset()
                dlg.dismiss()
                toast(getString(R.string.tiles_ready))
                updateStatus()
                if (routeAfter) computeRoute()
            } catch (e: kotlinx.coroutines.CancellationException) {
                dlg.dismiss()
                toast(getString(R.string.download_cancelled))
            } catch (e: Exception) {
                dlg.dismiss()
                updateStatus()
                AlertDialog.Builder(this@MainActivity)
                    .setTitle(R.string.download_failed_title)
                    .setMessage(e.message ?: "Bilinmeyen hata")
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
            }
        }
    }

    private fun showApiKeyDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.api_key_needed_title)
            .setMessage(R.string.api_key_needed_body)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    // ---------------- misc ----------------

    private fun restoreLastRoute() {
        val cached = store.loadLast() ?: return
        drawRoute(cached)
        showSummary(cached, getString(R.string.saved_route))
    }

    private fun clearAll() {
        clearRouteLine()
        startMarker?.let { binding.map.overlays.remove(it) }
        endMarker?.let { binding.map.overlays.remove(it) }
        startMarker = null
        endMarker = null
        startPoint = null
        endPoint = null
        lastSteps = emptyList()
        binding.btnSteps.isEnabled = false
        binding.tvRouteInfo.text = ""
        binding.tvHint.text = getString(R.string.hint_pick_start)
        binding.map.invalidate()
    }

    private fun clearRouteLine() {
        routeLine?.let { binding.map.overlays.remove(it) }
        routeLine = null
    }

    private fun updateStatus() {
        val online = Connectivity.isOnline(this)
        val base = if (online) getString(R.string.status_online) else getString(R.string.status_offline)
        val suffix = if (::tileManager.isInitialized && tileManager.isReady())
            getString(R.string.status_tiles_ready) else ""
        binding.tvStatus.text = base + suffix
        binding.tvStatus.setTextColor(
            if (online) Color.parseColor("#2E7D32") else Color.parseColor("#C62828")
        )
    }

    private fun setLoading(loading: Boolean) {
        binding.progress.visibility = if (loading) android.view.View.VISIBLE else android.view.View.GONE
        binding.btnRoute.isEnabled = !loading
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    // ---------------- osmdroid lifecycle ----------------

    override fun onResume() {
        super.onResume()
        binding.map.onResume()
        updateStatus()
    }

    override fun onPause() {
        super.onPause()
        binding.map.onPause()
    }
}

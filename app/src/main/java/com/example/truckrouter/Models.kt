package com.example.truckrouter

/** A simple latitude/longitude pair (degrees). */
data class LatLon(val lat: Double, val lon: Double)

/** One turn-by-turn instruction. */
data class RouteStep(
    val instruction: String,
    val distanceM: Double,
    val durationS: Double
)

/** A computed route: geometry + summary + instructions. */
data class RouteResult(
    val points: List<LatLon>,
    val distanceM: Double,
    val durationS: Double,
    val steps: List<RouteStep>
)

/**
 * Truck dimensions/restrictions sent to the routing engine.
 * Units: meters and metric tonnes. A value of 0 means "don't constrain".
 */
data class TruckSpec(
    val heightM: Double,
    val widthM: Double,
    val lengthM: Double,
    val weightT: Double,
    val axleLoadT: Double,
    val hazmat: Boolean
) {
    companion object {
        // Typical EU semi-trailer defaults.
        val DEFAULT = TruckSpec(
            heightM = 4.0,
            widthM = 2.55,
            lengthM = 16.5,
            weightT = 40.0,
            axleLoadT = 11.5,
            hazmat = false
        )
    }
}

/** Result wrapper returned by the repository. */
sealed class Outcome {
    /** Freshly computed online. */
    data class Online(val route: RouteResult) : Outcome()

    /** Freshly computed ON-DEVICE by the offline Valhalla engine. */
    data class OfflineRouted(val route: RouteResult, val note: String) : Outcome()

    /** No/failed network: showing a previously saved route. [note] explains why. */
    data class OfflineCached(val route: RouteResult, val note: String) : Outcome()

    /** Nothing could be produced. */
    data class Error(val message: String) : Outcome()
}

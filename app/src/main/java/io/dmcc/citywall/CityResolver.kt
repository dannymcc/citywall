package io.dmcc.citywall

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** Where the user is, resolved to a place name and the coordinates it was seen at. */
data class CityFix(val name: String, val lat: Double, val lon: Double)

/**
 * Turns "where am I" into a [CityFix] using AOSP [LocationManager] (no Play Services)
 * and the async [Geocoder]. Methods block on a latch with a timeout and are intended
 * to run on a worker thread.
 */
class CityResolver(private val ctx: Context) {

    private val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    private fun hasCoarse(): Boolean =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * @param useCapital when true, returns the capital of the country the user is in
     *   (centred on the capital), falling back to the real local area if the country
     *   isn't known or the capital can't be located.
     */
    @SuppressLint("MissingPermission") // gate checked via hasCoarse()
    fun currentCity(useCapital: Boolean = false): CityFix? {
        if (!hasCoarse()) return null
        val loc = acquireLocation() ?: return null
        val address = reverseGeocode(loc.latitude, loc.longitude) ?: return null

        if (useCapital) {
            val capital = Capitals.forCountry(address.countryCode)
            val coords = capital?.let { coordsFor(it) }
            if (capital != null && coords != null) {
                return CityFix(capital, coords.first, coords.second)
            }
            // else fall through to the real local area
        }

        val name = address.locality ?: address.subAdminArea ?: address.adminArea ?: return null
        return CityFix(name, loc.latitude, loc.longitude)
    }

    /** Forward geocode a place name to coordinates — used only by the opt-in pre-warm. */
    fun coordsFor(cityName: String): Pair<Double, Double>? {
        if (!Geocoder.isPresent()) return null
        val latch = CountDownLatch(1)
        val ref = AtomicReference<Pair<Double, Double>?>()
        try {
            Geocoder(ctx, Locale.getDefault()).getFromLocationName(cityName, 1) { addresses ->
                addresses.firstOrNull()?.let { ref.set(it.latitude to it.longitude) }
                latch.countDown()
            }
            latch.await(20, TimeUnit.SECONDS)
        } catch (_: Exception) {
        }
        return ref.get()
    }

    @SuppressLint("MissingPermission") // gate checked in currentCity()
    private fun acquireLocation(): Location? {
        val provider = chooseProvider() ?: return lastKnown()
        val latch = CountDownLatch(1)
        val ref = AtomicReference<Location?>()
        try {
            lm.getCurrentLocation(
                provider,
                CancellationSignal(),
                ContextCompat.getMainExecutor(ctx),
            ) { location ->
                ref.set(location)
                latch.countDown()
            }
            latch.await(20, TimeUnit.SECONDS)
        } catch (_: Exception) {
        }
        return ref.get() ?: lastKnown()
    }

    /** FUSED if available, else NETWORK, else GPS. */
    private fun chooseProvider(): String? {
        val preferred = listOf(
            LocationManager.FUSED_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.GPS_PROVIDER,
        )
        return preferred.firstOrNull { it in lm.allProviders && lm.isProviderEnabled(it) }
    }

    @SuppressLint("MissingPermission") // gate checked in currentCity()
    private fun lastKnown(): Location? {
        for (provider in lm.allProviders) {
            try {
                lm.getLastKnownLocation(provider)?.let { return it }
            } catch (_: Exception) {
            }
        }
        return null
    }

    private fun reverseGeocode(lat: Double, lon: Double): Address? {
        if (!Geocoder.isPresent()) return null
        val latch = CountDownLatch(1)
        val ref = AtomicReference<Address?>()
        try {
            Geocoder(ctx, Locale.getDefault()).getFromLocation(lat, lon, 1) { addresses ->
                ref.set(addresses.firstOrNull())
                latch.countDown()
            }
            latch.await(20, TimeUnit.SECONDS)
        } catch (_: Exception) {
        }
        return ref.get()
    }
}

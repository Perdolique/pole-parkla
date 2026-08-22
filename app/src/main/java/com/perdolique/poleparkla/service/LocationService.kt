package com.perdolique.poleparkla.service

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.perdolique.poleparkla.model.LocationSnapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class LocationService(private val context: Context) {
    private val client: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    fun hasPermission(): Boolean = listOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
    ).any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }

    @Suppress("MissingPermission")
    fun updates(): Flow<LocationSnapshot> = callbackFlow {
        if (!hasPermission()) {
            close()
            return@callbackFlow
        }
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { trySend(it.toSnapshot()) }
            }
        }
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5_000L)
            .setMinUpdateIntervalMillis(2_000L)
            .build()
        client.requestLocationUpdates(request, callback, context.mainLooper)
        awaitClose { client.removeLocationUpdates(callback) }
    }

    @Suppress("MissingPermission")
    suspend fun currentLocation(): LocationSnapshot? {
        if (!hasPermission()) return null
        val token = CancellationTokenSource()
        val fresh = try {
            client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, token.token).await()
        } catch (error: CancellationException) {
            token.cancel()
            throw error
        } catch (_: Exception) {
            null
        }
        if (fresh != null) return fresh.toSnapshot()
        return try {
            client.lastLocation.await()?.toSnapshot()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            null
        }
    }

    private fun android.location.Location.toSnapshot() = LocationSnapshot(
        latitude = latitude,
        longitude = longitude,
        accuracyMeters = accuracy.takeIf { hasAccuracy() },
        capturedAtEpochMillis = time.takeIf { it > 0 } ?: System.currentTimeMillis(),
    )
}

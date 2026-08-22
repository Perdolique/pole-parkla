package com.perdolique.poleparkla.service

import com.perdolique.poleparkla.model.AddressCandidate
import com.perdolique.poleparkla.model.AddressCandidateType
import com.perdolique.poleparkla.model.AddressResolution
import com.perdolique.poleparkla.model.LocationSnapshot
import java.io.InterruptedIOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

class InAksAddressException(message: String, cause: Throwable? = null) : Exception(message, cause)

class InAksAddressService(
    private val connectionFactory: (URL) -> HttpURLConnection = { url ->
        url.openConnection() as HttpURLConnection
    },
    private val coordinateConverter: InAksCoordinateConverter = InAksCoordinateConverter(),
    private val totalTimeoutMillis: Long = TOTAL_TIMEOUT_MILLIS,
) {
    suspend fun resolve(location: LocationSnapshot): AddressResolution = try {
        withTimeout(totalTimeoutMillis) {
            runInterruptible(Dispatchers.IO) { resolveBlocking(location) }
        }
    } catch (error: TimeoutCancellationException) {
        throw InAksAddressException("In-AKS address lookup timed out", error)
    }

    internal fun endpoint(location: LocationSnapshot): String {
        val coordinate = coordinateConverter.toLest97(location.latitude, location.longitude)
        return buildString {
            append(BASE_URL)
            append("?x=")
            append("%.3f".format(Locale.US, coordinate.easting))
            append("&y=")
            append("%.3f".format(Locale.US, coordinate.northing))
            append("&radius=")
            append(radiusMeters(location.accuracyMeters))
            append("&features=TANAV%2CEHITISHOONE&appartment=0&ihist=0")
        }
    }

    internal fun radiusMeters(accuracyMeters: Float?): Int =
        ceil(accuracyMeters?.toDouble() ?: DEFAULT_RADIUS_METERS.toDouble())
            .toInt()
            .coerceIn(MIN_RADIUS_METERS, MAX_RADIUS_METERS)

    private fun resolveBlocking(location: LocationSnapshot): AddressResolution {
        val socketTimeout = totalTimeoutMillis.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        val connection = connectionFactory(URL(endpoint(location))).apply {
            requestMethod = "GET"
            connectTimeout = socketTimeout
            readTimeout = socketTimeout
            useCaches = false
            setRequestProperty("Accept", "application/json")
        }
        return try {
            val status = connection.responseCode
            if (status !in 200..299) throw InAksAddressException("In-AKS returned HTTP $status")
            val response = connection.inputStream
                .bufferedReader(StandardCharsets.UTF_8)
                .use { it.readText() }
            InAksAddressParser.parse(response, location)
        } catch (error: InAksAddressException) {
            throw error
        } catch (error: CancellationException) {
            throw error
        } catch (error: InterruptedException) {
            throw CancellationException("In-AKS address lookup was cancelled", error)
        } catch (error: SocketTimeoutException) {
            throw InAksAddressException("In-AKS address lookup timed out", error)
        } catch (error: InterruptedIOException) {
            throw CancellationException("In-AKS address lookup was cancelled", error)
        } catch (error: Exception) {
            throw InAksAddressException("In-AKS address lookup failed", error)
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val BASE_URL = "https://aks.geoportaal.ee/inaks/inaadress/gazetteer"
        const val TOTAL_TIMEOUT_MILLIS = 10_000L
        const val DEFAULT_RADIUS_METERS = 50
        const val MIN_RADIUS_METERS = 30
        const val MAX_RADIUS_METERS = 100
    }
}

object InAksAddressParser {
    fun parse(raw: String, location: LocationSnapshot): AddressResolution {
        val entries = JSONObject(raw).optJSONArray("addresses")
        val buildings = mutableMapOf<String, AddressCandidate>()
        val streets = mutableMapOf<String, AddressCandidate>()

        for (index in 0 until (entries?.length() ?: 0)) {
            val entry = entries?.optJSONObject(index) ?: continue
            if (entry.optString("olek") != "K") continue
            val type = when (entry.optString("liikVal")) {
                "TANAV" -> AddressCandidateType.STREET
                "EHITISHOONE" -> AddressCandidateType.BUILDING
                else -> continue
            }
            val street = entry.optString("liikluspind").trim().takeIf(String::isNotBlank) ?: continue
            val municipality = entry.optString("omavalitsus").trim().takeIf(String::isNotBlank) ?: continue
            val houseNumber = entry.optString("aadress_nr").trim().takeIf(String::isNotBlank)
            val distanceMeters = entry.optString("kaugus")
                .toDoubleOrNull()
                ?.takeIf { it.isFinite() && it >= 0.0 }
                ?.roundToInt()
                ?: continue

            if (type == AddressCandidateType.STREET) {
                val streetCandidate = AddressCandidate(
                    address = "$street, $municipality",
                    distanceMeters = distanceMeters,
                    type = AddressCandidateType.STREET,
                )
                streets.keepNearest(streetCandidate.address.lowercase(Locale.ROOT), streetCandidate)
            } else if (houseNumber != null) {
                val buildingCandidate = AddressCandidate(
                    address = "$street $houseNumber, $municipality",
                    distanceMeters = distanceMeters,
                    type = AddressCandidateType.BUILDING,
                )
                buildings.keepNearest(buildingCandidate.address.lowercase(Locale.ROOT), buildingCandidate)
            }
        }

        val nearestBuildings = buildings.values.sortedBy(AddressCandidate::distanceMeters)
        val nearestStreets = streets.values.sortedBy(AddressCandidate::distanceMeters)
        val candidates = nearestStreets.take(MAX_STREETS) + nearestBuildings.take(MAX_BUILDINGS)
        val exactCandidates = candidates.filter { it.distanceMeters == 0 }
        val exact = exactCandidates.singleOrNull()
        val suggested = exact ?: candidates.minByOrNull(AddressCandidate::distanceMeters)

        return AddressResolution(
            location = location,
            candidates = candidates,
            suggested = suggested,
            needsReview = exact == null,
        )
    }

    private fun MutableMap<String, AddressCandidate>.keepNearest(
        key: String,
        candidate: AddressCandidate,
    ) {
        val current = this[key]
        if (current == null || candidate.distanceMeters < current.distanceMeters) this[key] = candidate
    }

    private const val MAX_STREETS = 2
    private const val MAX_BUILDINGS = 3
}

package com.perdolique.poleparkla.service

import com.perdolique.poleparkla.model.AddressCandidateType
import com.perdolique.poleparkla.model.LocationSnapshot
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InAksAddressServiceTest {
    @Test
    fun `parser deduplicates and ranks nearby streets and buildings`() {
        val resolution = InAksAddressParser.parse(MULTIPLE_ADDRESSES, LOCATION)

        assertEquals(
            listOf("Sääse tn, Tallinn", "A. H. Tammsaare tee, Tallinn"),
            resolution.candidates
                .filter { it.type == AddressCandidateType.STREET }
                .map { it.address },
        )
        assertEquals(
            listOf("Sääse tn 18, Tallinn", "A. H. Tammsaare tee 92, Tallinn", "Sääse tn 20, Tallinn"),
            resolution.candidates
                .filter { it.type == AddressCandidateType.BUILDING }
                .map { it.address },
        )
        assertEquals("Sääse tn 18, Tallinn", resolution.suggested?.address)
        assertFalse(resolution.needsReview)
    }

    @Test
    fun `multiple exact addresses require review`() {
        val resolution = InAksAddressParser.parse(
            """
                {"addresses":[
                  ${building("Tartu mnt", "24", 0)},
                  ${building("Liivalaia tn", "2", 0)}
                ]}
            """.trimIndent(),
            LOCATION,
        )

        assertEquals(2, resolution.candidates.size)
        assertNotNull(resolution.suggested)
        assertTrue(resolution.needsReview)
    }

    @Test
    fun `empty address result remains reviewable`() {
        val resolution = InAksAddressParser.parse("{\"addresses\":[]}", LOCATION)

        assertTrue(resolution.candidates.isEmpty())
        assertEquals(null, resolution.suggested)
        assertTrue(resolution.needsReview)
    }

    @Test
    fun `invalid and historic objects are ignored`() {
        val resolution = InAksAddressParser.parse(
            """
                {"addresses":[
                  {"olek":"V","liikVal":"EHITISHOONE","liikluspind":"Old tn","aadress_nr":"1","omavalitsus":"Tallinn","kaugus":"0"},
                  {"olek":"K","liikVal":"EHAK","liikluspind":"Ignored tn","aadress_nr":"2","omavalitsus":"Tallinn","kaugus":"0"},
                  {"olek":"K","liikVal":"EHITISHOONE","liikluspind":"","aadress_nr":"3","omavalitsus":"Tallinn","kaugus":"0"},
                  {"olek":"K","liikVal":"EHITISHOONE","liikluspind":"Broken tn","aadress_nr":"4","omavalitsus":"Tallinn","kaugus":"unknown"}
                ]}
            """.trimIndent(),
            LOCATION,
        )

        assertTrue(resolution.candidates.isEmpty())
    }

    @Test
    fun `coordinate conversion preserves documented In-AKS axis order`() {
        val converter = InAksCoordinateConverter()
        val lest97 = converter.toLest97(
            latitude = 59.40802641572147,
            longitude = 24.693720710505183,
        )

        assertEquals(539_399.0, lest97.easting, 5.0)
        assertEquals(6_585_772.0, lest97.northing, 5.0)

    }

    @Test
    fun `radius follows reported accuracy within field bounds`() {
        val service = InAksAddressService()

        assertEquals(50, service.radiusMeters(null))
        assertEquals(30, service.radiusMeters(4.2f))
        assertEquals(51, service.radiusMeters(50.1f))
        assertEquals(100, service.radiusMeters(250f))
    }

    @Test
    fun `service parses a successful response and disconnects`() = runBlocking {
        val connection = ResponseConnection(200, MULTIPLE_ADDRESSES)
        val service = InAksAddressService(connectionFactory = { connection })

        val resolution = service.resolve(LOCATION)

        assertEquals("Sääse tn 18, Tallinn", resolution.suggested?.address)
        assertTrue(connection.disconnected)
    }

    @Test
    fun `service reports HTTP failures without returning a suggestion`() = runBlocking {
        val connection = ResponseConnection(503, "unavailable")
        val service = InAksAddressService(connectionFactory = { connection })

        val error = runCatching { service.resolve(LOCATION) }.exceptionOrNull()

        assertTrue(error is InAksAddressException)
        assertTrue(connection.disconnected)
    }

    @Test
    fun `service reports malformed responses`() = runBlocking {
        val connection = ResponseConnection(200, "{not-json")
        val service = InAksAddressService(connectionFactory = { connection })

        val error = runCatching { service.resolve(LOCATION) }.exceptionOrNull()

        assertTrue(error is InAksAddressException)
        assertTrue(connection.disconnected)
    }

    @Test
    fun `endpoint sends L-EST coordinates and bounded radius`() {
        val endpoint = InAksAddressService().endpoint(LOCATION)

        assertTrue(endpoint.startsWith("https://aks.geoportaal.ee/inaks/inaadress/gazetteer?"))
        assertTrue(endpoint.contains("x=539"))
        assertTrue(endpoint.contains("y=6585"))
        assertTrue(endpoint.contains("radius=30"))
        assertTrue(endpoint.contains("features=TANAV%2CEHITISHOONE"))
    }

    private class ResponseConnection(
        private val status: Int,
        private val body: String,
    ) : HttpURLConnection(URL("https://aks.geoportaal.ee/inaks/inaadress/gazetteer")) {
        var disconnected = false
            private set

        override fun connect() = Unit

        override fun disconnect() {
            disconnected = true
        }

        override fun usingProxy(): Boolean = false

        override fun getResponseCode(): Int = status

        override fun getInputStream(): InputStream = ByteArrayInputStream(body.toByteArray())
    }

    private companion object {
        val LOCATION = LocationSnapshot(
            latitude = 59.40802641572147,
            longitude = 24.693720710505183,
            accuracyMeters = 8.5f,
            capturedAtEpochMillis = 1L,
        )

        val MULTIPLE_ADDRESSES = """
            {"addresses":[
              ${building("Sääse tn", "20", 44)},
              ${building("Sääse tn", "20", 25)},
              ${building("Sääse tn", "18", 18)},
              ${building("Sääse tn", "18", 0)},
              ${building("Sääse tn", "16", 45)},
              ${building("A. H. Tammsaare tee", "92", 23)},
              {"olek":"K","liikVal":"TANAV","liikluspind":"A. H. Tammsaare tee","aadress_nr":"","omavalitsus":"Tallinn","kaugus":"40"},
              {"olek":"K","liikVal":"TANAV","liikluspind":"Sääse tn","aadress_nr":"","omavalitsus":"Tallinn","kaugus":"37"}
            ]}
        """.trimIndent()

        fun building(street: String, number: String, distance: Int): String =
            """{"olek":"K","liikVal":"EHITISHOONE","liikluspind":"$street","aadress_nr":"$number","omavalitsus":"Tallinn","kaugus":"$distance"}"""
    }
}

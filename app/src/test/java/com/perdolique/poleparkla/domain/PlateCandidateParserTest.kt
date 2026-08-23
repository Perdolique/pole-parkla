package com.perdolique.poleparkla.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlateCandidateParserTest {
    @Test
    fun `ranks a common Estonian plate first`() {
        val candidates = PlateCandidateParser.parse("Toyota AB-1234, nearby plate 003 puk")

        assertEquals("003 PUK", candidates.first())
        assertTrue("AB1234" in candidates)
    }

    @Test
    fun `keeps a foreign plate candidate`() {
        assertEquals(listOf("AB12CDE"), PlateCandidateParser.parse("AB-12-CDE"))
    }

    @Test
    fun `keeps a digit-only foreign plate candidate`() {
        assertEquals(listOf("12345678"), PlateCandidateParser.parse("plate 12345678"))
    }

    @Test
    fun `ranks an Estonian O zero correction before the raw numeric candidate`() {
        assertEquals(
            listOf("003 OOO", "003000"),
            PlateCandidateParser.parse("003 000"),
        )
    }

    @Test
    fun `keeps the raw candidate beside a correction with mixed O and zero characters`() {
        assertEquals(
            listOf("003 OOO", "0O30O0"),
            PlateCandidateParser.parse("0O3-0O0"),
        )
    }

    @Test
    fun `rejects letter-only words`() {
        assertTrue(PlateCandidateParser.parse("TALLINN TOYOTA TEST").isEmpty())
    }
}

package com.perdolique.poleparkla.domain

import com.perdolique.poleparkla.model.ViolationType
import org.junit.Assert.assertEquals
import org.junit.Test

class ViolationTemplatesTest {
    @Test
    fun `contains both fixed Estonian descriptions`() {
        assertEquals(
            "Sõiduk on pargitud jalgrattateele või jalgrattarajale.",
            ViolationTemplates.description(ViolationType.CYCLE_PATH),
        )
        assertEquals(
            "Sõiduk on pargitud jalgteele või kõnniteele.",
            ViolationTemplates.description(ViolationType.PEDESTRIAN_PATH),
        )
    }
}

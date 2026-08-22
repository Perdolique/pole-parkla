package com.perdolique.poleparkla.ui.screens

import com.perdolique.poleparkla.ui.ReportCompletionStep
import org.junit.Assert.assertEquals
import org.junit.Test

class ReportPrimaryActionTest {
    @Test
    fun missingSectionsOpenTheirMatchingEditorsInOrder() {
        val expected = mapOf(
            ReportCompletionStep.VEHICLE to ReportEditor.VEHICLE,
            ReportCompletionStep.VIOLATION to ReportEditor.VIOLATION,
            ReportCompletionStep.LOCATION to ReportEditor.LOCATION,
            ReportCompletionStep.DELIVERY to ReportEditor.DELIVERY,
        )

        expected.forEach { (step, editor) ->
            var opened: ReportEditor? = null
            performReportPrimaryAction(step, {}, { opened = it }, {})
            assertEquals(editor, opened)
        }
    }

    @Test
    fun photosAndReadyReportUseDedicatedActions() {
        var photos = 0
        var ready = 0
        performReportPrimaryAction(ReportCompletionStep.PHOTOS, { photos++ }, {}, { ready++ })
        performReportPrimaryAction(null, { photos++ }, {}, { ready++ })

        assertEquals(1, photos)
        assertEquals(1, ready)
    }
}

package com.perdolique.poleparkla.domain

import com.perdolique.poleparkla.model.ViolationType

object ViolationTemplates {
    const val CYCLE_PATH_DESCRIPTION =
        "Sõiduk on pargitud jalgrattateele või jalgrattarajale."
    const val PEDESTRIAN_PATH_DESCRIPTION =
        "Sõiduk on pargitud jalgteele või kõnniteele."

    fun description(type: ViolationType?): String? = when (type) {
        ViolationType.CYCLE_PATH -> CYCLE_PATH_DESCRIPTION
        ViolationType.PEDESTRIAN_PATH -> PEDESTRIAN_PATH_DESCRIPTION
        ViolationType.CUSTOM, null -> null
    }
}


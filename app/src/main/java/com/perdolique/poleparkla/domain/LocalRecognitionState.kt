package com.perdolique.poleparkla.domain

import com.perdolique.poleparkla.model.Report
import com.perdolique.poleparkla.model.ReportPhoto

fun List<ReportPhoto>.localRecognitionFingerprint(): String =
    "v1:" + map(ReportPhoto::id).sorted().joinToString(separator = ",")

fun Report.hasCurrentLocalRecognition(): Boolean =
    localRecognitionFingerprint == photos.localRecognitionFingerprint()

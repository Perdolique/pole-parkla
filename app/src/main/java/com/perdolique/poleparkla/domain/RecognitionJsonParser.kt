package com.perdolique.poleparkla.domain

import com.perdolique.poleparkla.model.RecognitionResult
import com.perdolique.poleparkla.model.RecognitionSource
import com.perdolique.poleparkla.model.ViolationType
import org.json.JSONObject

object RecognitionJsonParser {
    fun parse(raw: String, source: RecognitionSource): RecognitionResult {
        val json = JSONObject(extractJson(raw))
        val rawCandidates = json.optJSONArray("plateCandidates")
        val candidates = buildList {
            if (rawCandidates != null) {
                for (index in 0 until minOf(rawCandidates.length(), 5)) {
                    PlateCandidateParser.normalize(rawCandidates.optString(index))?.let(::add)
                }
            }
        }.distinct()

        return RecognitionResult(
            source = source,
            plateCandidates = candidates,
            vehicleMake = json.optNullableString("vehicleMake"),
            vehicleModel = json.optNullableString("vehicleModel"),
            suggestedViolationType = json.optNullableString("suggestedViolationType")
                ?.let { runCatching { ViolationType.valueOf(it) }.getOrNull() }
                ?.takeIf { it != ViolationType.CUSTOM },
        )
    }

    private fun extractJson(raw: String): String {
        val trimmed = raw.trim()
        val start = trimmed.indexOf('{')
        val end = trimmed.lastIndexOf('}')
        require(start >= 0 && end > start) { "Recognition response does not contain JSON" }
        return trimmed.substring(start, end + 1)
    }

    private fun JSONObject.optNullableString(key: String): String? =
        if (isNull(key)) null else optString(key).trim().takeIf(String::isNotBlank)
}

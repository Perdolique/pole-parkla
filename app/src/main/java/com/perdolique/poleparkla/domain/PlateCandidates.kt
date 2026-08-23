package com.perdolique.poleparkla.domain

import com.perdolique.poleparkla.model.PlateCandidate
import com.perdolique.poleparkla.model.PlateObservation
import com.perdolique.poleparkla.model.RecognitionSource

fun aggregatePlateCandidates(
    observations: List<PlateObservation>,
    limit: Int = 5,
): List<PlateCandidate> = observations
    .mapNotNull { observation ->
        PlateCandidateParser.normalize(observation.value)?.let { normalized ->
            observation.copy(value = normalized)
        }
    }
    .groupBy(PlateObservation::value)
    .map { (value, matches) ->
        PlateCandidate(
            value = value,
            sources = matches.mapTo(linkedSetOf(), PlateObservation::source),
            supportingPhotoCount = matches.map(PlateObservation::photoId).distinct().size,
            observations = matches.sortedWith(plateObservationComparator),
        )
    }
    .sortedWith(plateCandidateComparator)
    .take(limit)

internal fun exactPlateCandidate(
    plate: String,
    observations: List<PlateObservation>,
): PlateCandidate? {
    val normalizedPlate = PlateCandidateParser.normalize(plate) ?: return null
    return aggregatePlateCandidates(observations, limit = Int.MAX_VALUE)
        .firstOrNull { it.value == normalizedPlate }
}

private val plateCandidateComparator =
    compareByDescending<PlateCandidate>(PlateCandidate::supportingPhotoCount)
        .thenByDescending { RecognitionSource.LOCAL_PLATE_MODEL in it.sources }
        .thenByDescending { it.sources.size }
        .thenByDescending { candidate -> candidate.observations.maxNullable(PlateObservation::relativeArea) }
        .thenByDescending { candidate ->
            candidate.observations.maxNullable(PlateObservation::detectionConfidence)
        }
        .thenByDescending { candidate ->
            candidate.observations.maxNullable(PlateObservation::characterConfidence)
        }
        .thenBy(PlateCandidate::value)

private val plateObservationComparator =
    compareByDescending<PlateObservation> { it.bounds != null }
        .thenByDescending { it.relativeArea ?: Float.NEGATIVE_INFINITY }
        .thenByDescending { it.detectionConfidence ?: Float.NEGATIVE_INFINITY }
        .thenByDescending { it.characterConfidence ?: Float.NEGATIVE_INFINITY }
        .thenBy(PlateObservation::photoId)

private fun List<PlateObservation>.maxNullable(
    selector: (PlateObservation) -> Float?,
): Float = mapNotNull(selector).maxOrNull() ?: Float.NEGATIVE_INFINITY

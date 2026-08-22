package com.perdolique.poleparkla.service

import com.perdolique.poleparkla.model.ReportPhoto
import java.io.File

internal data class PhotoRecognitionCacheKey(
    val id: String,
    val filePath: String,
    val length: Long,
    val lastModified: Long,
)

internal fun ReportPhoto.recognitionCacheKey(): PhotoRecognitionCacheKey {
    val file = File(filePath)
    return PhotoRecognitionCacheKey(
        id = id,
        filePath = filePath,
        length = file.length(),
        lastModified = file.lastModified(),
    )
}

package com.perdolique.poleparkla.service

import org.locationtech.proj4j.CRSFactory
import org.locationtech.proj4j.CoordinateTransformFactory
import org.locationtech.proj4j.ProjCoordinate

data class Lest97Coordinate(
    val easting: Double,
    val northing: Double,
)

class InAksCoordinateConverter {
    private val coordinateReferenceSystems = CRSFactory()
    private val coordinateTransforms = CoordinateTransformFactory()
    private val wgs84 = coordinateReferenceSystems.createFromName("EPSG:4326")
    private val lest97 = coordinateReferenceSystems.createFromName("EPSG:3301")
    private val toLest97 = coordinateTransforms.createTransform(wgs84, lest97)

    fun toLest97(latitude: Double, longitude: Double): Lest97Coordinate {
        val result = toLest97.transform(ProjCoordinate(longitude, latitude), ProjCoordinate())
        return Lest97Coordinate(easting = result.x, northing = result.y)
    }
}

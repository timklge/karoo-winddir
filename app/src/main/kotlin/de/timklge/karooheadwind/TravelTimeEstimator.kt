package de.timklge.karooheadwind

import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Class containing cycling physics calculations
 */
object TravelTimeEstimator {
    // Constants for cycling physics calculations
    private const val GRAVITY = 9.81 // m/s²
    private const val RHO_AIR = 1.225 // kg/m³ at sea level
    private const val CDA = 0.3 // m² (typical cyclist aerodynamic drag area)
    private const val ROLLING_RESISTANCE = 0.004 // typical value for road bikes
    private const val DRIVETRAIN_EFFICIENCY = 0.95 // typical bike drivetrain efficiency
    private const val BIKE_MASS = 9.5 // kg (default bike mass)
    private const val SUSTAINABLE_POWER_FACTOR = 0.70 // riding at 70% of FTP

    /**
     * Estimates the distance a cyclist can travel given time, FTP, and optional elevation profile
     * @param timeSeconds Duration of the ride in seconds
     * @param ftp Functional Threshold Power in watts
     * @param elevationProfile Optional elevation profile of the route
     * @return Estimated distance in meters
     */
    fun estimateDistance(
        timeSeconds: Int,
        ftp: Int,
        weight: Int,
        elevationProfile: SampledElevationData? = null,
        elevatioStartAtIndex: Int = 0
    ): Double {
        val totalMass = weight + BIKE_MASS
        // Apply 70% FTP factor and drivetrain efficiency
        val sustainablePower = ftp * SUSTAINABLE_POWER_FACTOR * DRIVETRAIN_EFFICIENCY

        // If we have elevation data, use it for a more accurate calculation
        if (elevationProfile != null) {
            return estimateDistanceWithElevation(timeSeconds, sustainablePower, totalMass, weight, elevationProfile, elevatioStartAtIndex)
        }

        // Simple flat-ground estimation using the cubic relationship between power and speed
        // P = k * v³ + C * v where k is aero coefficient and C is rolling resistance
        val k = 0.5 * RHO_AIR * CDA
        val c = totalMass * GRAVITY * ROLLING_RESISTANCE

        // Solve for velocity using Newton's method
        var velocity = 7.0 // Initial guess (7 m/s ≈ 25 km/h, lower due to 70% FTP)
        for (i in 1..10) { // Usually converges in a few iterations
            val f = k * velocity.pow(3) + c * velocity - sustainablePower
            val fPrime = 3 * k * velocity.pow(2) + c
            velocity -= f / fPrime
        }

        return velocity * timeSeconds
    }

    /**
     * Estimates distance considering elevation profile
     */
    private fun estimateDistanceWithElevation(
        timeSeconds: Int,
        sustainablePower: Double,
        totalMass: Double,
        weight: Int,
        elevationProfile: SampledElevationData,
        elevatioStartAtIndex: Int
    ): Double {
        var totalDistance = 0.0
        var remainingTime = timeSeconds.toDouble()
        var currentIndex = elevatioStartAtIndex
        var energyReserve = calculateEnergyReserve(timeSeconds)  // For short climbs

        while (remainingTime > 0 && currentIndex < elevationProfile.elevations.size - 1) {
            val elevation1 = elevationProfile.elevations[currentIndex]
            val elevation2 = elevationProfile.elevations[currentIndex + 1]
            val grade = (elevation2 - elevation1) / elevationProfile.interval

            // Calculate velocity for this segment
            val gravityComponent = totalMass * GRAVITY * sin(atan(grade))
            val k = 0.5 * RHO_AIR * CDA
            val c = totalMass * GRAVITY * (ROLLING_RESISTANCE * cos(atan(grade)) + sin(atan(grade)))

            // Calculate power available for this segment
            val availablePower = if (grade > 0.06) { // > 6% grade
                // Allow temporarily higher power output on steep sections
                min(sustainablePower * 1.2, sustainablePower + (energyReserve / remainingTime))
            } else {
                sustainablePower
            }

            // Solve for velocity using Newton's method
            var velocity = 7.0 // Initial guess
            for (i in 1..10) {
                val f = k * velocity.pow(3) + c * velocity - availablePower
                val fPrime = 3 * k * velocity.pow(2) + c
                velocity -= f / fPrime
            }

            val timeForSegment = elevationProfile.interval / velocity
            if (timeForSegment <= remainingTime) {
                totalDistance += elevationProfile.interval
                remainingTime -= timeForSegment
                // Update energy reserve
                if (grade > 0.06) {
                    energyReserve -= (availablePower - sustainablePower) * timeForSegment
                } else {
                    energyReserve = min(
                        calculateEnergyReserve(timeSeconds),
                        energyReserve + (sustainablePower - availablePower) * timeForSegment * 0.5
                    )
                }
                currentIndex++
            } else {
                totalDistance += velocity * remainingTime
                remainingTime = 0.0
            }
        }

        // If we still have remaining time, continue on flat ground
        if (remainingTime > 0) {
            val flatVelocity = estimateDistance(1, (sustainablePower / (SUSTAINABLE_POWER_FACTOR * DRIVETRAIN_EFFICIENCY)).roundToInt(), weight)
            totalDistance += flatVelocity * remainingTime
        }

        return totalDistance
    }

    /**
     * Calculates available energy reserve for short intense efforts
     * @param timeSeconds Total ride duration in seconds
     * @return Energy reserve in Joules
     */
    private fun calculateEnergyReserve(timeSeconds: Int): Double {
        // Rough estimation of available anaerobic work capacity
        // This is a simplification - in reality it would depend on many factors
        val baseReserve = 20000.0 // ~20kJ anaerobic work capacity
        return baseReserve * min(1.0, timeSeconds / 3600.0)
    }
}
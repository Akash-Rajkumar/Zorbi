package com.zorbi.fakes.p1

import com.zorbi.core.Calibration
import com.zorbi.core.CalibrationRepository
import com.zorbi.core.Side
import com.zorbi.core.TrackerFrame
import com.zorbi.core.TrackerId

class FakeCalibrationRepository : CalibrationRepository {
    private val storage = mutableMapOf<String, Calibration>()

    override suspend fun load(packageName: String): Calibration? = storage[packageName]

    override suspend fun save(packageName: String, c: Calibration) {
        storage[packageName] = c
    }

    override fun computeFromSamples(trackerId: TrackerId, samples: List<TrackerFrame>): Calibration {
        return Calibration(
            neutralAngleDeg = 0f,
            maxAngleDeg = 35f,
            reachScaleX = 0.8f,
            reachScaleY = 0.8f,
            dominantHand = Side.RIGHT,
            createdAtEpochMs = System.currentTimeMillis()
        )
    }
}


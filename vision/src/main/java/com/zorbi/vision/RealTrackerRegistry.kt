package com.zorbi.vision

import android.content.Context
import androidx.camera.core.Preview
import androidx.lifecycle.LifecycleOwner
import com.zorbi.core.Tracker
import com.zorbi.core.TrackerId
import com.zorbi.core.TrackerRegistry
import java.util.concurrent.ConcurrentHashMap

/**
 * Production implementation of [TrackerRegistry] (Gate I-2 / Phase 1).
 *
 * Responsibilities:
 * - Registers and lazily instantiates production trackers for [TrackerId.HAND_SINGLE],
 *   [TrackerId.HAND_DUAL_WHEEL], and [TrackerId.POSE_LITE].
 * - Reuses existing tracker instances per [TrackerId] to prevent duplicate CameraX or MediaPipe bindings.
 * - Supports injection of [externalLifecycleOwner] and [previewSurfaceProvider] for UI/camera previews.
 * - Supports custom tracker factory overrides for testing or specialized camera pipelines.
 * - Thread-safe lifecycle teardown via [releaseAll].
 */
class RealTrackerRegistry(
    private val context: Context,
    private val externalLifecycleOwner: LifecycleOwner? = null,
    private val previewSurfaceProvider: Preview.SurfaceProvider? = null,
    private val customTrackerFactories: Map<TrackerId, () -> Tracker> = emptyMap()
) : TrackerRegistry {

    private val instances = ConcurrentHashMap<TrackerId, Tracker>()

    override fun get(id: TrackerId): Tracker {
        return instances.computeIfAbsent(id) { key ->
            val customFactory = customTrackerFactories[key]
            if (customFactory != null) {
                return@computeIfAbsent customFactory()
            }

            when (key) {
                TrackerId.HAND_SINGLE -> HandSingleTracker(
                    context = context,
                    externalLifecycleOwner = externalLifecycleOwner,
                    previewSurfaceProvider = previewSurfaceProvider
                )
                TrackerId.HAND_DUAL_WHEEL -> HandDualWheelTracker(
                    context = context,
                    externalLifecycleOwner = externalLifecycleOwner,
                    previewSurfaceProvider = previewSurfaceProvider
                )
                TrackerId.POSE_LITE -> PoseLiteTracker(
                    context = context,
                    externalLifecycleOwner = externalLifecycleOwner,
                    previewSurfaceProvider = previewSurfaceProvider
                )
            }
        }
    }

    /**
     * Returns true if a tracker instance for [id] has already been instantiated.
     */
    fun isInstantiated(id: TrackerId): Boolean {
        return instances.containsKey(id)
    }

    /**
     * Stops and clears all cached tracker instances, releasing hardware and camera resources.
     */
    suspend fun releaseAll() {
        for ((_, tracker) in instances) {
            try {
                tracker.stop()
            } catch (_: Throwable) {
                // Ignore teardown errors during release
            }
        }
        instances.clear()
    }
}


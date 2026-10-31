package com.zorbi.vision.perf

import com.zorbi.core.PerfMode
import com.zorbi.core.Tracker
import com.zorbi.core.TrackerStats
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Performance controller that automatically drops tracker execution to [PerfMode.PERFORMANCE]
 * when inference latency exceeds the allocated budget (P1-4 / T-P1-4).
 *
 * Stabilization & Hysteresis:
 * - Drop condition: Triggered when p95 inference latency exceeds [p95BudgetMs] for
 *   [dropConsecutiveSamples] consecutive reports.
 * - Recovery condition: Reverts back to [targetMode] only when p95 drops below
 *   [recoveryThresholdMs] ([p95BudgetMs] * [recoveryRatio]) for [recoveryConsecutiveSamples]
 *   consecutive reports.
 * - Deadband: The region between [recoveryThresholdMs] and [p95BudgetMs] retains the current
 *   mode, preventing rapid oscillation ("chattering").
 *
 * @param initialMode The starting [PerfMode], default [PerfMode.QUALITY].
 * @param p95BudgetMs Maximum allowed p95 inference latency in milliseconds before dropping to PERFORMANCE.
 *                    Default is 80.0 ms (budget for ~15-20 FPS MediaPipe inference on budget SoC).
 * @param recoveryRatio Fraction of [p95BudgetMs] required to qualify for recovery. Default 0.75 (i.e. 60 ms).
 * @param dropConsecutiveSamples Number of consecutive over-budget samples required to drop mode. Default 2.
 * @param recoveryConsecutiveSamples Number of consecutive under-budget samples required to recover. Default 3.
 * @param onModeChanged Optional callback invoked whenever the active [PerfMode] changes.
 */
class PerfController(
    val initialMode: PerfMode = PerfMode.QUALITY,
    val p95BudgetMs: Float = DEFAULT_P95_BUDGET_MS,
    val recoveryRatio: Float = DEFAULT_RECOVERY_RATIO,
    val dropConsecutiveSamples: Int = DEFAULT_DROP_CONSECUTIVE_SAMPLES,
    val recoveryConsecutiveSamples: Int = DEFAULT_RECOVERY_CONSECUTIVE_SAMPLES,
    var onModeChanged: ((newMode: PerfMode, previousMode: PerfMode) -> Unit)? = null
) {
    companion object {
        const val DEFAULT_P95_BUDGET_MS = 80.0f
        const val DEFAULT_RECOVERY_RATIO = 0.75f
        const val DEFAULT_DROP_CONSECUTIVE_SAMPLES = 2
        const val DEFAULT_RECOVERY_CONSECUTIVE_SAMPLES = 3
    }

    init {
        require(p95BudgetMs > 0f) { "p95BudgetMs must be positive, got $p95BudgetMs" }
        require(recoveryRatio > 0f && recoveryRatio < 1.0f) {
            "recoveryRatio must be between 0 and 1, got $recoveryRatio"
        }
        require(dropConsecutiveSamples >= 1) {
            "dropConsecutiveSamples must be >= 1, got $dropConsecutiveSamples"
        }
        require(recoveryConsecutiveSamples >= 1) {
            "recoveryConsecutiveSamples must be >= 1, got $recoveryConsecutiveSamples"
        }
    }

    val recoveryThresholdMs: Float
        get() = p95BudgetMs * recoveryRatio

    private val _currentMode = MutableStateFlow(initialMode)
    val currentMode: StateFlow<PerfMode> = _currentMode.asStateFlow()

    @Volatile
    var targetMode: PerfMode = initialMode
        private set

    @Volatile
    var consecutiveOverBudget: Int = 0
        private set

    @Volatile
    var consecutiveUnderBudget: Int = 0
        private set

    val isOverloaded: Boolean
        get() = _currentMode.value == PerfMode.PERFORMANCE && targetMode != PerfMode.PERFORMANCE

    private val lock = Any()

    /**
     * Updates controller state based on newly reported [TrackerStats].
     *
     * @param stats Current tracker performance statistics.
     * @return The updated active [PerfMode].
     */
    fun onStatsUpdated(stats: TrackerStats): PerfMode {
        val p95 = stats.inferenceMsP95

        // Ignore zero/invalid stats reported before inference has begun
        if (p95 <= 0f) {
            return _currentMode.value
        }

        var modeToNotify: Pair<PerfMode, PerfMode>? = null

        synchronized(lock) {
            val current = _currentMode.value

            if (p95 > p95BudgetMs) {
                // Over budget: count towards drop to PERFORMANCE
                consecutiveOverBudget++
                consecutiveUnderBudget = 0

                if (consecutiveOverBudget >= dropConsecutiveSamples && current != PerfMode.PERFORMANCE) {
                    val previous = current
                    val newMode = PerfMode.PERFORMANCE
                    _currentMode.value = newMode
                    modeToNotify = newMode to previous
                }
            } else if (p95 < recoveryThresholdMs) {
                // Well under budget: count towards recovery to targetMode
                consecutiveUnderBudget++
                consecutiveOverBudget = 0

                if (current == PerfMode.PERFORMANCE && targetMode != PerfMode.PERFORMANCE) {
                    if (consecutiveUnderBudget >= recoveryConsecutiveSamples) {
                        val previous = current
                        val newMode = targetMode
                        _currentMode.value = newMode
                        modeToNotify = newMode to previous
                    }
                }
            } else {
                // In deadband: recoveryThresholdMs <= p95 <= p95BudgetMs
                // Reset consecutive counters to avoid accumulating split violations
                consecutiveOverBudget = 0
                consecutiveUnderBudget = 0
            }
        }

        modeToNotify?.let { (newMode, prevMode) ->
            onModeChanged?.invoke(newMode, prevMode)
        }

        return _currentMode.value
    }

    /**
     * Changes the desired baseline mode (e.g. [PerfMode.QUALITY] or [PerfMode.BALANCED]).
     * If not currently overloaded, the active mode updates immediately.
     */
    fun setTargetMode(mode: PerfMode) {
        var modeToNotify: Pair<PerfMode, PerfMode>? = null
        synchronized(lock) {
            targetMode = mode
            if (!isOverloaded && _currentMode.value != mode) {
                val prev = _currentMode.value
                _currentMode.value = mode
                modeToNotify = mode to prev
            }
        }
        modeToNotify?.let { (newMode, prevMode) ->
            onModeChanged?.invoke(newMode, prevMode)
        }
    }

    /**
     * Resets internal counters and restores mode to [newTargetMode].
     */
    fun reset(newTargetMode: PerfMode = initialMode) {
        var modeToNotify: Pair<PerfMode, PerfMode>? = null
        synchronized(lock) {
            consecutiveOverBudget = 0
            consecutiveUnderBudget = 0
            val prev = _currentMode.value
            targetMode = newTargetMode
            _currentMode.value = newTargetMode
            if (prev != newTargetMode) {
                modeToNotify = newTargetMode to prev
            }
        }
        modeToNotify?.let { (newMode, prevMode) ->
            onModeChanged?.invoke(newMode, prevMode)
        }
    }

    @Volatile
    private var activeJob: Job? = null

    @Volatile
    private var isTransitioning: Boolean = false

    /**
     * Attaches this controller to a [StateFlow] of [TrackerStats] in the given [CoroutineScope].
     * Cancels any previously active observation job to guarantee a single collector instance.
     */
    @Synchronized
    fun observe(statsFlow: StateFlow<TrackerStats>, scope: CoroutineScope): Job {
        activeJob?.cancel()
        val job = scope.launch {
            statsFlow.collect { stats ->
                onStatsUpdated(stats)
            }
        }
        activeJob = job
        return job
    }

    /**
     * Attaches this controller to an active [Tracker], automatically invoking [Tracker.start]
     * with the new [PerfMode] when overload or recovery occurs.
     * Cancels any previously active observation job to prevent duplicate collectors.
     */
    @Synchronized
    fun attachToTracker(tracker: Tracker, scope: CoroutineScope): Job {
        activeJob?.cancel()
        val job = scope.launch {
            tracker.stats.collect { stats ->
                val prev = _currentMode.value
                val next = onStatsUpdated(stats)
                if (next != prev && !isTransitioning) {
                    isTransitioning = true
                    try {
                        tracker.start(next)
                    } catch (t: Throwable) {
                        // Guard against camera restart errors or cancellation without crashing
                    } finally {
                        isTransitioning = false
                    }
                }
            }
        }
        activeJob = job
        return job
    }

    /**
     * Detaches and cancels active flow collection if currently attached.
     */
    @Synchronized
    fun detach() {
        activeJob?.cancel()
        activeJob = null
    }
}

package com.zorbi.fakes.p4

import com.zorbi.core.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.io.Reader

/**
 * ReplayTracker (P4-3)
 *
 * Implements [com.zorbi.core.Tracker] for offline testing, calibration, and gesture verification.
 * Loads and parses synthetic or recorded MediaPipe landmark JSONL fixtures according to the
 * frozen Section 5.1 contracts.
 */
class ReplayTracker(
    override val id: TrackerId = TrackerId.HAND_SINGLE,
    initialFrames: List<TrackerFrame> = emptyList()
) : Tracker {

    private val lock = Any()
    private val _loadedFrames = mutableListOf<TrackerFrame>()
    val loadedFrames: List<TrackerFrame> get() = synchronized(lock) { _loadedFrames.toList() }

    private val _frames = MutableSharedFlow<TrackerFrame>(replay = 1)
    override val frames: SharedFlow<TrackerFrame> = _frames

    private val _stats = MutableStateFlow(
        TrackerStats(fps = 30f, inferenceMsP50 = 0f, inferenceMsP95 = 0f, dropped = 0)
    )
    override val stats: StateFlow<TrackerStats> = _stats

    var isStarted: Boolean = false
        private set

    init {
        if (initialFrames.isNotEmpty()) {
            synchronized(lock) {
                _loadedFrames.addAll(initialFrames)
            }
        }
    }

    override suspend fun start(perf: PerfMode) {
        isStarted = true
    }

    override suspend fun stop() {
        isStarted = false
    }

    /** Emits a single frame to subscribers */
    suspend fun emitFrame(frame: TrackerFrame) {
        _frames.emit(frame)
    }

    /** Emits all loaded frames in sequence */
    suspend fun emitAll() {
        val framesSnapshot = synchronized(lock) { _loadedFrames.toList() }
        for (frame in framesSnapshot) {
            _frames.emit(frame)
        }
    }

    /** Updates the tracker stats state */
    fun updateStats(stats: TrackerStats) {
        _stats.value = stats
    }

    /** Loads frames from a raw JSONL text string */
    fun loadJsonl(text: String): List<TrackerFrame> {
        val lines = text.lineSequence().filter { it.isNotBlank() }
        val parsed = parseJsonlLines(lines)
        synchronized(lock) {
            _loadedFrames.clear()
            _loadedFrames.addAll(parsed)
        }
        return parsed
    }

    /** Loads frames from an [InputStream] (e.g. Android assets or file stream) */
    fun loadJsonl(inputStream: InputStream): List<TrackerFrame> {
        return loadJsonl(InputStreamReader(inputStream, Charsets.UTF_8))
    }

    /** Loads frames from a [Reader] */
    fun loadJsonl(reader: Reader): List<TrackerFrame> {
        val bufferedReader = reader as? BufferedReader ?: BufferedReader(reader)
        val lines = bufferedReader.lineSequence().filter { it.isNotBlank() }
        val parsed = parseJsonlLines(lines)
        synchronized(lock) {
            _loadedFrames.clear()
            _loadedFrames.addAll(parsed)
        }
        return parsed
    }

    companion object {
        /**
         * Parses a sequence of JSONL lines into validated [TrackerFrame] objects.
         * Validates monotonic timestamps, point counts (21 for hands, 33 for pose),
         * coordinate bounds [0, 1], and finite values.
         */
        fun parseJsonlLines(lines: Sequence<String>): List<TrackerFrame> {
            val result = mutableListOf<TrackerFrame>()
            var prevTsNanos = -1L

            for ((lineIndex, line) in lines.withIndex()) {
                val lineNum = lineIndex + 1
                val frame = parseJsonlLine(line, lineNum)

                if (frame.tsNanos <= prevTsNanos) {
                    throw IllegalArgumentException(
                        "Line $lineNum: Non-monotonic timestamp (prev=$prevTsNanos, curr=${frame.tsNanos})"
                    )
                }
                prevTsNanos = frame.tsNanos
                result.add(frame)
            }
            return result
        }

        /**
         * Parses a single JSONL line into a [TrackerFrame].
         */
        @Suppress("UNCHECKED_CAST")
        fun parseJsonlLine(line: String, lineNum: Int = 1): TrackerFrame {
            val parser = SimpleJsonParser(line)
            val root = try {
                val value = parser.parseValue()
                parser.ensureFullyConsumed()
                value as? Map<String, Any?>
                    ?: throw IllegalArgumentException("Expected JSON object at root")
            } catch (e: Exception) {
                throw IllegalArgumentException("Line $lineNum: Malformed JSON - ${e.message}", e)
            }

            val tsNanos = parseTimestampNanos(root["t"], lineNum)

            val trackerStr = root["tracker"] as? String
                ?: throw IllegalArgumentException("Line $lineNum: Missing or invalid 'tracker'")
            val trackerId = try {
                TrackerId.valueOf(trackerStr)
            } catch (e: IllegalArgumentException) {
                throw IllegalArgumentException("Line $lineNum: Unknown tracker identifier '$trackerStr'")
            }

            return when (trackerId) {
                TrackerId.HAND_SINGLE, TrackerId.HAND_DUAL_WHEEL -> {
                    val handsListRaw = root["hands"] as? List<*>
                        ?: throw IllegalArgumentException("Line $lineNum: Missing or invalid 'hands' array for $trackerId")

                    if (handsListRaw.isEmpty()) {
                        throw IllegalArgumentException("Line $lineNum: 'hands' array cannot be empty")
                    }
                    if (trackerId == TrackerId.HAND_SINGLE && handsListRaw.size != 1) {
                        throw IllegalArgumentException(
                            "Line $lineNum: HAND_SINGLE requires exactly 1 hand, found ${handsListRaw.size}"
                        )
                    }
                    if (trackerId == TrackerId.HAND_DUAL_WHEEL && handsListRaw.size != 2) {
                        throw IllegalArgumentException(
                            "Line $lineNum: HAND_DUAL_WHEEL requires exactly 2 hands, found ${handsListRaw.size}"
                        )
                    }

                    val parsedHands = handsListRaw.mapIndexed { hIdx, hObj ->
                        val hMap = hObj as? Map<String, Any?>
                            ?: throw IllegalArgumentException("Line $lineNum: Hand $hIdx must be an object")

                        val sideStr = hMap["side"] as? String
                            ?: throw IllegalArgumentException("Line $lineNum: Hand $hIdx missing 'side'")
                        val side = try {
                            Side.valueOf(sideStr)
                        } catch (e: IllegalArgumentException) {
                            throw IllegalArgumentException("Line $lineNum: Hand $hIdx invalid side '$sideStr'")
                        }

                        val scoreNum = hMap["score"] as? Number
                            ?: throw IllegalArgumentException("Line $lineNum: Hand $hIdx missing 'score'")
                        val score = scoreNum.toFloat()
                        validateFinite(score, "Hand $hIdx score", lineNum)

                        val lmRaw = hMap["lm"] as? List<*>
                            ?: throw IllegalArgumentException("Line $lineNum: Hand $hIdx missing 'lm' landmark array")
                        if (lmRaw.size != 21) {
                            throw IllegalArgumentException(
                                "Line $lineNum: Hand $hIdx landmark count must be exactly 21, found ${lmRaw.size}"
                            )
                        }

                        val points = lmRaw.mapIndexed { ptIdx, ptObj ->
                            parsePoint(ptObj, "Hand $hIdx Point $ptIdx", lineNum)
                        }
                        Hand(side = side, score = score, landmarks = points)
                    }
                    HandsFrame(tsNanos = tsNanos, hands = parsedHands)
                }
                TrackerId.POSE_LITE -> {
                    val scoreNum = root["score"] as? Number
                        ?: throw IllegalArgumentException("Line $lineNum: Missing or invalid pose 'score'")
                    val score = scoreNum.toFloat()
                    validateFinite(score, "Pose score", lineNum)

                    val lmRaw = root["lm"] as? List<*>
                        ?: throw IllegalArgumentException("Line $lineNum: Missing or invalid pose 'lm' landmark array")
                    if (lmRaw.size != 33) {
                        throw IllegalArgumentException(
                            "Line $lineNum: Pose landmark count must be exactly 33, found ${lmRaw.size}"
                        )
                    }

                    val points = lmRaw.mapIndexed { ptIdx, ptObj ->
                        parsePoint(ptObj, "Pose Point $ptIdx", lineNum)
                    }
                    PoseFrame(tsNanos = tsNanos, score = score, landmarks = points)
                }
            }
        }

        private fun parsePoint(ptObj: Any?, context: String, lineNum: Int): Pt {
            val ptMap = ptObj as? Map<String, Any?>
                ?: throw IllegalArgumentException("Line $lineNum: $context must be a JSON object")

            val xNum = ptMap["x"] as? Number
                ?: throw IllegalArgumentException("Line $lineNum: $context missing coordinate 'x'")
            val yNum = ptMap["y"] as? Number
                ?: throw IllegalArgumentException("Line $lineNum: $context missing coordinate 'y'")
            val zNum = (ptMap["z"] as? Number) ?: 0.0

            val x = xNum.toFloat()
            val y = yNum.toFloat()
            val z = zNum.toFloat()

            validateFinite(x, "$context x", lineNum)
            validateFinite(y, "$context y", lineNum)
            validateFinite(z, "$context z", lineNum)

            if (x < 0.0f || x > 1.0f || y < 0.0f || y > 1.0f) {
                throw IllegalArgumentException(
                    "Line $lineNum: $context coordinates out of [0, 1] bounds (x=$x, y=$y)"
                )
            }
            return Pt(x = x, y = y, z = z)
        }

        private fun validateFinite(valFloat: Float, context: String, lineNum: Int) {
            if (valFloat.isNaN() || valFloat.isInfinite()) {
                throw IllegalArgumentException("Line $lineNum: $context must be a finite number, found $valFloat")
            }
        }

        /**
         * Validates and extracts a strict monotonic nanosecond timestamp.
         *
         * Enforces:
         * - Non-null, numeric JSON value.
         * - Exact integer-token representation (rejects fractional values, decimal tokens, and scientific notation).
         * - Non-negative value (>= 0).
         * - Range bounded by 64-bit signed Long (0 .. Long.MAX_VALUE).
         */
        fun parseTimestampNanos(raw: Any?, lineNum: Int): Long {
            if (raw == null) {
                throw IllegalArgumentException("Line $lineNum: Missing or invalid timestamp 't'")
            }
            if (raw !is Number) {
                throw IllegalArgumentException("Line $lineNum: Missing or invalid timestamp 't' (expected numeric integer, got ${raw::class.simpleName})")
            }

            val bigInt: java.math.BigInteger = when (raw) {
                is Long -> java.math.BigInteger.valueOf(raw)
                is Int, is Short, is Byte -> java.math.BigInteger.valueOf(raw.toLong())
                is java.math.BigInteger -> raw
                is java.math.BigDecimal -> {
                    val exact = try {
                        raw.toBigIntegerExact()
                    } catch (e: ArithmeticException) {
                        throw IllegalArgumentException("Line $lineNum: Timestamp 't' cannot be a fractional number ($raw)", e)
                    }
                    if (raw.scale() != 0) {
                        throw IllegalArgumentException("Line $lineNum: Timestamp 't' must be an integer token, not decimal/exponent notation ($raw)")
                    }
                    exact
                }
                is Double, is Float -> {
                    val bd = try {
                        java.math.BigDecimal(raw.toString())
                    } catch (e: Exception) {
                        throw IllegalArgumentException("Line $lineNum: Timestamp 't' invalid floating-point value ($raw)")
                    }
                    val exact = try {
                        bd.toBigIntegerExact()
                    } catch (e: ArithmeticException) {
                        throw IllegalArgumentException("Line $lineNum: Timestamp 't' cannot be a fractional number ($raw)", e)
                    }
                    throw IllegalArgumentException("Line $lineNum: Timestamp 't' must be an integer token, not floating point ($raw)")
                }
                else -> throw IllegalArgumentException("Line $lineNum: Unsupported numeric type for timestamp 't': ${raw::class.simpleName}")
            }

            if (bigInt < java.math.BigInteger.ZERO) {
                throw IllegalArgumentException("Line $lineNum: Timestamp 't' cannot be negative ($bigInt)")
            }
            if (bigInt > java.math.BigInteger.valueOf(Long.MAX_VALUE)) {
                throw IllegalArgumentException("Line $lineNum: Timestamp 't' ($bigInt) is outside supported Long range (max: ${Long.MAX_VALUE})")
            }

            return bigInt.toLong()
        }
    }
}

/**
 * Lightweight, zero-dependency pure Kotlin JSON parser.
 * Supports standard JSON types without requiring Android `org.json` or external serialization libraries.
 */
internal class SimpleJsonParser(private val src: String) {
    private var idx = 0

    fun parseValue(): Any? {
        skipWhitespace()
        if (idx >= src.length) throw IllegalArgumentException("Unexpected EOF")
        return when (val ch = src[idx]) {
            '{' -> parseObject()
            '[' -> parseArray()
            '"' -> parseString()
            't', 'f' -> parseBoolean()
            'n' -> parseNull()
            '-', in '0'..'9' -> parseNumber()
            else -> throw IllegalArgumentException("Unexpected character '$ch' at index $idx")
        }
    }

    /**
     * Ensures all non-whitespace content in the source input has been consumed.
     * Rejects any trailing non-whitespace characters, additional JSON tokens, or garbage.
     */
    fun ensureFullyConsumed() {
        skipWhitespace()
        if (idx < src.length) {
            val trailingSnippet = if (src.length - idx > 30) src.substring(idx, idx + 30) + "..." else src.substring(idx)
            throw IllegalArgumentException("Unexpected trailing content after JSON value at index $idx: '$trailingSnippet'")
        }
    }

    private fun skipWhitespace() {
        while (idx < src.length && src[idx].isWhitespace()) {
            idx++
        }
    }

    private fun parseObject(): Map<String, Any?> {
        match('{')
        val map = mutableMapOf<String, Any?>()
        skipWhitespace()
        if (idx < src.length && src[idx] == '}') {
            idx++
            return map
        }
        var closed = false
        while (idx < src.length) {
            skipWhitespace()
            val key = parseString()
            skipWhitespace()
            match(':')
            val value = parseValue()
            map[key] = value
            skipWhitespace()
            if (idx < src.length && src[idx] == ',') {
                idx++
            } else if (idx < src.length && src[idx] == '}') {
                idx++
                closed = true
                break
            } else {
                throw IllegalArgumentException("Expected ',' or '}' at index $idx")
            }
        }
        if (!closed) {
            throw IllegalArgumentException("Unclosed JSON object, expected '}'")
        }
        return map
    }

    private fun parseArray(): List<Any?> {
        match('[')
        val list = mutableListOf<Any?>()
        skipWhitespace()
        if (idx < src.length && src[idx] == ']') {
            idx++
            return list
        }
        var closed = false
        while (idx < src.length) {
            val value = parseValue()
            list.add(value)
            skipWhitespace()
            if (idx < src.length && src[idx] == ',') {
                idx++
            } else if (idx < src.length && src[idx] == ']') {
                idx++
                closed = true
                break
            } else {
                throw IllegalArgumentException("Expected ',' or ']' at index $idx")
            }
        }
        if (!closed) {
            throw IllegalArgumentException("Unclosed JSON array, expected ']'")
        }
        return list
    }

    private fun parseString(): String {
        match('"')
        val sb = StringBuilder()
        while (idx < src.length) {
            val ch = src[idx++]
            if (ch == '"') return sb.toString()
            if (ch == '\\') {
                if (idx >= src.length) throw IllegalArgumentException("Unterminated escape")
                when (val esc = src[idx++]) {
                    '"' -> sb.append('"')
                    '\\' -> sb.append('\\')
                    '/' -> sb.append('/')
                    'b' -> sb.append('\b')
                    'f' -> sb.append('\u000c')
                    'n' -> sb.append('\n')
                    'r' -> sb.append('\r')
                    't' -> sb.append('\t')
                    'u' -> {
                        val hex = src.substring(idx, idx + 4)
                        idx += 4
                        sb.append(hex.toInt(16).toChar())
                    }
                    else -> sb.append(esc)
                }
            } else {
                sb.append(ch)
            }
        }
        throw IllegalArgumentException("Unterminated string")
    }

    private fun parseNumber(): Number {
        val start = idx
        if (src[idx] == '-') idx++
        while (idx < src.length && (src[idx].isDigit() || src[idx] == '.' || src[idx] == 'e' || src[idx] == 'E' || src[idx] == '+' || src[idx] == '-')) {
            idx++
        }
        val numStr = src.substring(start, idx)
        return if (numStr.contains('.') || numStr.contains('e') || numStr.contains('E')) {
            java.math.BigDecimal(numStr)
        } else {
            try {
                numStr.toLong()
            } catch (e: NumberFormatException) {
                java.math.BigInteger(numStr)
            }
        }
    }

    private fun parseBoolean(): Boolean {
        return if (src.startsWith("true", idx)) {
            idx += 4
            true
        } else if (src.startsWith("false", idx)) {
            idx += 5
            false
        } else {
            throw IllegalArgumentException("Invalid boolean at index $idx")
        }
    }

    private fun parseNull(): Any? {
        if (src.startsWith("null", idx)) {
            idx += 4
            return null
        }
        throw IllegalArgumentException("Invalid null at index $idx")
    }

    private fun match(expected: Char) {
        skipWhitespace()
        if (idx >= src.length || src[idx] != expected) {
            throw IllegalArgumentException("Expected '$expected' at index $idx")
        }
        idx++
    }
}

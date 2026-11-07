package com.zorbi.calib

import com.google.common.truth.Truth.assertThat
import com.zorbi.core.Calibration
import com.zorbi.core.Side
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * Unit tests for [RealCalibrationRepository] persistence (Person 1 / P1-7).
 */
class RealCalibrationRepositoryTest {

    private lateinit var tempDir: File
    private lateinit var repository: RealCalibrationRepository

    @Before
    fun setUp() {
        tempDir = Files.createTempDirectory("zorbi_calib_test").toFile()
        repository = RealCalibrationRepository(tempDir)
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    // 1. Save then load
    @Test
    fun saveThenLoad_returnsExactCalibration() = runTest {
        val original = Calibration(
            neutralAngleDeg = 2.5f,
            maxAngleDeg = 38.0f,
            reachScaleX = 0.75f,
            reachScaleY = 0.65f,
            dominantHand = Side.RIGHT,
            createdAtEpochMs = 1700000000000L
        )

        repository.save("com.example.racing", original)
        val loaded = repository.load("com.example.racing")

        assertThat(loaded).isNotNull()
        assertThat(loaded).isEqualTo(original)
    }

    // 2. Missing file returns null
    @Test
    fun load_missingFile_returnsNull() = runTest {
        val loaded = repository.load("com.nonexistent.app")
        assertThat(loaded).isNull()
    }

    // 3. Corrupted JSON returns null safely
    @Test
    fun load_corruptedJson_returnsNullGracefully() = runTest {
        val pkg = "com.corrupted.app"
        val file = File(tempDir, "$pkg.json")
        file.writeText("{ this is definitely not valid json syntax ...", Charsets.UTF_8)

        val loaded = repository.load(pkg)
        assertThat(loaded).isNull()
    }

    // 4. Invalid package name rejection
    @Test
    fun saveAndLoad_invalidPackageName_rejected() = runTest {
        val cal = Calibration(
            neutralAngleDeg = 0f,
            maxAngleDeg = 35f,
            reachScaleX = 0.8f,
            reachScaleY = 0.8f,
            dominantHand = Side.RIGHT,
            createdAtEpochMs = 1000L
        )

        // Path traversal attempts must throw IllegalArgumentException
        var threw1 = false
        try {
            repository.save("../traversal", cal)
        } catch (e: IllegalArgumentException) {
            threw1 = true
        }
        assertThat(threw1).isTrue()

        var threw2 = false
        try {
            repository.save("bad/path/pkg", cal)
        } catch (e: IllegalArgumentException) {
            threw2 = true
        }
        assertThat(threw2).isTrue()

        var threw3 = false
        try {
            repository.save("", cal)
        } catch (e: IllegalArgumentException) {
            threw3 = true
        }
        assertThat(threw3).isTrue()

        // Load with invalid names returns null
        assertThat(repository.load("../traversal")).isNull()
        assertThat(repository.load("bad/slash")).isNull()
        assertThat(repository.load("")).isNull()
    }

    // 5. Atomic-write behavior
    @Test
    fun save_usesAtomicMove_leavesNoTempFiles() = runTest {
        val cal = Calibration(
            neutralAngleDeg = -1.2f,
            maxAngleDeg = 32.5f,
            reachScaleX = 0.7f,
            reachScaleY = 0.7f,
            dominantHand = Side.LEFT,
            createdAtEpochMs = 123456789L
        )

        repository.save("com.atomic.test", cal)

        val files = tempDir.listFiles() ?: emptyArray()
        // Exactly one .json file must exist
        assertThat(files.map { it.name }).containsExactly("com.atomic.test.json")

        // No .tmp files left over
        val tmpFiles = files.filter { it.name.contains(".tmp") }
        assertThat(tmpFiles).isEmpty()
    }

    // 6. Multiple package names do not overwrite one another
    @Test
    fun multiplePackages_isolatedCorrectly() = runTest {
        val cal1 = Calibration(0f, 35f, 0.8f, 0.8f, Side.RIGHT, 100L)
        val cal2 = Calibration(5f, 40f, 0.6f, 0.6f, Side.LEFT, 200L)

        repository.save("com.game.alpha", cal1)
        repository.save("com.game.beta", cal2)

        val loadedAlpha = repository.load("com.game.alpha")
        val loadedBeta = repository.load("com.game.beta")

        assertThat(loadedAlpha).isEqualTo(cal1)
        assertThat(loadedBeta).isEqualTo(cal2)
    }

    // 7. Save assigns valid epoch-millisecond timestamp when given sentinel 0L
    @Test
    fun save_withSentinelZeroEpoch_assignsActualSystemEpochMs() = runTest {
        // Calibration computed from pure calculation has sentinel 0L
        val computed = Calibration(
            neutralAngleDeg = 0f,
            maxAngleDeg = 35f,
            reachScaleX = 0.8f,
            reachScaleY = 0.8f,
            dominantHand = Side.RIGHT,
            createdAtEpochMs = 0L
        )

        val beforeEpochMs = System.currentTimeMillis()
        repository.save("com.epoch.actual", computed)
        val afterEpochMs = System.currentTimeMillis()

        val loaded = repository.load("com.epoch.actual")
        assertThat(loaded).isNotNull()
        assertThat(loaded!!.createdAtEpochMs).isAtLeast(beforeEpochMs)
        assertThat(loaded.createdAtEpochMs).isAtMost(afterEpochMs)
    }

    // 8. Save with clock injection assigns expected epoch timestamp
    @Test
    fun save_withClockInjection_assignsExactClockTimestamp() = runTest {
        val expectedEpochMs = 1730477000000L
        val customRepo = RealCalibrationRepository(
            storageDir = tempDir,
            clock = { expectedEpochMs }
        )

        val computed = Calibration(
            neutralAngleDeg = 0f,
            maxAngleDeg = 35f,
            reachScaleX = 0.8f,
            reachScaleY = 0.8f,
            dominantHand = Side.RIGHT,
            createdAtEpochMs = 0L
        )

        customRepo.save("com.epoch.injected", computed)
        val loaded = customRepo.load("com.epoch.injected")

        assertThat(loaded).isNotNull()
        assertThat(loaded!!.createdAtEpochMs).isEqualTo(expectedEpochMs)
    }

    // 9. Load preserves persisted epoch timestamp
    @Test
    fun load_preservesPersistedEpochMs() = runTest {
        val originalEpoch = 1699999999999L
        val cal = Calibration(
            neutralAngleDeg = 1.0f,
            maxAngleDeg = 30.0f,
            reachScaleX = 0.5f,
            reachScaleY = 0.5f,
            dominantHand = Side.RIGHT,
            createdAtEpochMs = originalEpoch
        )

        repository.save("com.preserve.test", cal)

        val load1 = repository.load("com.preserve.test")
        val load2 = repository.load("com.preserve.test")

        assertThat(load1).isNotNull()
        assertThat(load2).isNotNull()
        assertThat(load1!!.createdAtEpochMs).isEqualTo(originalEpoch)
        assertThat(load2!!.createdAtEpochMs).isEqualTo(originalEpoch)
    }
}

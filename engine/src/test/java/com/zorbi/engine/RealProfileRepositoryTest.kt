package com.zorbi.engine

import com.google.common.truth.Truth.assertThat
import com.zorbi.core.EngineState
import com.zorbi.core.GameProfile
import com.zorbi.core.MappingType
import com.zorbi.core.PerfMode
import com.zorbi.core.ProfileOrientation
import com.zorbi.core.ProfileParams
import com.zorbi.core.Pt
import com.zorbi.core.TargetPoint
import com.zorbi.core.TrackerId
import com.zorbi.fakes.p1.FakeCalibrationRepository
import com.zorbi.fakes.p1.FakeTracker
import com.zorbi.fakes.p1.FakeTrackerRegistry
import com.zorbi.fakes.p2.FakeInputInjector
import java.io.File
import java.io.IOException
import java.nio.file.Files
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Unit test suite for [RealProfileRepository] covering Test Gate T-P2-6.
 *
 * Verifies:
 * - Parsing and loading of all 3 bundled demo profiles from physical assets.
 * - Package-name lookup and safe path traversal prevention.
 * - JSON serialization / deserialization round-trip fidelity.
 * - Validation of schema versions, target coordinates, and profile parameters.
 * - Safe rejection and fallback handling for malformed / unknown local profiles.
 * - Atomic persistence with user-overrides taking precedence over bundled defaults.
 * - Offline functionality and graceful cloud import failure recovery.
 * - End-to-end integration with [RealEngineController].
 */
class RealProfileRepositoryTest {

    private lateinit var tempStorageDir: File
    private lateinit var assetDir: File
    private lateinit var assetProvider: DirectoryAssetProvider
    private lateinit var repository: RealProfileRepository

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        prettyPrint = true
        encodeDefaults = true
    }

    @Before
    fun setUp() {
        tempStorageDir = Files.createTempDirectory("zorbi_profile_test_").toFile()

        assetDir = when {
            File("src/main/assets").exists() -> File("src/main/assets")
            File("engine/src/main/assets").exists() -> File("engine/src/main/assets")
            else -> File(System.getProperty("user.dir"), "src/main/assets")
        }
        assertThat(assetDir.exists()).isTrue()

        assetProvider = DirectoryAssetProvider(assetDir)
        repository = RealProfileRepository(
            assetProvider = assetProvider,
            storageDir = tempStorageDir,
            remoteSource = null,
            json = json
        )
    }

    @After
    fun tearDown() {
        tempStorageDir.deleteRecursively()
    }

    @Test
    fun test1_loadBundledSubwaySurfersProfile() = runTest {
        val profile = repository.get("com.kiloo.subwaysurf")

        assertThat(profile).isNotNull()
        assertThat(profile!!.schemaVersion).isEqualTo(1)
        assertThat(profile.packageName).isEqualTo("com.kiloo.subwaysurf")
        assertThat(profile.displayName).isEqualTo("Subway Surfers")
        assertThat(profile.orientation).isEqualTo(ProfileOrientation.PORTRAIT)
        assertThat(profile.tracker).isEqualTo(TrackerId.HAND_SINGLE)
        assertThat(profile.mapping).isEqualTo(MappingType.LANE_SWIPE)
        assertThat(profile.targets).containsKey("left")
        assertThat(profile.targets).containsKey("right")
        assertThat(profile.targets).containsKey("center")
        assertThat(profile.params.swipeMinSpeed).isGreaterThan(0f)
    }

    @Test
    fun test2_loadBundledFruitNinjaProfile() = runTest {
        val profile = repository.get("com.halfbrick.fruitninjafree")

        assertThat(profile).isNotNull()
        assertThat(profile!!.schemaVersion).isEqualTo(1)
        assertThat(profile.packageName).isEqualTo("com.halfbrick.fruitninjafree")
        assertThat(profile.displayName).isEqualTo("Fruit Ninja")
        assertThat(profile.orientation).isEqualTo(ProfileOrientation.LANDSCAPE)
        assertThat(profile.tracker).isEqualTo(TrackerId.HAND_SINGLE)
        assertThat(profile.mapping).isEqualTo(MappingType.SLASH)
        assertThat(profile.params.slashMinLength).isGreaterThan(0f)
    }

    @Test
    fun test3_loadBundledBeachBuggyProfile() = runTest {
        val profile = repository.get("com.vectorunit.purple.googleplay")

        assertThat(profile).isNotNull()
        assertThat(profile!!.schemaVersion).isEqualTo(1)
        assertThat(profile.packageName).isEqualTo("com.vectorunit.purple.googleplay")
        assertThat(profile.displayName).isEqualTo("Beach Buggy Racing")
        assertThat(profile.orientation).isEqualTo(ProfileOrientation.LANDSCAPE)
        assertThat(profile.tracker).isEqualTo(TrackerId.HAND_DUAL_WHEEL)
        assertThat(profile.mapping).isEqualTo(MappingType.STEER_BUTTONS)
        assertThat(profile.targets).containsKey("left")
        assertThat(profile.targets).containsKey("right")
        assertThat(profile.params.deadzoneDeg).isGreaterThan(0f)
    }

    @Test
    fun test4_packageLookup_returnsProfile_orNullIfUnregistered() = runTest {
        val unknown = repository.get("com.unknown.nonexistent.game")
        assertThat(unknown).isNull()

        val empty = repository.get("")
        assertThat(empty).isNull()
    }

    @Test
    fun test5_jsonSerialization_roundTripEquality() {
        val original = GameProfile(
            schemaVersion = 1,
            packageName = "com.test.roundtrip",
            displayName = "Roundtrip Test",
            orientation = ProfileOrientation.PORTRAIT,
            tracker = TrackerId.HAND_SINGLE,
            mapping = MappingType.LANE_SWIPE,
            targets = mapOf("center" to TargetPoint(0.5f, 0.5f)),
            params = ProfileParams(cooldownMs = 400L, swipeMinSpeed = 1.5f)
        )

        val serialized = json.encodeToString(original)
        val decoded = json.decodeFromString<GameProfile>(serialized)

        assertThat(decoded).isEqualTo(original)
    }

    @Test
    fun test6_invalidProfile_blankPackage_rejected() = runTest {
        val blankPkgProfile = GameProfile(
            schemaVersion = 1,
            packageName = "",
            displayName = "Invalid Blank",
            orientation = ProfileOrientation.PORTRAIT,
            tracker = TrackerId.HAND_SINGLE,
            mapping = MappingType.LANE_SWIPE
        )

        assertThat(ProfileValidator.isValid(blankPkgProfile)).isFalse()
        try {
            repository.saveLocal(blankPkgProfile)
            error("Should have failed validation")
        } catch (e: IllegalArgumentException) {
            assertThat(e.message).contains("failed validation")
        }

        // Test path traversal prevention
        val traversalPkg = "../../../evil"
        assertThat(ProfileValidator.isValidPackageName(traversalPkg)).isFalse()
    }

    @Test
    fun test7_unsupportedSchemaVersion_rejectedGracefully() = runTest {
        // Create a local file with schemaVersion = 2
        val futureProfileJson = """
        {
          "schemaVersion": 2,
          "packageName": "com.test.future",
          "displayName": "Future Game",
          "orientation": "PORTRAIT",
          "tracker": "HAND_SINGLE",
          "mapping": "LANE_SWIPE",
          "targets": {},
          "params": {}
        }
        """.trimIndent()

        File(tempStorageDir, "com.test.future.json").writeText(futureProfileJson)

        // Must reject gracefully by returning null, never crashing
        val loaded = repository.get("com.test.future")
        assertThat(loaded).isNull()
    }

    @Test
    fun test8_outOfBoundsTargets_rejected() {
        val outOfBounds = GameProfile(
            schemaVersion = 1,
            packageName = "com.test.oob",
            displayName = "Out of Bounds",
            orientation = ProfileOrientation.PORTRAIT,
            tracker = TrackerId.HAND_SINGLE,
            mapping = MappingType.LANE_SWIPE,
            targets = mapOf("bad" to TargetPoint(1.2f, 0.5f))
        )

        assertThat(ProfileValidator.isValid(outOfBounds)).isFalse()
    }

    @Test
    fun test9_invalidParameters_rejected() {
        val negativeCooldown = GameProfile(
            schemaVersion = 1,
            packageName = "com.test.badparam",
            displayName = "Bad Param",
            orientation = ProfileOrientation.PORTRAIT,
            tracker = TrackerId.HAND_SINGLE,
            mapping = MappingType.LANE_SWIPE,
            params = ProfileParams(cooldownMs = -100L)
        )
        assertThat(ProfileValidator.isValid(negativeCooldown)).isFalse()

        val negativeSpeed = negativeCooldown.copy(
            params = ProfileParams(swipeMinSpeed = -1.0f)
        )
        assertThat(ProfileValidator.isValid(negativeSpeed)).isFalse()

        val invalidDeadzone = negativeCooldown.copy(
            params = ProfileParams(deadzoneDeg = 120.0f)
        )
        assertThat(ProfileValidator.isValid(invalidDeadzone)).isFalse()
    }

    @Test
    fun test10_missingTargets_handledSafely() {
        val noTargets = GameProfile(
            schemaVersion = 1,
            packageName = "com.test.notargets",
            displayName = "No Targets",
            orientation = ProfileOrientation.LANDSCAPE,
            tracker = TrackerId.HAND_SINGLE,
            mapping = MappingType.SLASH,
            targets = emptyMap()
        )
        assertThat(ProfileValidator.isValid(noTargets)).isTrue()
    }

    @Test
    fun test11_saveLocal_persistsProfile_overridesBundled() = runTest {
        val originalSubway = repository.get("com.kiloo.subwaysurf")
        assertThat(originalSubway!!.displayName).isEqualTo("Subway Surfers")

        // Save customized local override
        val customized = originalSubway.copy(
            displayName = "Customized Subway Surfers",
            params = originalSubway.params.copy(cooldownMs = 500L)
        )
        repository.saveLocal(customized)

        // get() should now return the customized override
        val reloaded = repository.get("com.kiloo.subwaysurf")
        assertThat(reloaded).isNotNull()
        assertThat(reloaded!!.displayName).isEqualTo("Customized Subway Surfers")
        assertThat(reloaded.params.cooldownMs).isEqualTo(500L)
    }

    @Test
    fun test12_list_mergesBundledAndLocalProfiles() = runTest {
        val initialList = repository.list()
        // At least the 3 bundled profiles
        assertThat(initialList.size).isAtLeast(3)
        val packageNames = initialList.map { it.packageName }
        assertThat(packageNames).contains("com.kiloo.subwaysurf")
        assertThat(packageNames).contains("com.halfbrick.fruitninjafree")
        assertThat(packageNames).contains("com.vectorunit.purple.googleplay")

        // Add a 4th custom profile
        val newProfile = GameProfile(
            schemaVersion = 1,
            packageName = "com.test.newgame",
            displayName = "New Game",
            orientation = ProfileOrientation.PORTRAIT,
            tracker = TrackerId.HAND_SINGLE,
            mapping = MappingType.LANE_SWIPE
        )
        repository.saveLocal(newProfile)

        val updatedList = repository.list()
        assertThat(updatedList.map { it.packageName }).contains("com.test.newgame")
        assertThat(updatedList.size).isEqualTo(initialList.size + 1)
    }

    @Test
    fun test13_offlineMode_functionsWithoutNetwork() = runTest {
        val offlineRepo = RealProfileRepository(
            assetProvider = assetProvider,
            storageDir = tempStorageDir,
            remoteSource = null
        )

        val subway = offlineRepo.get("com.kiloo.subwaysurf")
        assertThat(subway).isNotNull()

        val list = offlineRepo.list()
        assertThat(list).isNotEmpty()
    }

    @Test
    fun test14_importFromCloud_success() = runTest {
        val cloudProfile = GameProfile(
            schemaVersion = 1,
            packageName = "com.community.game",
            displayName = "Community Game",
            orientation = ProfileOrientation.PORTRAIT,
            tracker = TrackerId.HAND_SINGLE,
            mapping = MappingType.LANE_SWIPE
        )

        val fakeRemoteSource = RemoteProfileSource { id ->
            if (id == "valid-uuid") cloudProfile else null
        }

        val cloudRepo = RealProfileRepository(
            assetProvider = assetProvider,
            storageDir = tempStorageDir,
            remoteSource = fakeRemoteSource
        )

        val imported = cloudRepo.importFromCloud("valid-uuid")
        assertThat(imported).isNotNull()
        assertThat(imported!!.packageName).isEqualTo("com.community.game")

        // Verify it was persisted locally
        val local = cloudRepo.get("com.community.game")
        assertThat(local).isNotNull()
        assertThat(local!!.displayName).isEqualTo("Community Game")
    }

    @Test
    fun test15_importFromCloud_networkFailure_returnsNullSafely() = runTest {
        val failingRemoteSource = RemoteProfileSource {
            throw IOException("Network unreachable (503)")
        }

        val cloudRepo = RealProfileRepository(
            assetProvider = assetProvider,
            storageDir = tempStorageDir,
            remoteSource = failingRemoteSource
        )

        val result = cloudRepo.importFromCloud("some-id")
        assertThat(result).isNull()
    }

    @Test
    fun test16_engineIntegration_loadsProfileAndRunsPipeline() = runTest {
        val fakeSingle = FakeTracker(TrackerId.HAND_SINGLE)
        val fakeDual = FakeTracker(TrackerId.HAND_DUAL_WHEEL)
        val registry = FakeTrackerRegistry(
            mapOf(
                TrackerId.HAND_SINGLE to fakeSingle,
                TrackerId.HAND_DUAL_WHEEL to fakeDual
            )
        )
        val injector = FakeInputInjector()
        val calib = FakeCalibrationRepository()

        val engine = RealEngineController(
            trackerRegistry = registry,
            profileRepository = repository,
            calibrationRepository = calib,
            inputInjector = injector
        )

        // 1. Subway Surfers
        engine.start("com.kiloo.subwaysurf", PerfMode.BALANCED)
        assertThat(engine.state.value).isEqualTo(EngineState.RUNNING)
        assertThat(fakeSingle.isStarted).isTrue()
        engine.stop()
        assertThat(engine.state.value).isEqualTo(EngineState.IDLE)
        assertThat(fakeSingle.isStarted).isFalse()

        // 2. Fruit Ninja
        engine.start("com.halfbrick.fruitninjafree", PerfMode.QUALITY)
        assertThat(engine.state.value).isEqualTo(EngineState.RUNNING)
        assertThat(fakeSingle.isStarted).isTrue()
        engine.stop()

        // 3. Beach Buggy Racing
        engine.start("com.vectorunit.purple.googleplay", PerfMode.PERFORMANCE)
        assertThat(engine.state.value).isEqualTo(EngineState.RUNNING)
        assertThat(fakeDual.isStarted).isTrue()
        engine.stop()
    }

    @Test
    fun test17_corruptLocalFile_fallsBackToBundledProfile() = runTest {
        // Corrupt the local profile for Subway Surfers
        val localSubway = File(tempStorageDir, "com.kiloo.subwaysurf.json")
        localSubway.writeText("{ this is totally corrupt json !!! }")

        // Should not crash, and should fall back to bundled Subway Surfers profile
        val profile = repository.get("com.kiloo.subwaysurf")
        assertThat(profile).isNotNull()
        assertThat(profile!!.packageName).isEqualTo("com.kiloo.subwaysurf")
        assertThat(profile.displayName).isEqualTo("Subway Surfers")
    }
}


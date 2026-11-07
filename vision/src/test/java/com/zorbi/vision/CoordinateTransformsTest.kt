package com.zorbi.vision

import com.google.common.truth.Truth.assertThat
import com.zorbi.core.Pt
import com.zorbi.core.Side
import com.zorbi.vision.internal.CoordinateTransforms
import com.zorbi.vision.internal.TrackerStatsCollector
import org.junit.Test

class CoordinateTransformsTest {

    @Test
    fun toSide_mapsValidMediaPipeLabelsCorrectly() {
        assertThat(CoordinateTransforms.toSide("Left")).isEqualTo(Side.LEFT)
        assertThat(CoordinateTransforms.toSide("left")).isEqualTo(Side.LEFT)
        assertThat(CoordinateTransforms.toSide(" LEFT ")).isEqualTo(Side.LEFT)

        assertThat(CoordinateTransforms.toSide("Right")).isEqualTo(Side.RIGHT)
        assertThat(CoordinateTransforms.toSide("right")).isEqualTo(Side.RIGHT)
        assertThat(CoordinateTransforms.toSide(" RIGHT ")).isEqualTo(Side.RIGHT)
    }

    @Test
    fun toSide_mapsUnknownOrNullToUnknown() {
        assertThat(CoordinateTransforms.toSide(null)).isEqualTo(Side.UNKNOWN)
        assertThat(CoordinateTransforms.toSide("")).isEqualTo(Side.UNKNOWN)
        assertThat(CoordinateTransforms.toSide("Unknown")).isEqualTo(Side.UNKNOWN)
        assertThat(CoordinateTransforms.toSide("Both")).isEqualTo(Side.UNKNOWN)
    }

    @Test
    fun toPt_mirrorsHorizontallyForFrontCamera() {
        val original = Pt(x = 0.2f, y = 0.4f, z = 0.1f)
        val transformed = CoordinateTransforms.toPt(original.x, original.y, original.z, mirrorHorizontally = true)

        assertThat(transformed.x).isWithin(1e-5f).of(0.8f)
        assertThat(transformed.y).isWithin(1e-5f).of(0.4f)
        assertThat(transformed.z).isWithin(1e-5f).of(0.1f)
    }

    @Test
    fun toPt_centerCoordinateRemainsCenteredWhenMirrored() {
        val center = Pt(x = 0.5f, y = 0.5f, z = 0f)
        val transformed = CoordinateTransforms.toPt(center.x, center.y, center.z, mirrorHorizontally = true)

        assertThat(transformed.x).isWithin(1e-5f).of(0.5f)
        assertThat(transformed.y).isWithin(1e-5f).of(0.5f)
        assertThat(transformed.z).isWithin(1e-5f).of(0f)
    }

    @Test
    fun toPt_clampsCoordinatesWithinNormalizedBounds() {
        val outOfBounds = CoordinateTransforms.toPt(x = 1.2f, y = -0.3f, z = 0f, mirrorHorizontally = false)
        assertThat(outOfBounds.x).isEqualTo(1.0f)
        assertThat(outOfBounds.y).isEqualTo(0.0f)
    }

    @Test
    fun toCorePoints_transforms21LandmarkListPreservingCount() {
        val rawPoints = List(21) { i -> Pt(x = i / 20f, y = i / 20f, z = 0f) }
        val transformed = CoordinateTransforms.toCorePoints(rawPoints, mirrorHorizontally = true)

        assertThat(transformed).hasSize(21)
        assertThat(transformed[0].x).isWithin(1e-5f).of(1.0f)
        assertThat(transformed[20].x).isWithin(1e-5f).of(0.0f)
    }

    @Test
    fun rotateNormalizedPoint_handlesAllQuarterTurns() {
        val p = Pt(x = 0.2f, y = 0.4f, z = 0.1f)

        val r0 = CoordinateTransforms.rotateNormalizedPoint(p.x, p.y, p.z, 0)
        assertThat(r0.x).isWithin(1e-5f).of(0.2f)
        assertThat(r0.y).isWithin(1e-5f).of(0.4f)

        val r90 = CoordinateTransforms.rotateNormalizedPoint(p.x, p.y, p.z, 90)
        assertThat(r90.x).isWithin(1e-5f).of(0.6f)
        assertThat(r90.y).isWithin(1e-5f).of(0.2f)

        val r180 = CoordinateTransforms.rotateNormalizedPoint(p.x, p.y, p.z, 180)
        assertThat(r180.x).isWithin(1e-5f).of(0.8f)
        assertThat(r180.y).isWithin(1e-5f).of(0.6f)

        val r270 = CoordinateTransforms.rotateNormalizedPoint(p.x, p.y, p.z, 270)
        assertThat(r270.x).isWithin(1e-5f).of(0.4f)
        assertThat(r270.y).isWithin(1e-5f).of(0.8f)
    }

    @Test
    fun rotateNormalizedPoint_handlesNegativeAndWrappedAngles() {
        val p = Pt(x = 0.3f, y = 0.7f, z = 0f)
        val rNeg90 = CoordinateTransforms.rotateNormalizedPoint(p.x, p.y, p.z, -90)
        val r270 = CoordinateTransforms.rotateNormalizedPoint(p.x, p.y, p.z, 270)

        assertThat(rNeg90.x).isWithin(1e-5f).of(r270.x)
        assertThat(rNeg90.y).isWithin(1e-5f).of(r270.y)

        val r360 = CoordinateTransforms.rotateNormalizedPoint(p.x, p.y, p.z, 360)
        assertThat(r360.x).isWithin(1e-5f).of(0.3f)
        assertThat(r360.y).isWithin(1e-5f).of(0.7f)
    }

    @Test
    fun mapToViewSpace_fillStretchesBothAxesCorrectly() {
        val center = Pt(0.5f, 0.5f)
        val mappedCenter = CoordinateTransforms.mapToViewSpace(
            center,
            srcWidth = 960f,
            srcHeight = 1280f,
            dstWidth = 1080f,
            dstHeight = 2400f,
            scaleType = CoordinateTransforms.ScaleType.FILL
        )
        assertThat(mappedCenter.x).isWithin(1e-4f).of(540f)
        assertThat(mappedCenter.y).isWithin(1e-4f).of(1200f)

        val corner = Pt(1.0f, 1.0f)
        val mappedCorner = CoordinateTransforms.mapToViewSpace(
            corner,
            srcWidth = 960f,
            srcHeight = 1280f,
            dstWidth = 1080f,
            dstHeight = 2400f,
            scaleType = CoordinateTransforms.ScaleType.FILL
        )
        assertThat(mappedCorner.x).isWithin(1e-4f).of(1080f)
        assertThat(mappedCorner.y).isWithin(1e-4f).of(2400f)
    }

    @Test
    fun mapToViewSpace_centerCropPreservesAspectRatioAndCenters() {
        val center = Pt(0.5f, 0.5f)
        // Tall phone: 1080x2400 (aspect 0.45), camera: 960x1280 (aspect 0.75)
        val mapped = CoordinateTransforms.mapToViewSpace(
            center,
            srcWidth = 960f,
            srcHeight = 1280f,
            dstWidth = 1080f,
            dstHeight = 2400f,
            scaleType = CoordinateTransforms.ScaleType.CENTER_CROP
        )
        // Center of view is (540, 1200)
        assertThat(mapped.x).isWithin(1e-4f).of(540f)
        assertThat(mapped.y).isWithin(1e-4f).of(1200f)
    }

    @Test
    fun mapToViewSpace_fitCenterPreservesAspectRatioWithLetterbox() {
        val center = Pt(0.5f, 0.5f)
        val mapped = CoordinateTransforms.mapToViewSpace(
            center,
            srcWidth = 960f,
            srcHeight = 1280f,
            dstWidth = 1080f,
            dstHeight = 2400f,
            scaleType = CoordinateTransforms.ScaleType.FIT_CENTER
        )
        // Center of view is (540, 1200)
        assertThat(mapped.x).isWithin(1e-4f).of(540f)
        assertThat(mapped.y).isWithin(1e-4f).of(1200f)
    }
}

class TrackerStatsCollectorTest {

    @Test
    fun statsCollector_recordsFramesAndComputesPercentiles() {
        val collector = TrackerStatsCollector()
        val baseNanos = 1_000_000_000L

        // Record 5 frames with known latencies
        collector.recordFrame(baseNanos + 10_000_000L, 10f)
        collector.recordFrame(baseNanos + 20_000_000L, 20f)
        collector.recordFrame(baseNanos + 30_000_000L, 30f)
        collector.recordFrame(baseNanos + 40_000_000L, 40f)
        collector.recordFrame(baseNanos + 50_000_000L, 50f)

        val stats = collector.stats.value
        assertThat(stats.fps).isEqualTo(5f)
        assertThat(stats.inferenceMsP50).isEqualTo(30f)
        assertThat(stats.inferenceMsP95).isEqualTo(50f)
        assertThat(stats.dropped).isEqualTo(0)
    }

    @Test
    fun statsCollector_recordsDroppedFrames() {
        val collector = TrackerStatsCollector()
        collector.recordDropped()
        collector.recordDropped()

        assertThat(collector.stats.value.dropped).isEqualTo(2)
    }

    @Test
    fun statsCollector_resetClearsAllMetrics() {
        val collector = TrackerStatsCollector()
        collector.recordFrame(1_000_000_000L, 15f)
        collector.recordDropped()

        collector.reset()

        val stats = collector.stats.value
        assertThat(stats.fps).isEqualTo(0f)
        assertThat(stats.inferenceMsP50).isEqualTo(0f)
        assertThat(stats.dropped).isEqualTo(0)
    }
}

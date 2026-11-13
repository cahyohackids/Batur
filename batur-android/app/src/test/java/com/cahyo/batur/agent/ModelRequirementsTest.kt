package com.cahyo.batur.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelRequirementsTest {
    private val gb = 1_000_000_000L

    @Test
    fun rekomendasiSesuaiRam() {
        // Nilai totalMem khas: HP 8 GB, 6 GB, 4 GB, 3 GB, 2 GB.
        assertEquals(ModelRecommendation.GEMMA, ModelRequirements.recommend(7_600_000_000L))
        assertEquals(ModelRecommendation.GEMMA, ModelRequirements.recommend(5_600_000_000L))
        assertEquals(ModelRecommendation.QWEN, ModelRequirements.recommend(3_700_000_000L))
        assertEquals(ModelRecommendation.QWEN, ModelRequirements.recommend(2_800_000_000L))
        assertEquals(ModelRecommendation.CLOUD_ONLY, ModelRequirements.recommend(1_900_000_000L))
    }

    @Test
    fun kebutuhanRamPerModel() {
        assertEquals(6, ModelRequirements.requiredRamGb("gemma-4-e2b", null))
        assertEquals(3, ModelRequirements.requiredRamGb("qwen3-0.6b", null))
        assertEquals(6, ModelRequirements.requiredRamGb("model-lain", 2 * gb))
        assertEquals(3, ModelRequirements.requiredRamGb("model-lain", 400_000_000L))
        assertNull(ModelRequirements.requiredRamGb("model-lain", null))
    }

    @Test
    fun cekRam() {
        assertTrue(ModelRequirements.meetsRam(5_600_000_000L, 6))
        assertFalse(ModelRequirements.meetsRam(3_700_000_000L, 6))
        assertTrue(ModelRequirements.meetsRam(3_700_000_000L, 3))
    }

    @Test
    fun pesanUnduhanGagal() {
        assertTrue(ModelRequirements.downloadErrorMessage(1006).contains("penuh"))
        assertTrue(ModelRequirements.downloadErrorMessage(404).contains("HTTP 404"))
        assertTrue(ModelRequirements.downloadErrorMessage(1).contains("kode 1"))
    }
}

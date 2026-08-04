package com.mbaliga.csapp.clustering

import com.mbaliga.csapp.domain.clustering.AnchorIdGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnchorIdGeneratorTest {

    @Test
    fun `fromAnchorKey is deterministic for the same input`() {
        val id1 = AnchorIdGenerator.fromAnchorKey("github:owner/repo#42")
        val id2 = AnchorIdGenerator.fromAnchorKey("github:owner/repo#42")
        assertEquals(id1, id2)
    }

    @Test
    fun `fromAnchorKey differs for different inputs`() {
        val id1 = AnchorIdGenerator.fromAnchorKey("github:owner/repo#42")
        val id2 = AnchorIdGenerator.fromAnchorKey("github:owner/repo#43")
        assertNotEquals(id1, id2)
    }

    @Test
    fun `fromAnchorKey has the inc_ prefix`() {
        val id = AnchorIdGenerator.fromAnchorKey("play:app:review-1")
        assertTrue(id.startsWith("inc_"))
    }

    @Test
    fun `chooseAnchor picks the earliest by createdAt`() {
        val anchor = AnchorIdGenerator.chooseAnchor(
            listOf("b" to 200L, "a" to 100L, "c" to 300L),
        )
        assertEquals("a", anchor)
    }

    @Test
    fun `chooseAnchor breaks createdAt ties by sourceKey`() {
        val anchor = AnchorIdGenerator.chooseAnchor(
            listOf("zzz" to 100L, "aaa" to 100L),
        )
        assertEquals("aaa", anchor)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `chooseAnchor rejects empty input`() {
        AnchorIdGenerator.chooseAnchor(emptyList())
    }
}

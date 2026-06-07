package app.sift.domain.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VectorMathTest {

    @Test
    fun identicalVectorsAreSimilarity1() {
        assertEquals(1f, cosine(floatArrayOf(1f, 2f, 3f), floatArrayOf(1f, 2f, 3f)), 1e-4f)
    }

    @Test
    fun orthogonalVectorsAre0() {
        assertEquals(0f, cosine(floatArrayOf(1f, 0f), floatArrayOf(0f, 1f)), 1e-4f)
    }

    @Test
    fun oppositeVectorsAreMinus1() {
        assertEquals(-1f, cosine(floatArrayOf(1f, 0f), floatArrayOf(-1f, 0f)), 1e-4f)
    }

    @Test
    fun magnitudeDoesNotMatterOnlyDirection() {
        // 同方向不同长度 → 仍然最像
        assertEquals(1f, cosine(floatArrayOf(1f, 1f), floatArrayOf(5f, 5f)), 1e-4f)
    }

    @Test
    fun mismatchedOrEmptyReturns0() {
        assertEquals(0f, cosine(floatArrayOf(1f, 2f), floatArrayOf(1f)))
        assertEquals(0f, cosine(floatArrayOf(), floatArrayOf()))
    }

    @Test
    fun topKReturnsMostSimilarInOrder() {
        val q = floatArrayOf(1f, 0f)
        val items = listOf(
            "same" to floatArrayOf(1f, 0f),
            "ortho" to floatArrayOf(0f, 1f),
            "near" to floatArrayOf(0.9f, 0.1f),
        )
        val top = topKByCosine(q, items, k = 2) { it.second }.map { it.first.first }
        assertEquals(listOf("same", "near"), top)
    }

    @Test
    fun floatArrayBytesRoundtrip() {
        val v = floatArrayOf(0.1f, -2.5f, 3.14159f, 0f)
        assertTrue(v.toBytes().toFloatArray().contentEquals(v))
    }
}

package dev.rubentxu.pipelattice.compose.compose

import dev.rubentxu.pipelattice.compose.domain.Layer
import dev.rubentxu.pipelattice.compose.domain.MergeResult
import dev.rubentxu.pipelattice.compose.domain.MergeRule
import dev.rubentxu.pipelattice.compose.domain.ParameterNode
import dev.rubentxu.pipelattice.resource.ParameterValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Tests for [MergeEngine.dispatch].
 *
 * Each merge rule is tested across four cases:
 * - lower=null, upper=null → null
 * - lower=null, upper=not null → upper
 * - lower=not null, upper=null → lower
 * - lower=not null, upper=not null → upper wins (ScalarReplace/ListReplace) or merged (MapStructural)
 *
 * Plus two identity-keyed rejection cases when schemaMergeKey is set and either node is a ListNode.
 */
class MergeEngineTest {

    // Helper constructors
    private fun scalar(value: Long) = ParameterNode.ScalarNode(ParameterValue.IntValue(value))
    private fun scalarBool(value: Boolean) = ParameterNode.ScalarNode(ParameterValue.BoolValue(value))
    private fun mapNode(vararg pairs: Pair<String, ParameterNode>) =
        ParameterNode.MapNode(mapOf(*pairs))
    private fun listNode(vararg items: ParameterNode) =
        ParameterNode.ListNode(listOf(*items))

    // --- ScalarReplace tests ---

    @Test
    fun `ScalarReplace lower null upper null returns null`() {
        val engine = MergeEngine()
        val result = engine.dispatch("path", null, MergeRule.ScalarReplace, null, null)
        assertEquals(MergeResult(null, null), result)
    }

    @Test
    fun `ScalarReplace lower null upper not null returns upper`() {
        val engine = MergeEngine()
        val upperNode = scalar(42)
        val result = engine.dispatch("path", null, MergeRule.ScalarReplace, null, upperNode)
        assertEquals(MergeResult(upperNode, null), result)
    }

    @Test
    fun `ScalarReplace lower not null upper null returns lower`() {
        val engine = MergeEngine()
        val lowerNode = scalar(10)
        val result = engine.dispatch("path", null, MergeRule.ScalarReplace, lowerNode, null)
        assertEquals(MergeResult(lowerNode, null), result)
    }

    @Test
    fun `ScalarReplace both not null returns upper`() {
        val engine = MergeEngine()
        val lowerNode = scalar(10)
        val upperNode = scalar(42)
        val result = engine.dispatch("path", null, MergeRule.ScalarReplace, lowerNode, upperNode)
        assertEquals(MergeResult(upperNode, null), result)
    }

    // --- ListReplace tests ---

    @Test
    fun `ListReplace lower null upper null returns null`() {
        val engine = MergeEngine()
        val result = engine.dispatch("path", null, MergeRule.ListReplace, null, null)
        assertEquals(MergeResult(null, null), result)
    }

    @Test
    fun `ListReplace lower null upper not null returns upper`() {
        val engine = MergeEngine()
        val upperNode = listNode(scalar(1), scalar(2))
        val result = engine.dispatch("path", null, MergeRule.ListReplace, null, upperNode)
        assertEquals(MergeResult(upperNode, null), result)
    }

    @Test
    fun `ListReplace lower not null upper null returns lower`() {
        val engine = MergeEngine()
        val lowerNode = listNode(scalar(1), scalar(2))
        val result = engine.dispatch("path", null, MergeRule.ListReplace, lowerNode, null)
        assertEquals(MergeResult(lowerNode, null), result)
    }

    @Test
    fun `ListReplace both not null returns upper`() {
        val engine = MergeEngine()
        val lowerNode = listNode(scalar(1), scalar(2))
        val upperNode = listNode(scalar(3), scalar(4))
        val result = engine.dispatch("path", null, MergeRule.ListReplace, lowerNode, upperNode)
        assertEquals(MergeResult(upperNode, null), result)
    }

    // --- MapStructural tests ---

    @Test
    fun `MapStructural lower null upper null returns null`() {
        val engine = MergeEngine()
        val result = engine.dispatch("path", null, MergeRule.MapStructural { _, l, u -> u ?: l }, null, null)
        assertEquals(MergeResult(null, null), result)
    }

    @Test
    fun `MapStructural lower null upper not null returns upper`() {
        val engine = MergeEngine()
        val upperNode = mapNode("key" to scalar(42))
        val result = engine.dispatch("path", null, MergeRule.MapStructural { _, l, u -> u ?: l }, null, upperNode)
        assertEquals(MergeResult(upperNode, null), result)
    }

    @Test
    fun `MapStructural lower not null upper null returns lower`() {
        val engine = MergeEngine()
        val lowerNode = mapNode("key" to scalar(10))
        val result = engine.dispatch("path", null, MergeRule.MapStructural { _, l, u -> u ?: l }, lowerNode, null)
        assertEquals(MergeResult(lowerNode, null), result)
    }

    @Test
    fun `MapStructural both not null merges entries`() {
        val engine = MergeEngine()
        val lowerNode = mapNode("key1" to scalar(10), "key2" to scalar(20))
        val upperNode = mapNode("key2" to scalar(200), "key3" to scalar(30))
        val result = engine.dispatch("path", null, MergeRule.MapStructural { key, l, u ->
            when (key) {
                "key2" -> u ?: l
                else -> l ?: u
            }
        }, lowerNode, upperNode)
        val value = result.value as ParameterNode.MapNode
        val key2Node = value.entries["key2"] as ParameterNode.ScalarNode
        val intValue = key2Node.value as ParameterValue.IntValue
        assertEquals(200L, intValue.value)
    }

    // --- Identity-keyed rejection (schemaMergeKey != null && ListNode) ---

    @Test
    fun `dispatch rejects identity-keyed merge when schemaMergeKey is set and lower is ListNode`() {
        val engine = MergeEngine()
        val listLower = listNode(scalar(1), scalar(2))
        val scalarUpper = scalar(42)

        val exception = assertFailsWith<MergeEngine.MergeUnsupportedException> {
            engine.dispatch("pipeline.stages", "items", MergeRule.ScalarReplace, listLower, scalarUpper)
        }
        assertEquals("pipeline.stages", exception.path)
        assertEquals("items", exception.mergeKey)
    }

    @Test
    fun `dispatch rejects identity-keyed merge when schemaMergeKey is set and upper is ListNode`() {
        val engine = MergeEngine()
        val scalarLower = scalar(42)
        val listUpper = listNode(scalar(1), scalar(2))

        val exception = assertFailsWith<MergeEngine.MergeUnsupportedException> {
            engine.dispatch("pipeline.stages", "items", MergeRule.ScalarReplace, scalarLower, listUpper)
        }
        assertEquals("pipeline.stages", exception.path)
        assertEquals("items", exception.mergeKey)
    }
}

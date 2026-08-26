package dev.rubentxu.pipelattice.compose.domain

import dev.rubentxu.pipelattice.resource.ParameterValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Invariants for [MergeRule], [ParameterNode], and [MergeResult].
 *
 * Phase 2 — domain/MergeRule
 */
class MergeRuleInvariantsTest {

    // -------------------------------------------------------------------------
    // ScalarReplace
    // -------------------------------------------------------------------------

    @Test
    fun `ScalarReplace null and null returns null with null winner`() {
        val rule = MergeRule.ScalarReplace
        val result = rule.apply(null, null)
        assertNull(result.value)
        assertNull(result.winner)
    }

    @Test
    fun `ScalarReplace lower-only returns lower with lower winner`() {
        val lowerNode = ParameterNode.ScalarNode(ParameterValue.StringValue("lower"))
        val rule = MergeRule.ScalarReplace
        val result = rule.apply(lowerNode, null)
        assertEquals(lowerNode, result.value)
        assertNull(result.winner)
    }

    @Test
    fun `ScalarReplace upper-only returns upper with upper winner`() {
        val upperNode = ParameterNode.ScalarNode(ParameterValue.StringValue("upper"))
        val rule = MergeRule.ScalarReplace
        val result = rule.apply(null, upperNode)
        assertEquals(upperNode, result.value)
        assertNull(result.winner)
    }

    @Test
    fun `ScalarReplace both-present returns upper replacing lower`() {
        val lowerNode = ParameterNode.ScalarNode(ParameterValue.StringValue("lower"))
        val upperNode = ParameterNode.ScalarNode(ParameterValue.StringValue("upper"))
        val rule = MergeRule.ScalarReplace
        val result = rule.apply(lowerNode, upperNode)
        assertEquals(upperNode, result.value)
    }

    // -------------------------------------------------------------------------
    // MapStructural
    // -------------------------------------------------------------------------

    @Test
    fun `MapStructural null and null returns null with null winner`() {
        val rule = MergeRule.MapStructural { _, _, u -> u }
        val result = rule.apply(null, null)
        assertNull(result.value)
        assertNull(result.winner)
    }

    @Test
    fun `MapStructural lower-only returns lower`() {
        val lowerNode = ParameterNode.MapNode(
            mapOf("key" to ParameterNode.ScalarNode(ParameterValue.StringValue("lower")))
        )
        val rule = MergeRule.MapStructural { _, _, u -> u }
        val result = rule.apply(lowerNode, null)
        assertEquals(lowerNode, result.value)
        assertNull(result.winner)
    }

    @Test
    fun `MapStructural upper-only returns upper`() {
        val upperNode = ParameterNode.MapNode(
            mapOf("key" to ParameterNode.ScalarNode(ParameterValue.StringValue("upper")))
        )
        val rule = MergeRule.MapStructural { _, _, u -> u }
        val result = rule.apply(null, upperNode)
        assertEquals(upperNode, result.value)
    }

    @Test
    fun `MapStructural both-present merges maps using keyMerger`() {
        val lowerNode = ParameterNode.MapNode(
            mapOf(
                "shared" to ParameterNode.ScalarNode(ParameterValue.StringValue("lower")),
                "lowerOnly" to ParameterNode.ScalarNode(ParameterValue.StringValue("lower-only"))
            )
        )
        val upperNode = ParameterNode.MapNode(
            mapOf(
                "shared" to ParameterNode.ScalarNode(ParameterValue.StringValue("upper")),
                "upperOnly" to ParameterNode.ScalarNode(ParameterValue.StringValue("upper-only"))
            )
        )
        // keyMerger: (key, lowerValue, upperValue) -> upperValue (upper wins on conflict)
        val rule = MergeRule.MapStructural { _, _, upper -> upper }
        val result = rule.apply(lowerNode, upperNode)

        val mergedMap = (result.value as ParameterNode.MapNode).entries
        assertEquals(ParameterNode.ScalarNode(ParameterValue.StringValue("upper")), mergedMap["shared"])
        assertEquals(ParameterNode.ScalarNode(ParameterValue.StringValue("lower-only")), mergedMap["lowerOnly"])
        assertEquals(ParameterNode.ScalarNode(ParameterValue.StringValue("upper-only")), mergedMap["upperOnly"])
    }

    // -------------------------------------------------------------------------
    // ListReplace
    // -------------------------------------------------------------------------

    @Test
    fun `ListReplace null and null returns null with null winner`() {
        val rule = MergeRule.ListReplace
        val result = rule.apply(null, null)
        assertNull(result.value)
        assertNull(result.winner)
    }

    @Test
    fun `ListReplace lower-only returns lower`() {
        val lowerNode = ParameterNode.ListNode(
            listOf(ParameterNode.ScalarNode(ParameterValue.StringValue("a")))
        )
        val rule = MergeRule.ListReplace
        val result = rule.apply(lowerNode, null)
        assertEquals(lowerNode, result.value)
        assertNull(result.winner)
    }

    @Test
    fun `ListReplace upper-only returns upper`() {
        val upperNode = ParameterNode.ListNode(
            listOf(ParameterNode.ScalarNode(ParameterValue.StringValue("b")))
        )
        val rule = MergeRule.ListReplace
        val result = rule.apply(null, upperNode)
        assertEquals(upperNode, result.value)
    }

    @Test
    fun `ListReplace both-present replaces lower with upper`() {
        val lowerNode = ParameterNode.ListNode(
            listOf(
                ParameterNode.ScalarNode(ParameterValue.StringValue("a")),
                ParameterNode.ScalarNode(ParameterValue.StringValue("b"))
            )
        )
        val upperNode = ParameterNode.ListNode(
            listOf(ParameterNode.ScalarNode(ParameterValue.StringValue("c")))
        )
        val rule = MergeRule.ListReplace
        val result = rule.apply(lowerNode, upperNode)
        val resultList = (result.value as ParameterNode.ListNode).items
        assertEquals(1, resultList.size)
        assertEquals(ParameterNode.ScalarNode(ParameterValue.StringValue("c")), resultList[0])
    }

    // -------------------------------------------------------------------------
    // ParameterNode variants
    // -------------------------------------------------------------------------

    @Test
    fun `ParameterNode ScalarNode carries ParameterValue`() {
        val pv = ParameterValue.IntValue(42L)
        val node = ParameterNode.ScalarNode(pv)
        assertEquals(pv, node.value)
    }

    @Test
    fun `ParameterNode MapNode carries entries map`() {
        val entries = mapOf("key" to ParameterNode.ScalarNode(ParameterValue.BoolValue(true)))
        val node = ParameterNode.MapNode(entries)
        assertEquals(entries, node.entries)
        assertEquals(1, node.entries.size)
    }

    @Test
    fun `ParameterNode ListNode carries items list`() {
        val items = listOf(
            ParameterNode.ScalarNode(ParameterValue.StringValue("x")),
            ParameterNode.ScalarNode(ParameterValue.StringValue("y"))
        )
        val node = ParameterNode.ListNode(items)
        assertEquals(items, node.items)
        assertEquals(2, node.items.size)
    }

    // -------------------------------------------------------------------------
    // MergeResult
    // -------------------------------------------------------------------------

    @Test
    fun `MergeResult value and winner are preserved`() {
        val node = ParameterNode.ScalarNode(ParameterValue.StringValue("result"))
        val result = MergeResult(value = node, winner = Layer.LOCAL)
        assertEquals(node, result.value)
        assertEquals(Layer.LOCAL, result.winner)
    }

    @Test
    fun `MergeResult winner may be null`() {
        val result = MergeResult(value = null, winner = null)
        assertNull(result.value)
        assertNull(result.winner)
    }
}

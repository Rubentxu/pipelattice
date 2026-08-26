package dev.rubentxu.pipelattice.graph.store

import dev.rubentxu.pipelattice.foundation.ResourceRef
import dev.rubentxu.pipelattice.graph.domain.Edge
import dev.rubentxu.pipelattice.graph.domain.EdgeKind
import dev.rubentxu.pipelattice.graph.domain.GraphChangeSet
import dev.rubentxu.pipelattice.graph.domain.GraphNode
import dev.rubentxu.pipelattice.graph.domain.GraphSnapshot
import dev.rubentxu.pipelattice.graph.domain.PlanFingerprint
import dev.rubentxu.pipelattice.graph.domain.StructuralDiff
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InMemoryGraphProjectionStoreTest {

    @Test
    fun `apply then snapshot returns nodes and edges with deterministic fingerprint`() {
        val store = InMemoryGraphProjectionStore()
        val project = GraphNode.Project(ResourceRef("projects/example"))
        val profile = GraphNode.PipelineProfile(ResourceRef("profiles/java-maven"))
        val edge = Edge(source = profile, target = project, kind = EdgeKind.SELECTS)

        store.apply(GraphChangeSet(addedEdges = listOf(edge), removedEdges = emptyList()))

        val snap = store.snapshot()
        assertEquals(2, snap.nodes.size)
        assertEquals(1, snap.edges.size)
        assertEquals(setOf(project, profile), snap.nodes)
        assertEquals(setOf(edge), snap.edges)
        assertEquals(64, snap.fingerprint.value.length)
        assertTrue(snap.fingerprint.value.all { it.isDigit() || it in 'a'..'f' })
    }

    @Test
    fun `fingerprint is deterministic regardless of apply order`() {
        val s1 = InMemoryGraphProjectionStore()
        val s2 = InMemoryGraphProjectionStore()
        val e1 = Edge(
            GraphNode.PipelineProfile(ResourceRef("a")),
            GraphNode.Project(ResourceRef("b")),
            EdgeKind.SELECTS,
        )
        val e2 = Edge(
            GraphNode.PipelineProfile(ResourceRef("a")),
            GraphNode.ConfigSource(Path.of("/x"), "h"),
            EdgeKind.IMPORTS,
        )

        s1.apply(GraphChangeSet(listOf(e1, e2), emptyList()))
        s2.apply(GraphChangeSet(listOf(e2, e1), emptyList()))

        assertEquals(s1.snapshot().fingerprint, s2.snapshot().fingerprint)
        assertEquals(s1.snapshot().edges, s2.snapshot().edges)
    }

    @Test
    fun `last-writer-wins when edge appears in both added and removed`() {
        val store = InMemoryGraphProjectionStore()
        val project = GraphNode.Project(ResourceRef("projects/example"))
        val profile = GraphNode.PipelineProfile(ResourceRef("profiles/java-maven"))
        val edge = Edge(source = profile, target = project, kind = EdgeKind.SELECTS)

        // Add the edge first
        store.apply(GraphChangeSet(addedEdges = listOf(edge), removedEdges = emptyList()))
        assertEquals(1, store.snapshot().edges.size)

        // Now try to remove and add the same edge — add wins
        store.apply(GraphChangeSet(addedEdges = listOf(edge), removedEdges = listOf(edge)))
        assertEquals(1, store.snapshot().edges.size)
        assertTrue(edge in store.snapshot().edges)
    }

    @Test
    fun `remove then add results in edge present`() {
        val store = InMemoryGraphProjectionStore()
        val project = GraphNode.Project(ResourceRef("projects/example"))
        val profile = GraphNode.PipelineProfile(ResourceRef("profiles/java-maven"))
        val edge = Edge(source = profile, target = project, kind = EdgeKind.SELECTS)

        // Remove non-existent edge first, then add
        store.apply(GraphChangeSet(addedEdges = emptyList(), removedEdges = listOf(edge)))
        store.apply(GraphChangeSet(addedEdges = listOf(edge), removedEdges = emptyList()))

        assertEquals(1, store.snapshot().edges.size)
        assertTrue(edge in store.snapshot().edges)
    }

    @Test
    fun `diff returns symmetric delta plus affected nodes union`() {
        val a = GraphNode.PipelineProfile(ResourceRef("a"))
        val b = GraphNode.Project(ResourceRef("b"))
        val c = GraphNode.Project(ResourceRef("c"))

        val edgeAB = Edge(a, b, EdgeKind.SELECTS)
        val edgeAC = Edge(a, c, EdgeKind.SELECTS)

        val baselineEdges = setOf(edgeAB)
        val candidateEdges = setOf(edgeAB, edgeAC)

        val baselineFingerprint = PlanFingerprint("0".repeat(64))
        val candidateFingerprint = PlanFingerprint("1".repeat(64))

        val baseline = GraphSnapshot(
            nodes = setOf(a, b),
            edges = baselineEdges,
            fingerprint = baselineFingerprint,
        )
        val candidate = GraphSnapshot(
            nodes = setOf(a, b, c),
            edges = candidateEdges,
            fingerprint = candidateFingerprint,
        )

        val diff = StructuralDiff.diff(baseline, candidate)

        assertEquals(1, diff.addedEdges.size)
        assertTrue(edgeAC in diff.addedEdges)
        assertEquals(0, diff.removedEdges.size)
        assertTrue(c in diff.affectedNodes)
        assertTrue(a in diff.affectedNodes)
    }

    @Test
    fun `snapshot returns immutable view independent of subsequent applies`() {
        val store = InMemoryGraphProjectionStore()
        val project = GraphNode.Project(ResourceRef("projects/example"))
        val profile1 = GraphNode.PipelineProfile(ResourceRef("profiles/java"))
        val profile2 = GraphNode.PipelineProfile(ResourceRef("profiles/maven"))
        val edge1 = Edge(profile1, project, EdgeKind.SELECTS)
        val edge2 = Edge(profile2, project, EdgeKind.SELECTS)

        store.apply(GraphChangeSet(listOf(edge1), emptyList()))
        val snap1 = store.snapshot()

        store.apply(GraphChangeSet(listOf(edge2), emptyList()))
        val snap2 = store.snapshot()

        // snap1 should be independent of the second apply
        assertEquals(1, snap1.edges.size)
        assertEquals(2, snap2.edges.size)
    }
}

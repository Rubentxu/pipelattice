package dev.rubentxu.pipelattice.compose.ports

import dev.rubentxu.pipelattice.compose.domain.Provenance

/**
 * Outbound port for emitting provenance nodes during composition.
 *
 * This port is "port-folded": the composition engine holds a reference to this port
 * and calls [emit] for each resolved provenance node. The adapter behind this port
 * decides what to do with the node (e.g., collect it, serialize it, stream it).
 *
 * Using a fun interface allows lambda syntax for simple adapters while preserving
 * the ability to implement with a named class for complex adapters.
 */
public fun interface ProvenanceSink {

    /**
     * Emits a single provenance node.
     *
     * @param node The provenance node to emit.
     */
    public fun emit(node: Provenance)
}

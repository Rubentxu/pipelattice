package dev.rubentxu.pipelattice.graph.ports

import dev.rubentxu.pipelattice.graph.domain.GraphChangeSet
import dev.rubentxu.pipelattice.graph.domain.GraphSnapshot
import kotlin.test.Test

/**
 * Compile-time test that [GraphProjectionStore] has the exact signature
 * declared in spec 01_ARCHITECTURE.md §6.
 */
class GraphProjectionStoreInterfaceTest {

    @Test
    fun `GraphProjectionStore has apply and snapshot methods`() {
        // Compile-time verification: if this object literal compiles, the interface
        // has the exact two-method signature required by spec 01 §6.
        val store: GraphProjectionStore = object : GraphProjectionStore {
            override fun apply(changeSet: GraphChangeSet) {
                // contract: apply takes GraphChangeSet, returns Unit
            }

            override fun snapshot(): GraphSnapshot {
                // contract: snapshot takes no params, returns GraphSnapshot
                throw UnsupportedOperationException()
            }
        }
        assert(store.toString().isNotEmpty())
    }
}

# ADR-0006 — Separación estricta con pipeline-kotlin

**Estado:** Accepted

Pipelattice no depende de pipeline-kotlin. El bridge depende de ambos.

Contrato de frontera: `ResolvedPipelinePlan`/plan protocol.

Un cambio de DSL/runtime no debe obligar a recompilar providers de Pipelattice.

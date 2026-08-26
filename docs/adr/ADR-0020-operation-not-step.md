# ADR-0020 — Operation es el concepto de Pipelattice; Step pertenece al runtime

**Estado:** Accepted

Pipelattice compila `OperationPlan` con capability, inputs, effects e idempotency. El bridge traduce a Step del runtime.

Evita duplicar el modelo de ejecución de pipeline-kotlin.

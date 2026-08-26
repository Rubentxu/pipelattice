# ADR-0007 — Uso restringido de Kotlin context parameters

**Estado:** Accepted

Kotlin 2.4 estabiliza context parameters y reemplaza context receivers.

Se permiten internamente para concerns request-scoped como diagnostics/provenance. No son DI general ni API pública de providers en V1.

Regla inicial: máximo recomendado de dos contexts por función.

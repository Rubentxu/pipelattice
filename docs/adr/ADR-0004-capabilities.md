# ADR-0004 — Capabilities en vez de Tool hierarchy

**Estado:** Accepted

Los workflows dependen de capability IDs. Maven/Gradle/Helm son providers.

Se elimina el concepto universal `ITool.execute(String, options)`.

Segundo provider real obligatorio antes de estabilizar una capability general.

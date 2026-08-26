# ADR-0016 — API pública pequeña y compatible

**Estado:** Accepted

`explicitApi()` en artefactos públicos. `internal` por defecto.

Capability IDs, resource schemas y plan protocol se versionan deliberadamente. Se añaden checks de compatibilidad binaria antes de v1 estable.

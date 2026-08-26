# ADR-0002 — Git es la fuente de verdad

**Estado:** Accepted

## Decisión
La configuración persistente vive en Git. Graph/store son proyecciones reconstruibles.

No se introduce EventStore como verdad paralela en V1. Candidate changes usan commits/branches/PRs.

## Consecuencia
El control-plane puede perder y reconstruir su projection sin perder intención declarada.

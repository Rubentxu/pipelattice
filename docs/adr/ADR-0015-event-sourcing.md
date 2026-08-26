# ADR-0015 — Event sourcing no es baseline

**Estado:** Accepted

Git aporta historia, branches y revisiones para configuration. El control-plane puede emitir eventos de observabilidad, pero no se modela todo como event-sourced.

Revisar solo si existen requisitos fuertes de reconstrucción causal no cubiertos por Git + provenance.

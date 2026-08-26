# ADR-0003 — Configuración por capas tipo Kustomize, pero tipada

**Estado:** Accepted

## Decisión
Imports + parameters + structural patches + governance.

Se rechaza templating textual Turing-complete. Colecciones se fusionan por identity keys definidos en schema.

## Trigger
Si >10% de proyectos requieren patches complejos, revisar resources/taxonomía antes de ampliar el mini-lenguaje.

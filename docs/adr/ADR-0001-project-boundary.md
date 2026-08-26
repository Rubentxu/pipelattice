# ADR-0001 — Pipelattice es control-plane, no runtime

**Estado:** Accepted

## Contexto
`framework_modular` mezcla capacidades y runtime Jenkins. `pipeline-kotlin` ya cubre la dirección de runtime/DSL.

## Decisión
Pipelattice compila configuración y produce `ResolvedPipelinePlan`. No ejecuta el DAG distribuido.

## Consecuencias
- retry/workers/replay/scheduling pertenecen a pipeline-kotlin;
- CLI puede validar y hacer tooling, no convertirse en scheduler;
- se evita repetir el standalone executor.

## Trigger de revisión
Solo si aparece un caso de uso de ejecución que pipeline-kotlin no pueda representar sin violar su propio boundary.

[English](README.md) | [Español](README.es.md)

# Pipelattice

[![CI](https://github.com/Rubentxu/pipelattice/actions/workflows/ci.yml/badge.svg)](https://github.com/Rubentxu/pipelattice/actions/workflows/ci.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
![Kotlin](https://img.shields.io/badge/Kotlin-2.4.10-7F52FF.svg)
![JVM](https://img.shields.io/badge/JVM-21-4c8e33.svg)

**Pipelattice es un plano de control GitOps de configuración que compila configuración de pipelines tipada y versionada en un `ResolvedPipelinePlan` inmutable y explicable.**

No ejecuta pipelines. La ejecución pertenece a un runtime (como [pipeline-kotlin](https://github.com/Rubentxu/pipeline-kotlin)) que consume el plan compilado mediante un adaptador pequeño y versionado.

## El problema que resuelve

Las plataformas de CI/CD empresariales suelen acumular decisiones corporativas dentro de clases del build tool y librerías compartidas, hasta que miles de pipelines se vuelven rígidos, no auditables y peligrosos de evolucionar. Pipelattice convierte ese problema en **compilación de configuración GitOps tipada**:

```text
Git (repos locales + catálogo central)
        │
        ▼
Configuration Compiler ──► Configuration Graph ──► Consultas de flota (impacto, drift)
        │
        ▼
ResolvedPipelinePlan (con fingerprint y provenance)
        │
        ▼
Adaptador de runtime (la ejecución queda fuera)
```

Una empresa mantiene un catálogo central con perfiles, políticas, providers, entornos y defaults. Cada repositorio declara únicamente su intención local — el `pipeline.yaml` del camino feliz cabe en menos de 40 líneas:

```yaml
apiVersion: pipelattice.dev/v1alpha1
kind: PipelineDefinition
metadata:
  name: payments-api
spec:
  profile:
    ref: catalog://profiles/java-maven-container@stable
  parameters:
    javaVersion: 21
```

## Principios de diseño

- **Git como única verdad.** El grafo y los planes son proyecciones reconstruibles; no existe una base de datos mutable de configuración.
- **Tipado de extremo a extremo.** Los errores aparecen en tiempo de compilación; nada de `Map<String, Any?>` en APIs públicas.
- **Governance integrado.** La configuración central puede ser `default` (modificable), `guardrail` (acotada) o `mandatory` (no eliminable).
- **Explicable.** Cada valor compilado lleva provenance: qué archivo, qué revisión y qué import lo puso ahí.
- **Capacidades, no herramientas.** Maven, Gradle o Helm son providers de capacidades (`project.build`, `image.build`, …); los workflows componen capacidades.
- **Núcleo neutral al runtime.** Utilizable desde CLI, servidores, IDEs, agentes y runtimes — el dominio nunca importa maquinaria de ejecución.

## Estado del proyecto

El proyecto avanza por hitos progresivos con criterios de salida explícitos (primero el arnés de arquitectura, después la funcionalidad).

| Hito | Alcance | Estado |
|---|---|---|
| **M0 — Foundation & Architecture Harness** | Build multi-módulo, convention plugins, `explicitApi`, warnings-as-errors, tipos de identidad, modelo de diagnostics, reglas fitness con ArchUnit, CI | ✅ Completado |
| **M1 — Typed Resource Model** | Envelope de recursos, primeros recursos, adaptador YAML con diagnostics posicionales | ✅ Completado |
| **M2 — Composition Compiler (slice)** | Resolución de imports + parameter overlay + replace-style merge + provenance + `explain(path)`. Subset §4 (3/9 capas) + §7 (3/6 reglas). UAT-001/002/015 cubiertos | ✅ Completado |
| M3+ | Policy engine, slice vertical de build, grafo reactivo, fleet diff, puente a runtime | ⏳ Planificado |

## Compilar desde fuente

Requisitos: **JDK 21** (el toolchain de Gradle lo fija) — todo lo demás llega vía wrapper.

```bash
git clone https://github.com/Rubentxu/pipelattice.git
cd pipelattice
./gradlew build          # compila + tests unitarios + tests de arquitectura
```

La verificación local es la puerta: `./gradlew build` ejecuta las mismas comprobaciones que CI.

## Módulos

| Módulo | Propósito |
|---|---|
| `build-logic` | Convention plugins: toolchain JVM 21, `explicitApi`, warnings-as-errors, JUnit platform |
| `foundation` | Tipos de identidad (value classes) y modelo de diagnostics con códigos de error estables |
| `testkit` | Utilidades de test compartidas (sinks de diagnósticos, fakes futuros) |
| `architecture-tests` | Reglas fitness de arquitectura aplicadas con ArchUnit |
| `resource-model` | Envelope de recursos tipado, value classes para identidad, governance y tipos de parámetros |
| `config-compiler` | Adaptador de parseo YAML (SnakeYAML Engine); único módulo que conoce YAML |
| `pipeline-compose` | Motor de composición: resolución de imports + parameter overlay + replace-style merge + provenance + `explain(path)`. Subset §4 (3/9 capas) + §7 (3/6 reglas). UAT-001/002/015 cubiertos. Aplica FARCH-011 (sin deps de YAML/JSON/GIT/serialization; puentea M1 solo vía el port `ResourceParser`) |

Los módulos futuros (`workflow-model`, `capabilities-api`, `policy-engine`, `graph-projection`, providers, `cli`) siguen el orden de hitos y solo se registran cuando contienen código real.

## Documentación

- [`docs/adr/`](docs/adr/) — decisiones de arquitectura aceptadas (boundary del proyecto, Git-como-verdad, configuración estratificada, capabilities, governance, provenance, secretos…)
- Las convenciones de ingeniería viven en el propio build logic: cada PR exige tests, architecture tests, códigos de diagnóstico estables para fallos nuevos, cero warnings sin justificar y un ADR cuando cambia un boundary.

## Licencia

[MIT](LICENSE) © Rubentxu

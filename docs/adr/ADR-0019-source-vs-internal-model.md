# ADR-0019 — Source schema separado del modelo interno

**Estado:** Accepted

El YAML externo se parsea a resources versionados y luego a modelos semánticos internos. El core no depende de una librería YAML.

Esto permite migraciones de schema sin contaminar el dominio y evita que decisiones sintácticas se conviertan en arquitectura.

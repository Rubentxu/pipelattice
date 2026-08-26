# ADR-0017 — Secrets por referencia

**Estado:** Accepted

Models serializables contienen `SecretRef`, nunca secreto material.

Resolución solo en execution boundary. Diagnostics y command rendering deben redactar información sensible.

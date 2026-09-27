# Failure fingerprinting

Phase 3 creates deterministic v1 SHA-256 failure signatures. The canonical input is `v1|failure type|exception type|normalized message|normalized stack root`. Timestamps, UUIDs, large IDs, ports, temporary paths, and Java line numbers are normalized before hashing. The full raw evidence remains separate from the stable signature.

Fingerprints identify recurring failure shapes, not final root cause. v1 is intentionally conservative and explicit; future algorithms must use a new version prefix so existing signatures remain interpretable.

# Test report ingestion

Axiom accepts bounded JUnit-style XML through the pipeline test-report endpoint. XML parsing disables DOCTYPE and external entities. Test identity is `test-id-v1|class|suite|name` hashed with SHA-256; status and duration do not alter it. This phase stores executions for future history analysis but does not calculate flakiness.

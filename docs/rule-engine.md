# Rule engine

The v1 classifier is deterministic and not AI. Rules emit explicit evidence; root signals outweigh generic downstream command failures. Scores are bounded heuristic confidence, not calibrated probabilities. Below 0.40 Axiom returns `UNKNOWN`. Each stored diagnosis records `axiom-classifier-v1` and its evidence, allowing future versions to coexist.

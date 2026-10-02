package com.axiom;

import org.junit.jupiter.api.Test;

/** Disposable GitHub acceptance fixture. Never merge this branch into main. */
class ControlledAcceptanceTest {
    @Test void controlledSameCommitRetry() {
        if ("true".equals(System.getenv("AXIOM_CONTROLLED_ACCEPTANCE"))
                && "1".equals(System.getenv("GITHUB_RUN_ATTEMPT"))) {
            throw new AssertionError("Axiom controlled acceptance sentinel: attempt one intentionally fails");
        }
    }
}

package com.axiom.analysis.fingerprint;

import com.axiom.application.analysis.AnalysisContext;
import java.util.Optional;

public interface FailureFingerprinter { Optional<String> fingerprint(AnalysisContext context); }

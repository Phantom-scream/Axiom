package com.axiom.analysis.classification;

import com.axiom.application.analysis.AnalysisContext;
import com.axiom.domain.diagnosis.Diagnosis;

public interface FailureClassifier { Diagnosis classify(AnalysisContext context); }

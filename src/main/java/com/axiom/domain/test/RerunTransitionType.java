package com.axiom.domain.test;

public enum RerunTransitionType {
    FAIL_TO_PASS,
    ERROR_TO_PASS,
    FAIL_TO_FAIL,
    ERROR_TO_ERROR,
    PASS_TO_FAIL,
    PASS_TO_PASS,
    FAIL_TO_DIFFERENT_FAILURE,
    UNKNOWN
}

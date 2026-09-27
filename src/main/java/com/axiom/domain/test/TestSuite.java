package com.axiom.domain.test; import java.util.List; public record TestSuite(String name,List<TestCaseExecution> testCases) {public TestSuite{testCases=List.copyOf(testCases);}}

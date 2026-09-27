package com.axiom.integrations.testreport; import com.axiom.domain.test.TestReport; public interface TestReportParser {TestReport parse(String sourceName,byte[] content,String frameworkHint);}

package com.axiom.analysis;
import com.axiom.analysis.logs.*;
import com.axiom.analysis.fingerprint.Sha256FailureFingerprinter;
import com.axiom.analysis.classification.ExplainableFailureClassifier;
import com.axiom.application.analysis.*;
import com.axiom.config.TriageProperties;
import com.axiom.domain.failure.*;
import com.axiom.domain.triage.ActionType;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
class LiveSpringDiagnosticsRegressionTest {
 private final LogNormalizer normalizer=new LogNormalizer();
 private final FailureEventExtractor extractor=new FailureEventExtractor(new Sha256FailureFingerprinter());
 private String fixture() throws Exception {try(var in=getClass().getResourceAsStream("/fixtures/live-spring-context-excerpt.log")){return new String(java.util.Objects.requireNonNull(in).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);}}
 @Test void deepestObservedCauseIsExtractedAndStableAcrossWrapperTestsAndSourceLines() throws Exception {
  var events=extractor.extract(normalizer.normalize(fixture()));
  var root=events.stream().filter(e->"org.springframework.beans.factory.NoSuchBeanDefinitionException".equals(e.exceptionType())).findFirst().orElseThrow();
  assertThat(root.type()).isEqualTo(FailureEventType.EXCEPTION);assertThat(root.occurrences()).isEqualTo(2);
  assertThat(root.rawMessage()).contains("Caused by:");
  var changed=extractor.extract(normalizer.normalize(fixture().replace("2304","2399").replace("AxiomApplicationTests","OtherContextTest")));
  assertThat(changed).anySatisfy(e->assertThat(e.fingerprint()).isEqualTo(root.fingerprint()));
  assertThat(events).anySatisfy(e->assertThat(e.type()).isEqualTo(FailureEventType.BUILD_ERROR));
 }
 @Test void missingBeanEvidenceOutranksGenericBuildSymptomsAndProducesConfigurationAction() throws Exception {
  var classifier=new ExplainableFailureClassifier();var inputs=new ArrayList<PipelineTriageService.Failure>();
  for(var event:extractor.extract(normalizer.normalize(fixture()))) {
   var diagnosis=classifier.classify(event.normalizedMessage(),event.exceptionType());
   boolean generic=event.type()==FailureEventType.BUILD_ERROR||event.exceptionType()==null;
   inputs.add(new PipelineTriageService.Failure(UUID.randomUUID(),event.fingerprint(),diagnosis.classification(),generic,event.firstLine(),0,null,0,null,false,false));
  }
  var triage=new PipelineTriageService().triage(inputs);
  assertThat(triage.primaryClassification()).isEqualTo(FailureClassification.CONFIGURATION_FAILURE);
  assertThat(triage.failureRankings()).anySatisfy(e->assertThat(e.role().name()).isEqualTo("DOWNSTREAM"));
  assertThat(new DeveloperActionService(new TriageProperties(null,null,null,null)).generate(triage,false)).extracting(a->a.type()).contains(ActionType.INSPECT_CONFIGURATION);
 }
 @Test void unsatisfiedDependencyWrapperAloneDoesNotAssertMissingBeanConfiguration() {
  assertThat(new ExplainableFailureClassifier().classify("org.springframework.beans.factory.UnsatisfiedDependencyException",null).classification()).isEqualTo(FailureClassification.UNKNOWN);
 }
}

package com.axiom.application.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.axiom.api.error.GitComparisonUnavailableException;
import com.axiom.domain.pipeline.CiProviderType;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class GitChangeReferenceResolverTest {
    private final GitChangeReferenceResolver resolver =
            new GitChangeReferenceResolver(mock(JdbcTemplate.class));

    @Test
    void resolvesPersistedPullRequestBaseAndHead() {
        UUID runId = UUID.randomUUID();
        var reference = resolver.resolve(new GitChangeReferenceResolver.PipelineMetadata(
                runId,
                UUID.randomUUID(),
                CiProviderType.GITHUB_ACTIONS,
                "owner",
                "repo",
                "base",
                "head",
                42L,
                "pull_request"));

        assertThat(reference.pipelineRunId()).isEqualTo(runId);
        assertThat(reference.baseSha()).isEqualTo("base");
        assertThat(reference.headSha()).isEqualTo("head");
        assertThat(reference.pullRequestNumber()).isEqualTo(42L);
    }

    @Test
    void resolvesPushOnlyWhenProviderMetadataContainsBase() {
        var reference = resolver.resolve(new GitChangeReferenceResolver.PipelineMetadata(
                UUID.randomUUID(),
                UUID.randomUUID(),
                CiProviderType.GITHUB_ACTIONS,
                "owner",
                "repo",
                "before-sha",
                "head-sha",
                null,
                "push"));

        assertThat(reference.baseSha()).isEqualTo("before-sha");
        assertThat(reference.eventName()).isEqualTo("push");
    }

    @Test
    void missingBaseFailsWithoutAssumingMainOrParent() {
        var metadata = new GitChangeReferenceResolver.PipelineMetadata(
                UUID.randomUUID(),
                UUID.randomUUID(),
                CiProviderType.GITHUB_ACTIONS,
                "owner",
                "repo",
                null,
                "head",
                null,
                "push");

        assertThatThrownBy(() -> resolver.resolve(metadata))
                .isInstanceOf(GitComparisonUnavailableException.class)
                .hasMessageContaining("no reliable base SHA")
                .hasMessageNotContaining("main")
                .hasMessageNotContaining("HEAD~1");
    }
}

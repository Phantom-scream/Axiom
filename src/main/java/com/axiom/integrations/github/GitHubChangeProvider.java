package com.axiom.integrations.github;

import com.axiom.domain.pipeline.ChangeType;
import com.axiom.domain.pipeline.ChangedFile;
import com.axiom.domain.pipeline.CiProviderType;
import com.axiom.domain.pipeline.FileCategory;
import com.axiom.domain.pipeline.GitChangeReference;
import com.axiom.domain.pipeline.GitChangeSet;
import com.axiom.domain.pipeline.GitProvider;
import com.axiom.integrations.ci.GitChangeProvider;
import com.axiom.integrations.github.client.GitHubApiClient;
import com.axiom.integrations.github.dto.GitHubChangedFileDto;
import com.axiom.integrations.github.exception.GitHubIntegrationException;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class GitHubChangeProvider implements GitChangeProvider {
    private final GitHubApiClient client;

    public GitHubChangeProvider(GitHubApiClient client) {
        this.client = client;
    }

    @Override
    public CiProviderType providerType() {
        return CiProviderType.GITHUB_ACTIONS;
    }

    @Override
    public GitChangeSet fetchChanges(GitChangeReference reference) {
        var response = client.compare(
                reference.repositoryOwner(),
                reference.repositoryName(),
                reference.baseSha(),
                reference.headSha());
        List<GitHubChangedFileDto> responseFiles = response.files() == null ? List.of() : response.files();
        if (responseFiles.size() > 300) {
            throw new GitHubIntegrationException("GitHub returned more changed files than Axiom can safely process.");
        }
        List<ChangedFile> files = responseFiles.stream().map(this::map).toList();
        int additions = files.stream().mapToInt(ChangedFile::additions).sum();
        int deletions = files.stream().mapToInt(ChangedFile::deletions).sum();
        String baseSha = response.baseCommit() == null || response.baseCommit().sha() == null
                ? reference.baseSha()
                : response.baseCommit().sha();
        return new GitChangeSet(
                reference.pipelineRunId(),
                reference.repositoryId(),
                baseSha,
                reference.headSha(),
                reference.pullRequestNumber(),
                GitProvider.GITHUB,
                files.size(),
                additions,
                deletions,
                files);
    }

    private ChangedFile map(GitHubChangedFileDto source) {
        validatePath(source.filename());
        if (source.previousFilename() != null) validatePath(source.previousFilename());
        return new ChangedFile(
                source.filename(),
                source.previousFilename(),
                changeType(source.status()),
                FileCategory.UNKNOWN,
                null,
                Math.max(0, source.additions()),
                Math.max(0, source.deletions()),
                Math.max(0, source.changes()));
    }

    private ChangeType changeType(String status) {
        if (status == null) return ChangeType.UNKNOWN;
        return switch (status.toLowerCase(Locale.ROOT)) {
            case "added" -> ChangeType.ADDED;
            case "modified", "changed" -> ChangeType.MODIFIED;
            case "removed", "deleted" -> ChangeType.DELETED;
            case "renamed" -> ChangeType.RENAMED;
            case "copied" -> ChangeType.COPIED;
            default -> ChangeType.UNKNOWN;
        };
    }

    private void validatePath(String path) {
        if (path == null || path.isBlank() || path.length() > 4096 || path.indexOf('\0') >= 0) {
            throw new GitHubIntegrationException("GitHub returned an invalid changed-file path.");
        }
    }
}

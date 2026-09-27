package com.axiom.application.pipeline;

import com.axiom.domain.pipeline.ChangedFile;
import com.axiom.domain.pipeline.GitChangeSet;
import com.axiom.integrations.ci.GitChangeProvider;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class GitChangeIngestionService {
    private final GitChangeReferenceResolver references;
    private final List<GitChangeProvider> providers;
    private final ChangedFileClassifier classifier;
    private final GitChangePersistenceService persistence;

    public GitChangeIngestionService(
            GitChangeReferenceResolver references,
            List<GitChangeProvider> providers,
            ChangedFileClassifier classifier,
            GitChangePersistenceService persistence) {
        this.references = references;
        this.providers = List.copyOf(providers);
        this.classifier = classifier;
        this.persistence = persistence;
    }

    public GitChangeSet ingest(UUID pipelineRunId) {
        var reference = references.resolve(pipelineRunId);
        GitChangeProvider provider = providers.stream()
                .filter(candidate -> candidate.providerType() == reference.provider())
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "No Git change provider is configured for " + reference.provider() + "."));
        GitChangeSet fetched = provider.fetchChanges(reference);
        List<ChangedFile> classified = fetched.files().stream()
                .map(file -> new ChangedFile(
                        file.path(),
                        file.previousPath(),
                        file.changeType(),
                        classifier.classify(file.path()),
                        classifier.moduleHint(file.path()),
                        file.additions(),
                        file.deletions(),
                        file.changes()))
                .toList();
        GitChangeSet normalized = new GitChangeSet(
                reference.pipelineRunId(),
                reference.repositoryId(),
                fetched.baseSha(),
                fetched.headSha(),
                reference.pullRequestNumber(),
                fetched.provider(),
                classified.size(),
                classified.stream().mapToInt(ChangedFile::additions).sum(),
                classified.stream().mapToInt(ChangedFile::deletions).sum(),
                classified);
        return persistence.save(normalized);
    }

    public GitChangeSet get(UUID pipelineRunId) {
        return persistence.findByPipelineRunId(pipelineRunId);
    }
}

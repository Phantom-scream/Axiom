package com.axiom.integrations.ci;

import com.axiom.domain.pipeline.CiProviderType;
import com.axiom.domain.pipeline.GitChangeReference;
import com.axiom.domain.pipeline.GitChangeSet;

public interface GitChangeProvider {
    CiProviderType providerType();

    GitChangeSet fetchChanges(GitChangeReference reference);
}

package com.axiom.integrations.github.exception;

/** Unsafe create may have reached GitHub; do not automatically repeat it. */
public class GitHubPublicationOutcomeUnknownException extends ExternalProviderUnavailableException {
    public GitHubPublicationOutcomeUnknownException() { super(false); }
    @Override public String getMessage() { return "GitHub publication outcome is unknown; reconcile external reports before retrying."; }
}

package com.axiom.application.publication;

import com.axiom.domain.triage.PipelineTriageResult;
import com.axiom.config.OperationalLimitsProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class GitHubPrCommentRenderer {
    public static final String MARKER = "<!-- axiom-ci-intelligence -->";
    private final GitHubCheckRenderer triageRenderer;
    private final int commentLimit;

    public GitHubPrCommentRenderer(GitHubCheckRenderer triageRenderer) {
        this(triageRenderer, 60_000);
    }

    private GitHubPrCommentRenderer(GitHubCheckRenderer triageRenderer, int commentLimit) {
        this.triageRenderer = triageRenderer;
        this.commentLimit = commentLimit;
    }

    @Autowired
    public GitHubPrCommentRenderer(
            GitHubCheckRenderer triageRenderer, OperationalLimitsProperties limits) {
        this(triageRenderer, limits.effectivePublishedMarkdownCharacters());
    }

    public String render(PipelineTriageResult triage) {
        var rendered = triageRenderer.render(triage);
        String body = MARKER + "\n## Axiom CI Intelligence\n\n" + rendered.summary()
                + "\n\n<details>\n<summary>Ranked failure details</summary>\n\n"
                + rendered.text()
                + "\n\n</details>\n";
        if (body.length() <= commentLimit) return body;
        return body.substring(0, commentLimit - 24) + "\n\n_Report truncated._\n";
    }
}

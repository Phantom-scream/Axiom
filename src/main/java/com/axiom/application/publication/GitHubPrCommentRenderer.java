package com.axiom.application.publication;

import com.axiom.domain.triage.PipelineTriageResult;
import org.springframework.stereotype.Component;

@Component
public class GitHubPrCommentRenderer {
    public static final String MARKER = "<!-- axiom-ci-intelligence -->";
    private static final int COMMENT_LIMIT = 60_000;

    private final GitHubCheckRenderer triageRenderer;

    public GitHubPrCommentRenderer(GitHubCheckRenderer triageRenderer) {
        this.triageRenderer = triageRenderer;
    }

    public String render(PipelineTriageResult triage) {
        var rendered = triageRenderer.render(triage);
        String body = MARKER + "\n## Axiom CI Intelligence\n\n" + rendered.summary()
                + "\n\n<details>\n<summary>Ranked failure details</summary>\n\n"
                + rendered.text()
                + "\n\n</details>\n";
        if (body.length() <= COMMENT_LIMIT) return body;
        return body.substring(0, COMMENT_LIMIT - 24) + "\n\n_Report truncated._\n";
    }
}

package com.axiom.api.controller;

import com.axiom.api.dto.GitHubWebhookResponse;
import com.axiom.application.webhook.GitHubWebhookService;
import com.axiom.config.WebhookProperties;
import com.axiom.api.error.WebhookPayloadTooLargeException;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/webhooks")
public class GitHubWebhookController {
    private final GitHubWebhookService webhooks;
    private final WebhookProperties properties;

    public GitHubWebhookController(GitHubWebhookService webhooks, WebhookProperties properties) {
        this.webhooks = webhooks;
        this.properties = properties;
    }

    @PostMapping("/github")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public GitHubWebhookResponse receive(
            @RequestHeader("X-GitHub-Event") String event,
            @RequestHeader("X-GitHub-Delivery") String deliveryId,
            @RequestHeader(value = "X-Hub-Signature-256", required = false) String signature,
            HttpServletRequest request) throws IOException {
        int maximum = properties.effectiveMaxPayloadBytes();
        if (request.getContentLengthLong() > maximum) {
            throw new WebhookPayloadTooLargeException(maximum);
        }
        byte[] body = request.getInputStream().readNBytes(maximum + 1);
        if (body.length > maximum) throw new WebhookPayloadTooLargeException(maximum);
        return GitHubWebhookResponse.from(webhooks.receive(event, deliveryId, signature, body));
    }
}

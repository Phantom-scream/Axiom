package com.axiom.api.dto;

import com.axiom.application.webhook.GitHubWebhookService;

public record GitHubWebhookResponse(
        String deliveryId, String event, String status, boolean duplicate) {
    public static GitHubWebhookResponse from(GitHubWebhookService.Receipt receipt) {
        return new GitHubWebhookResponse(
                receipt.deliveryId(), receipt.event(), receipt.status(), receipt.duplicate());
    }
}

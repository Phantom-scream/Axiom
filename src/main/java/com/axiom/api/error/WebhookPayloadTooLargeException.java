package com.axiom.api.error;

public class WebhookPayloadTooLargeException extends RuntimeException {
    public WebhookPayloadTooLargeException(int maximumBytes) {
        super("The GitHub webhook payload exceeds the " + maximumBytes + " byte limit.");
    }
}

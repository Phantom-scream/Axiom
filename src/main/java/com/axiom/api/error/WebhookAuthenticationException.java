package com.axiom.api.error;

public class WebhookAuthenticationException extends RuntimeException {
    public WebhookAuthenticationException() {
        super("The GitHub webhook signature is missing or invalid.");
    }
}

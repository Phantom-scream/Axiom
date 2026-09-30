package com.axiom.application.webhook;

import com.axiom.api.error.WebhookAuthenticationException;
import com.axiom.config.GitHubProperties;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

@Component
public class GitHubWebhookSignatureVerifier {
    private static final String PREFIX = "sha256=";
    private final GitHubProperties properties;

    public GitHubWebhookSignatureVerifier(GitHubProperties properties) {
        this.properties = properties;
    }

    public void verify(byte[] payload, String signature) {
        if (signature == null || !signature.startsWith(PREFIX)) {
            throw new WebhookAuthenticationException();
        }
        String secret = properties.webhookSecret();
        if (secret == null || secret.isBlank()) throw new WebhookAuthenticationException();
        byte[] supplied;
        try {
            supplied = HexFormat.of().parseHex(signature.substring(PREFIX.length()));
        } catch (IllegalArgumentException exception) {
            throw new WebhookAuthenticationException();
        }
        if (!MessageDigest.isEqual(hmac(payload, secret), supplied)) {
            throw new WebhookAuthenticationException();
        }
    }

    private byte[] hmac(byte[] payload, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(payload);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("HMAC SHA-256 is unavailable.", exception);
        }
    }
}

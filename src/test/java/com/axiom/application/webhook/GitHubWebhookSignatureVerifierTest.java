package com.axiom.application.webhook;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.axiom.api.error.WebhookAuthenticationException;
import com.axiom.config.GitHubProperties;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

class GitHubWebhookSignatureVerifierTest {
    private static final String SECRET = "test-webhook-secret";
    private final GitHubWebhookSignatureVerifier verifier =
            new GitHubWebhookSignatureVerifier(properties());

    @Test
    void acceptsValidHmacAndRejectsMissingInvalidOrModifiedPayloads() throws Exception {
        byte[] body = "{\"action\":\"completed\"}".getBytes(StandardCharsets.UTF_8);
        String signature = sign(body);

        assertThatCode(() -> verifier.verify(body, signature)).doesNotThrowAnyException();
        assertThatThrownBy(() -> verifier.verify(body, null))
                .isInstanceOf(WebhookAuthenticationException.class);
        assertThatThrownBy(() -> verifier.verify(body, "sha256=00"))
                .isInstanceOf(WebhookAuthenticationException.class);
        assertThatThrownBy(() -> verifier.verify(
                        "modified".getBytes(StandardCharsets.UTF_8), signature))
                .isInstanceOf(WebhookAuthenticationException.class);
    }

    private String sign(byte[] body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return "sha256=" + HexFormat.of().formatHex(mac.doFinal(body));
    }

    private GitHubProperties properties() {
        return new GitHubProperties(
                null, null, SECRET, false, false, null, null, null, 1, null, null);
    }
}

package com.axiom.api.error;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import com.axiom.integrations.github.exception.*;

@RestControllerAdvice
public class ApiExceptionHandler {
    private final Clock clock;
    public ApiExceptionHandler(Clock clock) { this.clock = clock; }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> validation(MethodArgumentNotValidException exception, HttpServletRequest request) {
        String message = exception.getBindingResult().getFieldErrors().stream().findFirst().map(FieldError::getDefaultMessage).orElse("Request validation failed");
        return response(HttpStatus.BAD_REQUEST, message, request);
    }
    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ApiError> illegalArgument(IllegalArgumentException exception, HttpServletRequest request) { return response(HttpStatus.BAD_REQUEST, exception.getMessage(), request); }
    @ExceptionHandler(PipelineRunNotFoundException.class)
    ResponseEntity<ApiError> notFound(PipelineRunNotFoundException e,HttpServletRequest r){return response(HttpStatus.NOT_FOUND,e.getMessage(),r,"PIPELINE_RUN_NOT_FOUND");}
    @ExceptionHandler(ResourceNotFoundException.class)
    ResponseEntity<ApiError> resourceNotFound(ResourceNotFoundException e,HttpServletRequest r){return response(HttpStatus.NOT_FOUND,e.getMessage(),r,"RESOURCE_NOT_FOUND");}
    @ExceptionHandler(AnalysisPrerequisiteException.class)
    ResponseEntity<ApiError> analysisPrerequisite(AnalysisPrerequisiteException e,HttpServletRequest r){return response(HttpStatus.CONFLICT,e.getMessage(),r,"ANALYSIS_PREREQUISITE_MISSING");}
    @ExceptionHandler(GitComparisonUnavailableException.class)
    ResponseEntity<ApiError> comparisonUnavailable(GitComparisonUnavailableException e,HttpServletRequest r){return response(HttpStatus.UNPROCESSABLE_CONTENT,e.getMessage(),r,"GIT_COMPARISON_UNAVAILABLE");}
    @ExceptionHandler(GitHubComparisonNotFoundException.class)
    ResponseEntity<ApiError> comparisonNotFound(GitHubComparisonNotFoundException e,HttpServletRequest r){return response(HttpStatus.NOT_FOUND,e.getMessage(),r,"GITHUB_COMPARISON_NOT_FOUND");}
    @ExceptionHandler(GitHubCheckTargetNotFoundException.class)
    ResponseEntity<ApiError> checkTargetNotFound(GitHubCheckTargetNotFoundException e,HttpServletRequest r){return response(HttpStatus.NOT_FOUND,e.getMessage(),r,"GITHUB_CHECK_TARGET_NOT_FOUND");}
    @ExceptionHandler(GitHubPullRequestNotFoundException.class)
    ResponseEntity<ApiError> pullRequestNotFound(GitHubPullRequestNotFoundException e,HttpServletRequest r){return response(HttpStatus.NOT_FOUND,e.getMessage(),r,"GITHUB_PULL_REQUEST_NOT_FOUND");}
    @ExceptionHandler(InvalidGitHubComparisonException.class)
    ResponseEntity<ApiError> invalidComparison(InvalidGitHubComparisonException e,HttpServletRequest r){return response(HttpStatus.UNPROCESSABLE_CONTENT,e.getMessage(),r,"GITHUB_COMPARISON_INVALID");}
    @ExceptionHandler(InvalidGitHubCheckException.class)
    ResponseEntity<ApiError> invalidCheck(InvalidGitHubCheckException e,HttpServletRequest r){return response(HttpStatus.UNPROCESSABLE_CONTENT,e.getMessage(),r,"GITHUB_CHECK_INVALID");}
    @ExceptionHandler(InvalidGitHubCommentException.class)
    ResponseEntity<ApiError> invalidComment(InvalidGitHubCommentException e,HttpServletRequest r){return response(HttpStatus.UNPROCESSABLE_CONTENT,e.getMessage(),r,"GITHUB_COMMENT_INVALID");}
    @ExceptionHandler(GitHubAuthenticationException.class)
    ResponseEntity<ApiError> authentication(GitHubAuthenticationException e,HttpServletRequest r){return response(HttpStatus.UNAUTHORIZED,e.getMessage(),r,"GITHUB_AUTHENTICATION_FAILED");}
    @ExceptionHandler({GitHubPermissionException.class,GitHubRateLimitException.class})
    ResponseEntity<ApiError> forbidden(GitHubIntegrationException e,HttpServletRequest r){return response(HttpStatus.FORBIDDEN,e.getMessage(),r,e instanceof GitHubRateLimitException?"GITHUB_RATE_LIMITED":"GITHUB_PERMISSION_DENIED");}
    @ExceptionHandler(ExternalProviderUnavailableException.class)
    ResponseEntity<ApiError> unavailable(ExternalProviderUnavailableException e,HttpServletRequest r){return response(HttpStatus.SERVICE_UNAVAILABLE,e.getMessage(),r,"EXTERNAL_PROVIDER_UNAVAILABLE");}
    @ExceptionHandler(WebhookAuthenticationException.class)
    ResponseEntity<ApiError> webhookAuthentication(WebhookAuthenticationException e,HttpServletRequest r){return response(HttpStatus.UNAUTHORIZED,e.getMessage(),r,"GITHUB_WEBHOOK_SIGNATURE_INVALID");}
    @ExceptionHandler(WebhookPayloadTooLargeException.class)
    ResponseEntity<ApiError> webhookPayloadTooLarge(WebhookPayloadTooLargeException e,HttpServletRequest r){return response(HttpStatus.PAYLOAD_TOO_LARGE,e.getMessage(),r,"GITHUB_WEBHOOK_PAYLOAD_TOO_LARGE");}
    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> unexpected(Exception exception, HttpServletRequest request) {
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred.", request);
    }
    private ResponseEntity<ApiError> response(HttpStatus status, String message, HttpServletRequest request) {
        return response(status,message,request,status.getReasonPhrase());
    }
    private ResponseEntity<ApiError> response(HttpStatus status, String message, HttpServletRequest request,String error) {
        String correlationId = Optional.ofNullable(MDC.get("correlationId")).orElse("unknown");
        return ResponseEntity.status(status).body(new ApiError(Instant.now(clock), status.value(), error, message, request.getRequestURI(), correlationId));
    }
}

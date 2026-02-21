package com.moveinsync.mdm.exception;

import com.moveinsync.mdm.dto.response.ApiErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.stream.Collectors;

/**
 * Global exception handler providing consistent error responses.
 * Every exception maps to a specific HTTP status and error code.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(DeviceNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleDeviceNotFound(DeviceNotFoundException ex) {
        log.warn("Device not found: {}", ex.getMessage());
        return buildResponse(HttpStatus.NOT_FOUND, "DEVICE_NOT_FOUND", ex.getMessage());
    }

    @ExceptionHandler(DeviceAlreadyExistsException.class)
    public ResponseEntity<ApiErrorResponse> handleDeviceAlreadyExists(DeviceAlreadyExistsException ex) {
        log.warn("Device already exists: {}", ex.getMessage());
        return buildResponse(HttpStatus.CONFLICT, "DEVICE_ALREADY_EXISTS", ex.getMessage());
    }

    @ExceptionHandler(VersionAlreadyExistsException.class)
    public ResponseEntity<ApiErrorResponse> handleVersionAlreadyExists(VersionAlreadyExistsException ex) {
        log.warn("Version already exists: {}", ex.getMessage());
        return buildResponse(HttpStatus.CONFLICT, "VERSION_ALREADY_EXISTS", ex.getMessage());
    }

    @ExceptionHandler(DowngradeNotAllowedException.class)
    public ResponseEntity<ApiErrorResponse> handleDowngradeNotAllowed(DowngradeNotAllowedException ex) {
        log.warn("Downgrade attempt blocked: {}", ex.getMessage());
        return buildResponse(HttpStatus.BAD_REQUEST, "DOWNGRADE_NOT_ALLOWED", ex.getMessage());
    }

    @ExceptionHandler(NoUpgradePathException.class)
    public ResponseEntity<ApiErrorResponse> handleNoUpgradePath(NoUpgradePathException ex) {
        log.warn("No upgrade path: {}", ex.getMessage());
        return buildResponse(HttpStatus.BAD_REQUEST, "NO_UPGRADE_PATH", ex.getMessage());
    }

    @ExceptionHandler(InvalidStateTransitionException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidStateTransition(InvalidStateTransitionException ex) {
        log.warn("Invalid state transition: {}", ex.getMessage());
        return buildResponse(HttpStatus.BAD_REQUEST, "INVALID_STATE_TRANSITION", ex.getMessage());
    }

    @ExceptionHandler(ScheduleNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleScheduleNotFound(ScheduleNotFoundException ex) {
        log.warn("Schedule not found: {}", ex.getMessage());
        return buildResponse(HttpStatus.NOT_FOUND, "SCHEDULE_NOT_FOUND", ex.getMessage());
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiErrorResponse> handleBadCredentials(BadCredentialsException ex) {
        log.warn("Authentication failed: {}", ex.getMessage());
        return buildResponse(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Username or password is incorrect.");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        log.warn("Validation failed: {}", message);
        return buildResponse(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("Illegal argument: {}", ex.getMessage());
        return buildResponse(HttpStatus.BAD_REQUEST, "BAD_REQUEST", ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleGeneral(Exception ex) {
        log.error("Unexpected error: ", ex);
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "An unexpected error occurred. Please try again later.");
    }

    private ResponseEntity<ApiErrorResponse> buildResponse(HttpStatus status, String error, String message) {
        return ResponseEntity.status(status).body(
                ApiErrorResponse.builder()
                        .error(error)
                        .message(message)
                        .timestamp(LocalDateTime.now())
                        .build());
    }
}

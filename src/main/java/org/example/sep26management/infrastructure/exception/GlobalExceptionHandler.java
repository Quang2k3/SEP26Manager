package org.example.sep26management.infrastructure.exception;

import lombok.extern.slf4j.Slf4j;
import org.example.sep26management.application.constants.LogMessages;
import org.example.sep26management.application.constants.MessageConstants;
import org.example.sep26management.application.dto.response.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

        @ExceptionHandler(MethodArgumentNotValidException.class)
        public ResponseEntity<ApiResponse<Map<String, String>>> handleValidationExceptions(
                        MethodArgumentNotValidException ex) {
                Map<String, String> errors = new HashMap<>();

                ex.getBindingResult().getAllErrors().forEach((error) -> {
                        String fieldName = ((FieldError) error).getField();
                        String errorMessage = error.getDefaultMessage();
                        errors.put(fieldName, errorMessage);
                });

                log.warn(LogMessages.EXCEPTION_VALIDATION_ERROR, errors);

                String firstError = errors.values().iterator().next();

                return ResponseEntity
                                .badRequest()
                                .body(ApiResponse.error(firstError));
        }

        @ExceptionHandler(BusinessException.class)
        public ResponseEntity<ApiResponse<Void>> handleBusinessException(
                        BusinessException ex,
                        WebRequest request) {
                log.warn(LogMessages.EXCEPTION_BUSINESS, ex.getMessage());

                return ResponseEntity
                                .badRequest()
                                .body(ApiResponse.error(ex.getMessage()));
        }

        @ExceptionHandler(UnauthorizedException.class)
        public ResponseEntity<ApiResponse<Void>> handleUnauthorizedException(
                        UnauthorizedException ex,
                        WebRequest request) {
                log.warn(LogMessages.EXCEPTION_UNAUTHORIZED, ex.getMessage());

                return ResponseEntity
                                .status(HttpStatus.UNAUTHORIZED)
                                .body(ApiResponse.error(ex.getMessage()));
        }

        @ExceptionHandler(AuthenticationException.class)
        public ResponseEntity<ApiResponse<Void>> handleAuthenticationException(
                        AuthenticationException ex,
                        WebRequest request) {
                log.warn(LogMessages.EXCEPTION_AUTHENTICATION_FAILED, ex.getMessage());

                return ResponseEntity
                                .status(HttpStatus.UNAUTHORIZED)
                                .body(ApiResponse.error(MessageConstants.AUTH_FAILED));
        }

        @ExceptionHandler(ResourceNotFoundException.class)
        public ResponseEntity<ApiResponse<Void>> handleResourceNotFoundException(
                        ResourceNotFoundException ex,
                        WebRequest request) {
                log.warn(LogMessages.EXCEPTION_RESOURCE_NOT_FOUND, ex.getMessage());

                return ResponseEntity
                                .status(HttpStatus.NOT_FOUND)
                                .body(ApiResponse.error(ex.getMessage()));
        }

        @ExceptionHandler(AccessDeniedException.class)
        public ResponseEntity<ApiResponse<Void>> handleAccessDeniedException(
                        AccessDeniedException ex,
                        WebRequest request) {
                log.warn(LogMessages.EXCEPTION_ACCESS_DENIED, ex.getMessage());

                return ResponseEntity
                                .status(HttpStatus.FORBIDDEN)
                                .body(ApiResponse.error(MessageConstants.ACCESS_DENIED));
        }

        @ExceptionHandler(ForbiddenException.class)
        public ResponseEntity<ApiResponse<Void>> handleForbiddenException(
                        ForbiddenException ex,
                        WebRequest request) {
                log.warn(LogMessages.EXCEPTION_FORBIDDEN, ex.getMessage());

                return ResponseEntity
                                .status(HttpStatus.FORBIDDEN)
                                .body(ApiResponse.error(ex.getMessage()));
        }

        @ExceptionHandler(MaxUploadSizeExceededException.class)
        public ResponseEntity<ApiResponse<Void>> handleMaxSizeException(
                        MaxUploadSizeExceededException ex) {
                log.warn(LogMessages.EXCEPTION_FILE_SIZE_EXCEEDED, ex.getMessage());

                return ResponseEntity
                                .status(HttpStatus.PAYLOAD_TOO_LARGE)
                                .body(ApiResponse.error(MessageConstants.FILE_TOO_LARGE));
        }

        @ExceptionHandler(Exception.class)
        public ResponseEntity<ApiResponse<Void>> handleGlobalException(
                        Exception ex,
                        WebRequest request) {
                log.error(LogMessages.EXCEPTION_UNEXPECTED, ex);

                return ResponseEntity
                                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                                .body(ApiResponse.error(MessageConstants.UNEXPECTED_ERROR));
        }
}
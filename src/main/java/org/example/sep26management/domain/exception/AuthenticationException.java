package org.example.sep26management.domain.exception;

public class AuthenticationException extends DomainException {
    
    public AuthenticationException(String message) {
        super(message);
    }
    
    public AuthenticationException(String message, Throwable cause) {
        super(message, cause);
    }
}


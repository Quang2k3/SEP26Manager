package org.example.sep26management.domain.exception;

public class InvalidCredentialsException extends AuthenticationException {
    
    public InvalidCredentialsException() {
        super("Invalid username or password");
    }
    
    public InvalidCredentialsException(String message) {
        super(message);
    }
}


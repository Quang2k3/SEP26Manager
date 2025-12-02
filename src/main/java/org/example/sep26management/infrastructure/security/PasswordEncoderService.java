package org.example.sep26management.infrastructure.security;

public interface PasswordEncoderService {
    String encode(String rawPassword);
    boolean matches(String rawPassword, String encodedPassword);
}


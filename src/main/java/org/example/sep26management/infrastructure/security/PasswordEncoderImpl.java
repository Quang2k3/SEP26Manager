package org.example.sep26management.infrastructure.security;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class PasswordEncoderImpl implements PasswordEncoderService {
    
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    
    @Override
    public String encode(String rawPassword) {
        return encoder.encode(rawPassword);
    }
    
    @Override
    public boolean matches(String rawPassword, String encodedPassword) {
        return encoder.matches(rawPassword, encodedPassword);
    }
}


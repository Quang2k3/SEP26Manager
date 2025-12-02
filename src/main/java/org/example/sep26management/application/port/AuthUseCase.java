package org.example.sep26management.application.port;

import org.example.sep26management.application.dto.LoginRequest;
import org.example.sep26management.application.dto.LoginResponse;
import org.example.sep26management.application.dto.RegisterRequest;
import org.example.sep26management.application.dto.UserDto;

public interface AuthUseCase {
    
    LoginResponse login(LoginRequest request);
    
    UserDto register(RegisterRequest request);
    
    UserDto getCurrentUser(String token);
}


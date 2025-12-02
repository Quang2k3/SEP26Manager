package org.example.sep26management.presentation.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.sep26management.application.dto.LoginRequest;
import org.example.sep26management.application.dto.LoginResponse;
import org.example.sep26management.application.dto.RegisterRequest;
import org.example.sep26management.application.dto.UserDto;
import org.example.sep26management.application.port.AuthUseCase;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Authentication API endpoints for login, register and user management")
public class AuthController {
    
    private final AuthUseCase authUseCase;
    
    @Operation(
        summary = "User login",
        description = "Authenticate user with username and password. Returns JWT token on success."
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Login successful",
            content = @Content(schema = @Schema(implementation = LoginResponse.class))
        ),
        @ApiResponse(
            responseCode = "401",
            description = "Invalid credentials",
            content = @Content
        ),
        @ApiResponse(
            responseCode = "400",
            description = "Validation error",
            content = @Content
        )
    })
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = authUseCase.login(request);
        return ResponseEntity.ok(response);
    }
    
    @Operation(
        summary = "User registration",
        description = "Register a new user account. Username and email must be unique."
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "201",
            description = "User created successfully",
            content = @Content(schema = @Schema(implementation = UserDto.class))
        ),
        @ApiResponse(
            responseCode = "400",
            description = "Username or email already exists, or validation error",
            content = @Content
        )
    })
    @PostMapping("/register")
    public ResponseEntity<UserDto> register(@Valid @RequestBody RegisterRequest request) {
        UserDto user = authUseCase.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(user);
    }
    
    @Operation(
        summary = "Get current user",
        description = "Get information about the currently authenticated user. Requires JWT token."
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "User information retrieved successfully",
            content = @Content(schema = @Schema(implementation = UserDto.class))
        ),
        @ApiResponse(
            responseCode = "401",
            description = "Invalid or missing token",
            content = @Content
        )
    })
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/me")
    public ResponseEntity<UserDto> getCurrentUser(@RequestHeader("Authorization") String token) {
        UserDto user = authUseCase.getCurrentUser(token);
        return ResponseEntity.ok(user);
    }
}


package org.example.sep26management.infrastructure.config;

import org.example.sep26management.infrastructure.security.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of("*"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("Authorization", "Content-Type"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // ── Public ───────────────────────────────────────────────────
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/ws/**", "/ws/chat/**").permitAll()
                        .requestMatchers(
                                "/v1/auth/**",
                                "/v1/test/**",
                                "/v1/ping",
                                "/v1/scanner/**",
                                "/uploads/**",
                                "/js/**",
                                "/actuator/**",
                                "/api/actuator/**",
                                "/swagger-ui/**",
                                "/api/swagger-ui/**",
                                "/v3/api-docs/**",
                                "/v1/health/**",
                                "/v1/outbound/sales-orders/*/signed-note",
                                "/v1/outbound/sales-orders/*/pick-signed-note",
                                "/v1/putaway-tasks/*/signed-note",
                                "/v1/attachments/session/**"
                        ).permitAll()

                        // ── Scanner OTP: verify PUBLIC (mobile belum punya JWT) ───────
                        // generate endpoint diproteksi @PreAuthorize di controller
                        .requestMatchers("/v1/scanner-otp/verify", "/v1/scanner-otp/cleanup").permitAll()

                        // ── Scan events ───────────────────────────────────────────────
                        .requestMatchers("/v1/scan-events", "/api/v1/scan-events").hasAnyRole("KEEPER", "QC")

                        // ── Attachments ───────────────────────────────────────────────
                        .requestMatchers("/v1/attachments/upload").hasAnyRole("KEEPER", "QC", "MANAGER")

                        // ── Manager only ──────────────────────────────────────────────
                        .requestMatchers("/v1/users/**").hasRole("MANAGER")

                        // ── Zones ─────────────────────────────────────────────────────
                        .requestMatchers(HttpMethod.GET, "/v1/zones/**").hasAnyRole("MANAGER", "KEEPER")
                        .requestMatchers("/v1/zones/**").hasRole("MANAGER")
                        .requestMatchers("/v1/category-zone-mappings/**").hasRole("MANAGER")
                        .requestMatchers("/v1/categories/**").hasAnyRole("MANAGER")
                        .requestMatchers("/v1/skus/**").authenticated()

                        // ── Locations ─────────────────────────────────────────────────
                        .requestMatchers(HttpMethod.GET, "/v1/locations/**").hasAnyRole("MANAGER", "KEEPER")
                        .requestMatchers("/v1/locations/**").hasRole("MANAGER")

                        // ── Everything else requires auth ─────────────────────────────
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
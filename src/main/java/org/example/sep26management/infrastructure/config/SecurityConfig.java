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

        // Sử dụng allowedOriginPatterns("*") kết hợp với allowCredentials(true)
        // sẽ tự động reflect Origin của request về response, đáp ứng yêu cầu của Axios.
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
                        // Public endpoints
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/ws/**", "/ws/chat/**").permitAll()
                        .requestMatchers(
                                "/v1/auth/**",
                                "/v1/test/**",
                                "/v1/ping",
                                "/v1/scanner/**",
                                "/uploads/**", // static uploaded files (avatars, etc.)
                                "/js/**", // static JS files (html5-qrcode, etc.)
                                "/actuator/**",
                                "/api/actuator/**",
                                "/swagger-ui/**",
                                "/api/swagger-ui/**",
                                "/v3/api-docs/**",
                                "/v3/api-docs/**", // Swagger JSON endpoints "/v1/health",
                                "/v1/health/**",
                                "/v1/outbound/sales-orders/*/signed-note",      // QR upload outbound — no auth needed
                                "/v1/outbound/sales-orders/*/pick-signed-note",  // QR upload pick note — no auth needed
                                "/v1/putaway-tasks/*/signed-note"                // QR upload putaway — no auth needed
                        )
                        .permitAll()
                        // Scan events — requires KEEPER or QC role (iPhone scanner)
                        .requestMatchers("/v1/scan-events", "/api/v1/scan-events").hasAnyRole("KEEPER", "QC")
                        // [FIX QC] Upload anh hang hong tu dien thoai scan QC
                        .requestMatchers("/v1/attachments/upload").hasAnyRole("KEEPER", "QC", "MANAGER")
                        // Manager only endpoints
                        .requestMatchers("/v1/users/**").hasRole("MANAGER")
                        // Zones: KEEPER cần GET để chọn zone khi làm putaway
                        .requestMatchers(HttpMethod.GET, "/v1/zones/**").hasAnyRole("MANAGER", "KEEPER")
                        .requestMatchers("/v1/zones/**").hasRole("MANAGER")
                        .requestMatchers("/v1/category-zone-mappings/**").hasRole("MANAGER") // Zone Management
                        .requestMatchers("/v1/categories/**").hasAnyRole("MANAGER")
                        .requestMatchers("/v1/skus/**").authenticated()
                        // Locations: KEEPER cần GET để load parent AISLE/RACK trong putaway
                        .requestMatchers(HttpMethod.GET, "/v1/locations/**").hasAnyRole("MANAGER", "KEEPER")
                        .requestMatchers("/v1/locations/**").hasRole("MANAGER")
                        // Authenticated endpoints
                        .anyRequest().authenticated())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
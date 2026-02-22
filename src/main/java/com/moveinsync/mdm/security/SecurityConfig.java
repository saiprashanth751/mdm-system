package com.moveinsync.mdm.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthFilter;
    private final UserDetailsService userDetailsService;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        // Public endpoints — only login is unauthenticated
                        .requestMatchers("/api/v1/auth/login").permitAll()
                        // Register requires SUPER_ADMIN — prevents unauthorized account creation
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/register").hasRole("SUPER_ADMIN")

                        // Device-facing endpoints (no admin auth — devices use IMEI)
                        .requestMatchers("/api/v1/devices/register").permitAll()
                        .requestMatchers("/api/v1/devices/heartbeat").permitAll()
                        .requestMatchers(HttpMethod.PUT, "/api/v1/updates/*/status").permitAll()

                        // Swagger / OpenAPI
                        .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/api-docs/**", "/v3/api-docs/**")
                        .permitAll()

                        // Actuator — health, info, and prometheus for monitoring scraper
                        .requestMatchers("/actuator/health", "/actuator/info", "/actuator/prometheus").permitAll()

                        // Admin-only: approve/reject schedules
                        .requestMatchers(HttpMethod.PUT, "/api/v1/updates/schedule/*/approve").hasRole("SUPER_ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/updates/schedule/*/reject").hasRole("SUPER_ADMIN")

                        // Release engineers + super admins: manage versions and schedules
                        .requestMatchers(HttpMethod.POST, "/api/v1/versions/**")
                        .hasAnyRole("SUPER_ADMIN", "RELEASE_ENGINEER")
                        .requestMatchers(HttpMethod.POST, "/api/v1/updates/schedule")
                        .hasAnyRole("SUPER_ADMIN", "RELEASE_ENGINEER")

                        // All authenticated admins: read operations
                        .anyRequest().authenticated())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authenticationProvider(authenticationProvider())
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}

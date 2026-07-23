package com.BRIXO.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // ── Autorización ─────────────────────────────────────────────
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/", "/home", "/login", "/registro",
                                 "/css/**", "/js/**", "/images/**", "/webjars/**").permitAll()
                .requestMatchers("/admin", "/admin/**").hasRole("ADMIN")
                .requestMatchers("/dashboard", "/servicios/**").authenticated()
                .anyRequest().authenticated()
            )

            // ── Login / Logout ────────────────────────────────────────────
            .formLogin(form -> form
                .loginPage("/login")
                .successHandler((req, res, auth) -> res.sendRedirect(req.getContextPath() + "/"))
                .failureUrl("/login?error")
                .permitAll()
            )
            .logout(logout -> logout
                .logoutSuccessUrl("/login?logout")
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID")
                .permitAll()
            )

            // ── Sesión ────────────────────────────────────────────────────
            .sessionManagement(session -> session
                .sessionFixation().migrateSession()   // previene session fixation
                .invalidSessionUrl("/login?expired")
                .maximumSessions(1)                   // un solo dispositivo por usuario
                .expiredUrl("/login?expired")
            )

            // ── Security headers ──────────────────────────────────────────
            .headers(headers -> headers
                .frameOptions(frame -> frame.sameOrigin())
                .contentTypeOptions(ct -> {})
                .referrerPolicy(ref -> ref
                    .policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                .addHeaderWriter((req, res) ->
                    res.setHeader("Permissions-Policy", "camera=(), microphone=(), geolocation=()"))
            );

        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
                .connectTimeout(Duration.ofSeconds(10))
                .readTimeout(Duration.ofSeconds(60))
                .build();
    }
}

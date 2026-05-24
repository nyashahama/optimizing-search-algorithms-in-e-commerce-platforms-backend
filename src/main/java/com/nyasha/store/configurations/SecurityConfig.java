package com.nyasha.store.configurations;

import com.nyasha.store.security.ApiRateLimitFilter;
import com.nyasha.store.observability.RequestIdFilter;
import jakarta.servlet.Filter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

import static org.springframework.security.config.Customizer.withDefaults;

@Configuration
public class SecurityConfig {
    private final boolean rateLimitEnabled;
    private final int rateLimitRequestsPerMinute;
    private final long rateLimitWindowMs;

    public SecurityConfig(
            @Value("${security.rate-limit.enabled:true}") boolean rateLimitEnabled,
            @Value("${security.rate-limit.requests-per-minute:120}") int rateLimitRequestsPerMinute,
            @Value("${security.rate-limit.window-ms:60000}") long rateLimitWindowMs
    ) {
        this.rateLimitEnabled = rateLimitEnabled;
        this.rateLimitRequestsPerMinute = rateLimitRequestsPerMinute;
        this.rateLimitWindowMs = rateLimitWindowMs;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        .requestMatchers(HttpMethod.GET,
                                "/api/products",
                                "/api/products/*",
                                "/api/products/search",
                                "/api/products/autocomplete",
                                "/api/products/category/**",
                                "/api/categories",
                                "/api/categories/*",
                                "/api/search",
                                "/api/search/**",
                                "/api/reviews/products/**"
                        ).permitAll()
                        .requestMatchers("/api/ops/status").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/users/register", "/users/login").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/carts/**").hasAnyRole("USER", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/carts/**").hasAnyRole("USER", "ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/api/carts/**").hasAnyRole("USER", "ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/carts/**").hasAnyRole("USER", "ADMIN")
                        .requestMatchers("/api/checkouts/**").hasAnyRole("USER", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/orders/me").hasAnyRole("USER", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/orders/*").hasAnyRole("USER", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/orders/*/pack").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/orders/*/ship").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/orders/*/delivered").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/orders/*/cancel").hasAnyRole("USER", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/payments/orders/*").hasAnyRole("USER", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/payments/orders/*/capture").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/payments/orders/*/refund").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/inventory/low-stock").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/inventory/**").hasAnyRole("USER", "ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/inventory/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/api/inventory/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/suppliers/**").hasAnyRole("USER", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/suppliers/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/suppliers/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/suppliers/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/returns/*").hasAnyRole("USER", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/reviews", "/api/reviews/**").hasAnyRole("USER", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/reviews").hasAnyRole("USER", "ADMIN")
                        .requestMatchers("/api/wishlists/**").hasAnyRole("USER", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/returns/*").hasAnyRole("USER", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/returns/*/approve").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/returns/*/reject").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/returns/*/refund").hasAnyRole("USER", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/addresses/me", "/api/addresses/me/**").hasAnyRole("USER", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/addresses/me").hasAnyRole("USER", "ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/addresses/me/**").hasAnyRole("USER", "ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/addresses/me/**").hasAnyRole("USER", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/users/**").hasAnyRole("USER", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/users/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/users/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/users/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/addresses/**").hasAnyRole("USER", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/products/**", "/api/categories/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/products/**", "/api/categories/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/products/**", "/api/categories/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/benchmarks/runs").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/benchmarks/runs").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/benchmarks/runs/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/index/rebuild").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/index/status").hasRole("ADMIN")
                        .requestMatchers("/actuator/**").hasRole("ADMIN")
                        .anyRequest().authenticated()
                )
                .addFilterBefore(rateLimitFilter(), org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(requestIdFilter(), org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter.class)
                .httpBasic(withDefaults());

        return http.build();
    }

    @Bean
    public Filter rateLimitFilter() {
        return new ApiRateLimitFilter(rateLimitEnabled, rateLimitRequestsPerMinute, rateLimitWindowMs, java.time.Clock.systemUTC());
    }

    @Bean
    public Filter requestIdFilter() {
        return new RequestIdFilter();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of("http://localhost:3000", "http://localhost:4200"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("*"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}

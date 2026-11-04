package com.panscience.assistant;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.*;
import jakarta.servlet.http.*;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.stereotype.Component;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

@Component
class JwtService {

 private final SecretKey key;

 JwtService(@Value("${app.jwt-secret}") String secret) {
  key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
 }

 String create(String orgId) {
  return Jwts.builder()
          .subject(orgId)
          .issuedAt(new Date())
          .expiration(new Date(System.currentTimeMillis() + 86400000L * 7))
          .signWith(key)
          .compact();
 }

 String validate(String token) {
  return Jwts.parser()
          .verifyWith(key)
          .build()
          .parseSignedClaims(token)
          .getPayload()
          .getSubject();
 }
}

@Component
class JwtFilter extends GenericFilter {

 private final JwtService jwt;

 JwtFilter(JwtService jwt) {
  this.jwt = jwt;
 }

 @Override
 public void doFilter(
         ServletRequest req,
         ServletResponse res,
         FilterChain chain
 ) throws java.io.IOException, ServletException {

  HttpServletRequest request = (HttpServletRequest) req;

  // CORS preflight requests must pass without JWT authentication.
  if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
   chain.doFilter(req, res);
   return;
  }

  String header = request.getHeader("Authorization");

  if (header != null && header.startsWith("Bearer ")) {
   try {
    String orgId = jwt.validate(header.substring(7));

    SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(
                    orgId,
                    null,
                    List.of()
            )
    );
   } catch (Exception ignored) {
    SecurityContextHolder.clearContext();
   }
  }

  chain.doFilter(req, res);
 }
}

@Configuration
class SecurityConfig {

 @Bean
 CorsConfigurationSource corsConfigurationSource(
         @Value("${app.frontend-origin:http://localhost:5173}") String origin
 ) {
  CorsConfiguration configuration = new CorsConfiguration();

  configuration.setAllowedOrigins(List.of(
          origin,
          "http://localhost:5173",
          "http://127.0.0.1:5173",
          "http://localhost:5500",
          "http://127.0.0.1:5500"
  ));

  configuration.setAllowedMethods(List.of(
          "GET",
          "POST",
          "PUT",
          "DELETE",
          "OPTIONS"
  ));

  configuration.setAllowedHeaders(List.of("*"));
  configuration.setAllowCredentials(true);
  configuration.setMaxAge(3600L);

  UrlBasedCorsConfigurationSource source =
          new UrlBasedCorsConfigurationSource();

  source.registerCorsConfiguration("/**", configuration);

  return source;
 }

 @Bean
 SecurityFilterChain filter(
         HttpSecurity http,
         JwtFilter jwtFilter,
         CorsConfigurationSource corsConfigurationSource
 ) throws Exception {

  return http
          .cors(cors -> cors.configurationSource(corsConfigurationSource))
          .csrf(csrf -> csrf.disable())
          .sessionManagement(session ->
                  session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
          )
          .authorizeHttpRequests(auth -> auth
                  .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                  .requestMatchers(
                          "/api/auth/**",
                          "/api/public/**",
                          "/api/assistant/*/config",
                          "/actuator/**"
                  ).permitAll()
                  .anyRequest().authenticated()
          )
          .addFilterBefore(
                  jwtFilter,
                  UsernamePasswordAuthenticationFilter.class
          )
          .build();
 }
}

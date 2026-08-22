package com.Patients.Security;

import com.Patients.Variables.SecretKeyValue;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.HttpServletRequest;
import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationProvider;
import org.springframework.security.oauth2.server.resource.authentication.JwtIssuerAuthenticationManagerResolver;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Resource-server-only Spring Security config.
 *
 * Handles ONE job: validating incoming Bearer JWTs from two different issuers
 * ("APPLICATION" — this app's own HS256 tokens, and Keycloak's RS256 tokens)
 * and turning a valid token into an Authentication with the right principal + authorities.
 *
 * Login (username/password -> JWT) and outbound OAuth2-client (client-credentials to
 * Keycloak) concerns live elsewhere — they are not part of resource-server validation.
 */
@Configuration
public class ResourceServerConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity httpSecurity,
            JwtIssuerAuthenticationManagerResolver jwtIssuerAuthenticationManagerResolver) throws Exception {

        return httpSecurity
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/error", "/create/patient", "/login").permitAll()
                        .anyRequest().authenticated()
                )

                // Every protected request's Bearer token is routed to the right
                // AuthenticationManager based on its "iss" claim.
                .oauth2ResourceServer(server ->
                        server.authenticationManagerResolver(jwtIssuerAuthenticationManagerResolver))

                .cors(cors -> cors.configurationSource(new CorsConfigurationSource() {
                    @Override
                    public @Nullable CorsConfiguration getCorsConfiguration(HttpServletRequest request) {
                        CorsConfiguration corsConfiguration = new CorsConfiguration();
                        corsConfiguration.setAllowedOrigins(List.of("*"));
                        corsConfiguration.setAllowedHeaders(List.of("*"));
                        corsConfiguration.setExposedHeaders(List.of("*"));
                        return corsConfiguration;
                    }
                }))
                .build();
    }

    @Bean
    public JwtIssuerAuthenticationManagerResolver jwtIssuerAuthenticationManagerResolver(
            RolePermissionConverter rolePermissionConverter) {

        // issuer string -> AuthenticationManager that knows how to validate tokens from it.
        Map<String, AuthenticationManager> authenticationManagerMap = new HashMap<>();

        String keyCloakIssuer = "http://localhost/realms/master";
        String keyCloakJwsSetUri = "http://localhost/realms/master/protocol/openid-connect/certs";
        String applicationIssuer = "APPLICATION";

        // --- Issuer #1: Keycloak. RS256, verified against Keycloak's public JWKS. ---
        NimbusJwtDecoder nimbusJwtDecoderKeyCloak =
                NimbusJwtDecoder.withJwkSetUri(keyCloakJwsSetUri).build();
        JwtAuthenticationProvider keycloakProvider =
                new JwtAuthenticationProvider(nimbusJwtDecoderKeyCloak);
        authenticationManagerMap.put(keyCloakIssuer, keycloakProvider::authenticate);

        // --- Issuer #2: this application. HS256, verified against our shared secret. ---
        SecretKey secretKey = Keys.hmacShaKeyFor(
                SecretKeyValue.SECRET_KEY.getBytes(StandardCharsets.UTF_8));
        NimbusJwtDecoder nimbusJwtDecoder =
                NimbusJwtDecoder.withSecretKey(secretKey)
                        .macAlgorithm(MacAlgorithm.HS256).build();
        JwtAuthenticationProvider applicationProvider =
                new JwtAuthenticationProvider(nimbusJwtDecoder);

        // Authorities come from RolePermissionConverter; principal name comes from
        // the "userId" claim instead of the default "sub".
        JwtAuthenticationConverter jwtAuthenticationConverter = new JwtAuthenticationConverter();
        jwtAuthenticationConverter.setJwtGrantedAuthoritiesConverter(rolePermissionConverter);
        jwtAuthenticationConverter.setPrincipalClaimName("userId");
        applicationProvider.setJwtAuthenticationConverter(jwtAuthenticationConverter);

        authenticationManagerMap.put(applicationIssuer, applicationProvider::authenticate);

        return new JwtIssuerAuthenticationManagerResolver(authenticationManagerMap::get);
    }
}

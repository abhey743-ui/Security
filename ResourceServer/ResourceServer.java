package com.Patients.Security;

import com.Patients.Repository.PatientCredentialsRepository;
import com.Patients.Repository.PatientRepository;
import com.Patients.Repository.RolePermissionRepository;
import com.Patients.Security.SecurityUtils.CustomAccessDeniedHandler;
import com.Patients.Security.SecurityUtils.CustomAuthenticationEntryPoint;
import com.Patients.Security.SecurityUtils.TokenGeneration;
import com.Patients.Security.SecurityUtils.UserDetailServiceImpl;
import com.Patients.Variables.SecretKeyValue;
import feign.RequestInterceptor;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.HttpServletRequest;
import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.*;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationProvider;
import org.springframework.security.oauth2.server.resource.authentication.JwtIssuerAuthenticationManagerResolver;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.logout.LogoutFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import tools.jackson.databind.ObjectMapper;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Central Spring Security configuration.
 *
 * This class configures the app as BOTH:
 *  1. An OAuth2 RESOURCE SERVER that must validate incoming JWTs from TWO different issuers:
 *       - "APPLICATION"  -> your own HS256 JWTs, minted after username/password login
 *       - Keycloak realm -> RS256 JWTs from Keycloak (OIDC / client-credentials)
 *  2. An OAuth2 CLIENT that fetches its own outbound client-credentials token from Keycloak
 *     to call other downstream services via Feign.
 *
 * See SECURITY_CONFIG.md for the full walkthrough.
 */
@Configuration
public class SecurityChain {

    // Handles how the CSRF token is attached to the request attributes so it can be
    // read later (e.g. rendered into a response header) — required boilerplate when
    // using CookieCsrfTokenRepository with the newer CSRF API.
    CsrfTokenRequestAttributeHandler csrfTokenRequestAttributeHandler = new CsrfTokenRequestAttributeHandler();

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity httpSecurity,
            TokenGeneration tokenGeneration,
            AuthenticationManager authenticationManager,
            ObjectMapper objectMapper,
            PatientRepository patientRepository,
            UsernamePasswordAuthenticationFilterImpl usernamePasswordAuthenticationFilter,
            JwtIssuerAuthenticationManagerResolver jwtIssuerAuthenticationManagerResolver,
            CookieCsrfTokenRepository cookieCsrfTokenRepository) {

        return httpSecurity
                // No HttpSession is created/used — every request authenticates itself via its JWT.
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // Public endpoints vs. everything-else-needs-a-valid-token.
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/error", "/create/patient", "/login").permitAll()
                        .anyRequest().authenticated()
                )

                // We're not using Spring's default HTML login form/flow.
                .formLogin(form -> form.disable())

                // CSRF token lives in a cookie (readable by JS since withHttpOnlyFalse()).
                // /contact is exempted from CSRF checks.
                .csrf(csrf -> csrf
                        .csrfTokenRepository(cookieCsrfTokenRepository.withHttpOnlyFalse())
                        .ignoringRequestMatchers("/contact")
                        .csrfTokenRequestHandler(csrfTokenRequestAttributeHandler))

                // Custom filter that (presumably) writes the CSRF token into the response
                // after your login filter has run.
                .addFilterAfter(new CsrfTokenFilterGen(), UsernamePasswordAuthenticationFilterImpl.class)

                // Your custom login filter: intercepts POST /login, authenticates credentials,
                // and mints the "APPLICATION"-issuer JWT via TokenGeneration on success.
                .addFilterAfter(usernamePasswordAuthenticationFilter, LogoutFilter.class)

                // *** THE MULTI-ISSUER RESOURCE SERVER WIRING ***
                // Every protected request's Bearer token is routed to the correct
                // AuthenticationManager based on its "iss" claim. See jwtIssuerAuthenticationManagerResolver().
                .oauth2ResourceServer(server ->
                        server.authenticationManagerResolver(jwtIssuerAuthenticationManagerResolver))

                // Custom 401 / 403 responses instead of Spring's default whitelabel pages.
                .exceptionHandling(exception ->
                        exception.authenticationEntryPoint(new CustomAuthenticationEntryPoint())
                                .accessDeniedHandler(new CustomAccessDeniedHandler())
                )

                // NOTE: wide-open CORS — fine for local dev, tighten before any real deployment.
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
    public CookieCsrfTokenRepository csrfTokenRepository() {
        return new CookieCsrfTokenRepository();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration) {
        return authenticationConfiguration.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        // Delegating encoder: auto-detects algorithm from a "{bcrypt}"/"{noop}"/etc. prefix
        // on stored hashes, and encodes NEW passwords with bcrypt by default.
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    public UsernamePasswordAuthenticationFilter usernamePasswordAuthenticationFilter(
            TokenGeneration tokenGeneration,
            ObjectMapper objectMapper,
            AuthenticationManager authenticationManager,
            PatientRepository patientRepository) {
        return new UsernamePasswordAuthenticationFilterImpl(
                patientRepository, tokenGeneration, objectMapper, authenticationManager);
    }

    @Bean
    public UserDetailsService userDetailsService(
            PatientCredentialsRepository patientCredentialsRepository,
            RolePermissionRepository rolePermissionRepository) {
        return new UserDetailServiceImpl(patientCredentialsRepository, rolePermissionRepository);
    }

    // ---------------------------------------------------------------------
    // OAuth2 CLIENT side: this service authenticating ITSELF to Keycloak
    // (client-credentials grant) so it can call other services.
    // ---------------------------------------------------------------------

    @Bean
    public OAuth2AuthorizedClientManager oAuth2AuthorizedClientManager(
            ClientRegistrationRepository clientRegistrationRepository,
            OAuth2AuthorizedClientRepository oAuth2AuthorizedClientRepository) {

        DefaultOAuth2AuthorizedClientManager defaultOAuth2AuthorizedClientManager =
                new DefaultOAuth2AuthorizedClientManager(clientRegistrationRepository, oAuth2AuthorizedClientRepository);

        OAuth2AuthorizedClientProvider oAuth2AuthorizedClientProvider =
                OAuth2AuthorizedClientProviderBuilder.builder().clientCredentials().build();

        defaultOAuth2AuthorizedClientManager.setAuthorizedClientProvider(oAuth2AuthorizedClientProvider);
        return defaultOAuth2AuthorizedClientManager;
    }

    @Bean
    public ClientRegistrationRepository clientRegistrationRepository() {
        return new InMemoryClientRegistrationRepository(keyCloak());
    }

    @Bean
    public ClientRegistration keyCloak() {
        // TODO: move clientId/clientSecret/tokenUri to application.yml / env vars / a secrets
        // manager. A live-looking secret hardcoded here should be treated as compromised.
        return ClientRegistration
                .withRegistrationId("keycloak")
                .clientId("PatientService")
                .clientSecret("2KnnN0IkagEsrmXdLkgbb3hlOS7I7GGvl2J69qgKgbMjJHSvdpmBpsdxGocjL6mKahL6q8viixPMVpzuIxbAGw")
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .tokenUri("http://localhost:8080/realms/master/protocol/openid-connect/token")
                .build();
    }

    // ---------------------------------------------------------------------
    // RESOURCE SERVER side: validating INCOMING JWTs from two issuers.
    // This is the core "multi-tenant auth" piece.
    // ---------------------------------------------------------------------

    @Bean
    public JwtIssuerAuthenticationManagerResolver jwtIssuerAuthenticationManagerResolver(
            RolePermissionRepository rolePermissionRepository,
            RolePermissionConverter rolePermissionConverter) {

        // Map of issuer string -> AuthenticationManager that knows how to validate
        // tokens from that specific issuer. Spring peeks at the unverified "iss"
        // claim first, uses it as the lookup key, THEN verifies the signature with
        // whichever manager matched.
        Map<String, AuthenticationManager> authenticationManagerMap = new HashMap<>();

        // Shared secret used to verify tokens minted by THIS application (HS256).
        SecretKey secretKey = Keys.hmacShaKeyFor(
                SecretKeyValue.SECRET_KEY.getBytes(StandardCharsets.UTF_8));

        String keyCloakIssuer = "http://localhost/realms/master";
        String keyCloakJwsSetUri = "http://localhost/realms/master/protocol/openid-connect/certs";
        String applicationIssuer = "APPLICATION";

        // --- Issuer #1: Keycloak. RS256, verified against Keycloak's public JWKS. ---
        NimbusJwtDecoder nimbusJwtDecoderKeyCloak =
                NimbusJwtDecoder.withJwkSetUri(keyCloakJwsSetUri).build();
        JwtAuthenticationProvider jwtAuthenticationProvider1 =
                new JwtAuthenticationProvider(nimbusJwtDecoderKeyCloak);
        authenticationManagerMap.put(keyCloakIssuer, jwtAuthenticationProvider1::authenticate);

        // --- Issuer #2: this application. HS256, verified against our shared secret. ---
        NimbusJwtDecoder nimbusJwtDecoder =
                NimbusJwtDecoder.withSecretKey(secretKey)
                        .macAlgorithm(MacAlgorithm.HS256).build();
        JwtAuthenticationProvider jwtAuthenticationProvider =
                new JwtAuthenticationProvider(nimbusJwtDecoder);

        // Custom converter: authorities come from RolePermissionConverter, and the
        // Authentication's principal name comes from the "userId" claim instead of "sub".
        JwtAuthenticationConverter jwtAuthenticationConverter = new JwtAuthenticationConverter();
        jwtAuthenticationConverter.setJwtGrantedAuthoritiesConverter(rolePermissionConverter);
        jwtAuthenticationConverter.setPrincipalClaimName("userId");
        jwtAuthenticationProvider.setJwtAuthenticationConverter(jwtAuthenticationConverter);

        authenticationManagerMap.put(applicationIssuer, jwtAuthenticationProvider::authenticate);

        // Resolver just does a map lookup keyed by the token's "iss" claim.
        return new JwtIssuerAuthenticationManagerResolver(authenticationManagerMap::get);
    }

    @Bean
    public RequestInterceptor requestInterceptor(OAuth2AuthorizedClientManager oAuth2AuthorizedClientManager) {
        // Runs on every outgoing Feign call: fetches (or reuses a cached) client-credentials
        // token for the "keycloak" registration, then stamps it onto the outgoing request.
        return restTemplate -> {
            OAuth2AuthorizeRequest oAuth2AuthorizeRequest = OAuth2AuthorizeRequest
                    .withClientRegistrationId("keycloak")
                    .principal("APPLICATION")
                    .build();
            OAuth2AuthorizedClient auth2AuthorizedClient =
                    oAuth2AuthorizedClientManager.authorize(oAuth2AuthorizeRequest);
            String accessToken = String.valueOf(auth2AuthorizedClient.getAccessToken());
            restTemplate.header("Authorization", "Bearer " + accessToken);
        };
    }
}

# Spring Security — Multi-Issuer OAuth2 Resource Server

This document explains the `SecurityChain` configuration: a Spring Security setup that acts as an
**OAuth2 Resource Server** capable of validating JWTs from **two different issuers** at the same time —
your own application-issued JWTs (login via username/password) and Keycloak-issued OIDC tokens.

---

## 1. The core idea: what is a Resource Server?

In OAuth2 terms, a **Resource Server** is any API that:
- Does **not** issue tokens itself (that's an Authorization Server's job — e.g. Keycloak).
- Only **validates incoming tokens** on each request and decides whether to allow access.

`spring-boot-starter-oauth2-resource-server` gives Spring Security the building blocks to do this:
decode a JWT, verify its signature, verify its claims (issuer, expiry, audience), and turn it into an
`Authentication` object that the rest of Spring Security understands.

Normally, a resource server trusts **one** issuer, configured with a single property:

```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: http://localhost:8080/realms/master
```

**Your case is different** — you have two issuers:
1. Your **own backend** issues HS256-signed JWTs after username/password login (`UsernamePasswordAuthenticationFilterImpl`).
2. **Keycloak** issues RS256-signed JWTs (standard OIDC) for client-credentials/service-to-service calls.

A single `issuer-uri` property can't handle two trust sources, so instead of the simple property-based
config, you configured it **programmatically** using `JwtIssuerAuthenticationManagerResolver`.

---

## 2. `JwtIssuerAuthenticationManagerResolver` — the heart of multi-tenancy

```java
@Bean
public JwtIssuerAuthenticationManagerResolver jwtIssuerAuthenticationManagerResolver(
        RolePermissionRepository rolePermissionRepository,
        RolePermissionConverter rolePermissionConverter) {

    Map<String, AuthenticationManager> authenticationManagerMap = new HashMap<>();

    SecretKey secretKey = Keys.hmacShaKeyFor(
            SecretKeyValue.SECRET_KEY.getBytes(StandardCharsets.UTF_8));

    String keyCloakIssuer     = "http://localhost/realms/master";
    String keyCloakJwsSetUri  = "http://localhost/realms/master/protocol/openid-connect/certs";
    String applicationIssuer  = "APPLICATION";

    // --- Decoder #1: Keycloak, verifies RS256 signature via JWKS endpoint ---
    NimbusJwtDecoder nimbusJwtDecoderKeyCloak =
            NimbusJwtDecoder.withJwkSetUri(keyCloakJwsSetUri).build();
    JwtAuthenticationProvider jwtAuthenticationProvider1 =
            new JwtAuthenticationProvider(nimbusJwtDecoderKeyCloak);
    authenticationManagerMap.put(keyCloakIssuer, jwtAuthenticationProvider1::authenticate);

    // --- Decoder #2: Your app, verifies HS256 signature via shared secret ---
    NimbusJwtDecoder nimbusJwtDecoder =
            NimbusJwtDecoder.withSecretKey(secretKey)
                    .macAlgorithm(MacAlgorithm.HS256).build();
    JwtAuthenticationProvider jwtAuthenticationProvider =
            new JwtAuthenticationProvider(nimbusJwtDecoder);

    JwtAuthenticationConverter jwtAuthenticationConverter = new JwtAuthenticationConverter();
    jwtAuthenticationConverter.setJwtGrantedAuthoritiesConverter(rolePermissionConverter);
    jwtAuthenticationConverter.setPrincipalClaimName("userId");
    jwtAuthenticationProvider.setJwtAuthenticationConverter(jwtAuthenticationConverter);

    authenticationManagerMap.put(applicationIssuer, jwtAuthenticationProvider::authenticate);

    return new JwtIssuerAuthenticationManagerResolver(authenticationManagerMap::get);
}
```

### How this works, step by step

1. **Request comes in** with `Authorization: Bearer <token>`.
2. Spring Security's `BearerTokenAuthenticationFilter` extracts the raw token and hands it to the
   `AuthenticationManagerResolver` configured on the filter chain (`.oauth2ResourceServer(server ->
   server.authenticationManagerResolver(jwtIssuerAuthenticationManagerResolver))`).
3. `JwtIssuerAuthenticationManagerResolver` does **one cheap, unverified peek** at the token: it
   base64-decodes the payload just enough to read the `iss` (issuer) claim — no signature check yet.
4. It looks up that issuer string as a key in your `authenticationManagerMap`:
   - If `iss == "http://localhost/realms/master"` → routed to the **Keycloak** `AuthenticationManager`.
   - If `iss == "APPLICATION"` → routed to **your** `AuthenticationManager`.
   - If it matches neither key → resolution fails and the request is rejected (401).
5. Only *now* does the matched `AuthenticationManager` (via `JwtAuthenticationProvider` →
   `NimbusJwtDecoder`) actually **verify the signature and standard claims** (`exp`, `nbf`, etc.) using
   the decoder that belongs to that issuer.
6. On success, the `JwtAuthenticationConverter` turns the verified JWT into an `Authentication` object:
   - **Principal name** comes from the claim you named via `setPrincipalClaimName("userId")` — so
     `authentication.getName()` returns the value of the `userId` claim instead of the default `sub`.
   - **Granted authorities** (roles/permissions) come from your custom `RolePermissionConverter`
     (a `Converter<Jwt, Collection<GrantedAuthority>>`), which presumably reads roles/permissions out of
     the token or cross-references `RolePermissionRepository`.

**Important detail:** the Keycloak provider (`jwtAuthenticationProvider1`) does **not** get the custom
`jwtAuthenticationConverter` — only the application one does. That means tokens from Keycloak will use
Spring's default conversion (`sub` claim as principal, `SCOPE_xxx` authorities from the `scope` claim),
while your app tokens get custom `userId`-as-principal and roles from `RolePermissionConverter`. This is
worth double-checking: if you *want* Keycloak tokens to also carry custom authorities, you'd wire the
same (or an equivalent) converter onto `jwtAuthenticationProvider1` too.

### Why the map key must match the `iss` claim *exactly*

The resolver does a **literal string match** against the `iss` claim inside the token. So:
- Your app's JWTs must be minted with `iss = "APPLICATION"` exactly (check your `TokenGeneration` /
  token-issuing code — it must set this claim to that literal string).
- Keycloak issues tokens with `iss` equal to the realm's issuer URL, which by default is
  `{keycloak-base-url}/realms/{realm-name}` — so `http://localhost/realms/master` must match what your
  running Keycloak instance actually puts in its tokens (host, port, and scheme included). If Keycloak is
  reachable at `http://localhost:8080` but issues tokens with `iss=http://localhost:8080/realms/master`,
  and your map key says `http://localhost/realms/master` (no port), **resolution will silently fail** —
  this is one of the most common gotchas with this pattern.

---

## 3. Application properties you need

Because you built the decoders **programmatically** (not via `spring.security.oauth2.resourceserver.jwt.*`
properties), Spring Boot won't auto-configure anything here — you don't strictly need resource-server
properties in `application.yml` for this bean to work. But you do have configuration values that are
currently **hardcoded** and should really be externalized:

```yaml
# application.yml (recommended — move hardcoded values here)
security:
  jwt:
    secret-key: ${JWT_SECRET_KEY}        # matches SecretKeyValue.SECRET_KEY
    application-issuer: APPLICATION

keycloak:
  issuer-uri: http://localhost:8080/realms/master
  jwk-set-uri: http://localhost:8080/realms/master/protocol/openid-connect/certs
  client-id: PatientService
  client-secret: ${KEYCLOAK_CLIENT_SECRET}   # never hardcode this in source, as it currently is
  token-uri: http://localhost:8080/realms/master/protocol/openid-connect/token
```

Then inject with `@Value` or a `@ConfigurationProperties` class instead of the literals currently in
`keyCloak()` and `jwtIssuerAuthenticationManagerResolver()`. This matters doubly here because your
`clientSecret(...)` is a live-looking secret sitting in source code — treat that as compromised and
rotate it in Keycloak regardless of what you do next.

If you ever *do* want Spring Boot to auto-configure a **single**-issuer resource server elsewhere (e.g. a
different service that only trusts Keycloak), that's when you'd use the standard property:

```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: http://localhost:8080/realms/master
```

Spring Boot then auto-creates the `JwtDecoder` for you by calling the issuer's
`/.well-known/openid-configuration` and following the `jwks_uri` in it. You're not using this path here
because you need two issuers, which is exactly what forces the manual `JwtIssuerAuthenticationManagerResolver`
approach.

---

## 4. The rest of the filter chain, piece by piece

```java
httpSecurity
    .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
    .authorizeHttpRequests(auth -> auth
        .requestMatchers("/error", "/create/patient", "/login").permitAll()
        .anyRequest().authenticated())
    .formLogin(form -> form.disable())
    .csrf(csrf -> csrf
        .csrfTokenRepository(cookieCsrfTokenRepository.withHttpOnlyFalse())
        .ignoringRequestMatchers("/contact")
        .csrfTokenRequestHandler(csrfTokenRequestAttributeHandler))
    .addFilterAfter(new CsrfTokenFilterGen(), UsernamePasswordAuthenticationFilterImpl.class)
    .addFilterAfter(usernamePasswordAuthenticationFilter, LogoutFilter.class)
    .oauth2ResourceServer(server ->
        server.authenticationManagerResolver(jwtIssuerAuthenticationManagerResolver))
    .exceptionHandling(exception -> exception
        .authenticationEntryPoint(new CustomAuthenticationEntryPoint())
        .accessDeniedHandler(new CustomAccessDeniedHandler()))
    .cors(cors -> cors.configurationSource(...))
    .build();
```

| Piece | What it does |
|---|---|
| `sessionCreationPolicy(STATELESS)` | No `HttpSession` is created or used — every request must carry its own proof of identity (the JWT). This is standard/required for token-based auth. |
| `authorizeHttpRequests` | `/error`, `/create/patient`, `/login` are public; everything else needs a valid, authenticated principal. |
| `formLogin(disable)` | Turns off Spring's default HTML login page/flow — you're doing auth via your own filter + JSON, not a form POST. |
| `csrf(...)` | CSRF protection using a **cookie-based** token repository. `withHttpOnlyFalse()` lets JS read the cookie (needed for SPA-style "read cookie, send header" CSRF patterns). `/contact` is exempted. Note: CSRF is only meaningful for **cookie-based** session auth; if all your auth is via `Authorization: Bearer` header, CSRF risk is inherently much lower — this is worth revisiting depending on how your frontend stores the JWT. |
| `addFilterAfter(CsrfTokenFilterGen, ...)` | Custom filter, presumably responsible for pushing the CSRF token into the response (e.g. as a cookie/header) after your auth filter runs. |
| `addFilterAfter(usernamePasswordAuthenticationFilter, LogoutFilter.class)` | Your **login** filter — this is what handles `POST /login`, authenticates via `AuthenticationManager`, and (presumably inside `TokenGeneration`) mints the `APPLICATION`-issuer JWT on success. |
| `oauth2ResourceServer(...)` | Wires in the multi-issuer resolver discussed above — this is what protects `anyRequest().authenticated()` routes. |
| `exceptionHandling(...)` | Custom 401 (`AuthenticationEntryPoint`) and 403 (`AccessDeniedHandler`) responses instead of Spring's default whitelabel behavior. |
| `cors(...)` | Wide-open CORS (`*` origins/headers/exposed-headers) — fine for local dev, **should be locked down to explicit origins before production**, especially since you allow credentials-adjacent cookies (CSRF cookie) alongside `*` origins, which browsers will actually reject in credentialed requests anyway. |

---

## 5. Other beans

- **`csrfTokenRepository()`** — a `CookieCsrfTokenRepository` bean, storing the CSRF token in a cookie
  (`XSRF-TOKEN` by default) rather than in the (stateless, non-existent) session.
- **`authenticationManager(...)`** — pulled straight from Spring's `AuthenticationConfiguration`; used by
  your login filter to actually run the username/password check against `UserDetailsService`.
- **`passwordEncoder()`** — `PasswordEncoderFactories.createDelegatingPasswordEncoder()` gives you a
  encoder that auto-detects the algorithm from a `{bcrypt}`/`{noop}`/etc. prefix on stored hashes and
  defaults new hashes to bcrypt. Good default choice.
- **`userDetailsService(...)`** — your custom `UserDetailServiceImpl`, backed by
  `PatientCredentialsRepository` (for credentials) and `RolePermissionRepository` (for authorities).
- **`oAuth2AuthorizedClientManager(...)`** + **`clientRegistrationRepository()`** + **`keyCloak()`** —
  this is the **client-credentials** side: your service acting as an OAuth2 *client* to fetch its own
  service-to-service access token from Keycloak (separate concern from validating *incoming* tokens).
- **`requestInterceptor(...)`** — a Feign `RequestInterceptor` that, on every outgoing Feign call, fetches
  a client-credentials token via `oAuth2AuthorizedClientManager` and stamps `Authorization: Bearer ...`
  onto the outgoing request. This is how your service authenticates itself when calling *other* services.

So in short: your service is simultaneously a **Resource Server** (validating two kinds of incoming JWTs)
and an **OAuth2 Client** (obtaining its own outbound token via client-credentials to call other services)
— that's why you see both `oauth2ResourceServer` and `OAuth2AuthorizedClientManager` in the same class.

---

## 6. Suggested cleanup checklist

1. Move `clientSecret`, `SECRET_KEY`, issuer URLs, and JWKS URLs out of source code and into
   `application.yml` / environment variables / a secrets manager. Rotate the Keycloak client secret,
   since it's currently committed in plaintext.
2. Double check the Keycloak `iss` value matches exactly what your running realm emits (host + port +
   scheme) — mismatches here are the #1 cause of "valid token but resolver returns 401."
3. Decide whether Keycloak-issued tokens should also go through `RolePermissionConverter` for consistent
   authority handling across both issuers.
4. Reassess the CORS policy (`*` everywhere) before any non-local deployment.
5. Consider extracting the two issuer/decoder blocks into small private helper methods
   (`buildKeycloakManager(...)`, `buildApplicationManager(...)`) for readability as you add more issuers
   later — the map-based resolver scales to N issuers with the same pattern.

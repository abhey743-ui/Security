# OAuth2 Configuration: Authorization Code vs Client Credentials

Focused, minimal configuration for the two grant types your app needs — **Authorization Code** (user logs in through Keycloak) and **Client Credentials** (service-to-service, e.g. your Feign client). No extra boilerplate, no unrelated filters.

---

## 1. Authorization Code Grant — Configuration

Used when a **human user** must log in via Keycloak (browser redirect, consent, then redirected back with a `code`).

### Java Config

```java
@Bean
public ClientRegistration keycloakAuthCode() {
    return ClientRegistration
            .withRegistrationId("keycloak-authcode")           // must match {registrationId} in redirect-uri
            .clientId("PatientService")
            .clientSecret("your-client-secret")
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE) // user-driven login flow
            .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")       // Spring's default callback path
            .scope("openid", "profile", "email")                // 'openid' makes this OIDC (see section 4)
            .authorizationUri("http://localhost:8080/realms/master/protocol/openid-connect/auth") // where user is redirected to log in
            .tokenUri("http://localhost:8080/realms/master/protocol/openid-connect/token")         // where the 'code' is exchanged for tokens
            .build();
}
```

```java
// In your SecurityFilterChain — enables the redirect-to-login + callback handling.
// This is what actually triggers the Authorization Code flow at runtime.
http.oauth2Login(Customizer.withDefaults());
```

### YAML Alternative

```yaml
spring:
  security:
    oauth2:
      client:
        registration:
          keycloak-authcode:
            client-id: PatientService
            client-secret: your-client-secret
            authorization-grant-type: authorization_code   # user must log in via browser
            redirect-uri: "{baseUrl}/login/oauth2/code/{registrationId}"
            scope: openid, profile, email                  # 'openid' = OIDC login (Section 4)
        provider:
          keycloak-authcode:
            authorization-uri: http://localhost:8080/realms/master/protocol/openid-connect/auth
            token-uri: http://localhost:8080/realms/master/protocol/openid-connect/token
```

> With YAML, you don't need the `ClientRegistration` `@Bean` — Spring Boot auto-builds the `ClientRegistrationRepository` from these properties. Use **either** the Java bean **or** YAML, not both for the same registration.

---

## 2. Client Credentials Grant — Configuration

Used for **machine-to-machine** calls (no user) — e.g. your Feign interceptor calling another internal service.

### Java Config

```java
@Bean
public ClientRegistration keycloakClientCredentials() {
    return ClientRegistration
            .withRegistrationId("keycloak-clientcreds")
            .clientId("PatientService")
            .clientSecret("your-client-secret")
            .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS) // no user, app authenticates as itself
            .tokenUri("http://localhost:8080/realms/master/protocol/openid-connect/token") // only a token endpoint needed — no auth/redirect step
            .build();
}
```

### YAML Alternative

```yaml
spring:
  security:
    oauth2:
      client:
        registration:
          keycloak-clientcreds:
            client-id: PatientService
            client-secret: your-client-secret
            authorization-grant-type: client_credentials   # no user involved, app-to-app
        provider:
          keycloak-clientcreds:
            token-uri: http://localhost:8080/realms/master/protocol/openid-connect/token
```

> Notice: Client Credentials needs **no** `authorization-uri` and **no** `redirect-uri` — there's no browser redirect step, just a direct POST to the token endpoint.

---

## 3. The Required Beans (Shared by Both Grant Types)

These four beans are **the same infrastructure for both flows** — you register both `ClientRegistration`s into one repository, and one `OAuth2AuthorizedClientProvider` chain can support both grant types at once.

```java
// 1. Holds ALL your registered clients (both grant types live in the same repository)
@Bean
public ClientRegistrationRepository clientRegistrationRepository(
        ClientRegistration keycloakAuthCode,
        ClientRegistration keycloakClientCredentials) {
    return new InMemoryClientRegistrationRepository(keycloakAuthCode, keycloakClientCredentials);
}

// 2. Stores the token(s) issued for an authorized client, keyed by principal + registrationId.
//    Default (HttpSession-based) only works for STATEFUL apps with a user session.
//    Your SecurityChain uses SessionCreationPolicy.STATELESS -> override this bean
//    (see "Gotchas" below) or Authorization Code tokens won't persist between requests.
@Bean
public OAuth2AuthorizedClientRepository authorizedClientRepository(
        OAuth2AuthorizedClientService authorizedClientService) {
    return new AuthenticatedPrincipalOAuth2AuthorizedClientRepository(authorizedClientService);
}

@Bean
public OAuth2AuthorizedClientService authorizedClientService(
        ClientRegistrationRepository clientRegistrationRepository) {
    return new InMemoryOAuth2AuthorizedClientService(clientRegistrationRepository); // swap for a JPA-backed service in production
}

// 3. The engine that actually fetches/refreshes tokens for whichever grant type is requested.
//    ONE manager, built with BOTH provider strategies -> handles Authorization Code
//    refresh AND Client Credentials token issuance.
@Bean
public OAuth2AuthorizedClientManager oAuth2AuthorizedClientManager(
        ClientRegistrationRepository clientRegistrationRepository,
        OAuth2AuthorizedClientRepository authorizedClientRepository) {

    OAuth2AuthorizedClientProvider provider = OAuth2AuthorizedClientProviderBuilder.builder()
            .authorizationCode()   // handles the user-driven flow's token exchange
            .refreshToken()        // lets Authorization Code tokens auto-refresh
            .clientCredentials()   // handles the app-to-app flow's token issuance
            .build();

    DefaultOAuth2AuthorizedClientManager manager =
            new DefaultOAuth2AuthorizedClientManager(clientRegistrationRepository, authorizedClientRepository);
    manager.setAuthorizedClientProvider(provider);
    return manager;
}
```

### How to Use the Manager for Each Grant Type

```java
// Client Credentials — principal is your OWN app, not a user (used by your Feign interceptor)
OAuth2AuthorizeRequest clientCredsRequest = OAuth2AuthorizeRequest
        .withClientRegistrationId("keycloak-clientcreds")
        .principal("APPLICATION")            // arbitrary name, no real user backing it
        .build();

// Authorization Code — principal MUST be the actual authenticated user's Authentication object
OAuth2AuthorizeRequest authCodeRequest = OAuth2AuthorizeRequest
        .withClientRegistrationId("keycloak-authcode")
        .principal(SecurityContextHolder.getContext().getAuthentication()) // real logged-in user
        .build();
```

### What's Different Between the Two Grant Types (and What's Not)

| | Authorization Code | Client Credentials |
|---|---|---|
| `ClientRegistration` fields needed | `authorizationUri`, `tokenUri`, `redirectUri`, `scope` | `tokenUri` only |
| Provider added to builder | `.authorizationCode()` + `.refreshToken()` | `.clientCredentials()` |
| `principal(...)` passed | The logged-in user's `Authentication` | Any static string (no real user) |
| Triggers browser redirect? | Yes (`.oauth2Login()`) | No — pure backend HTTP call |
| `ClientRegistrationRepository` bean | **Same bean**, holds both registrations | **Same bean** |
| `OAuth2AuthorizedClientManager` bean | **Same bean**, same provider chain | **Same bean** |
| `OAuth2AuthorizedClientRepository` bean | **Same bean** — but must not be session-only in a stateless app | **Same bean** (Client Credentials doesn't strictly need session storage, but reuses the same bean for consistency/token caching) |

**Bottom line, confirming your instinct:** you only need **one** `ClientRegistrationRepository`, **one** `OAuth2AuthorizedClientManager`, and **one** `OAuth2AuthorizedClientRepository` bean for the whole app — the grant-type difference lives in the `ClientRegistration` objects and in the `OAuth2AuthorizedClientProvider` chain you attach to the manager, not in separate infrastructure.

### Overriding These Beans

- **`ClientRegistrationRepository`** — override to load registrations from a database instead of hardcoding, by implementing the interface yourself instead of using `InMemoryClientRegistrationRepository`.
- **`OAuth2AuthorizedClientRepository`** — override this if you're stateless (your case). Common replacement: a custom implementation backed by Redis, or one that re-derives the token per-request instead of persisting it (since Client Credentials tokens can just be re-fetched/cached in-memory per app instance rather than per user session).
- **`OAuth2AuthorizedClientProvider`** — override the builder chain to add `.password()` or remove grant types you don't use; order in the builder does not matter, each provider only activates for its matching grant type.
- **`OAuth2AuthorizedClientManager`** — override entirely if you need custom token-refresh logic (e.g., custom retry/backoff when Keycloak is down).

---

## 4. If You Switch to OIDC — What Changes

Your Client Credentials setup (Section 2) **stays exactly the same** — OIDC only applies to flows where a real user identity is established, so it never touches Client Credentials.

For **Authorization Code**, here's what changes:

1. **Add `openid` to the `scope`** on the `keycloak-authcode` registration (already shown above). This one flag is the entire trigger — Spring Security detects the `openid` scope and automatically switches from plain OAuth2 handling to OIDC handling.

2. **Token response gains a new token.** Previously you only got `access_token` (+ `refresh_token`). Now Keycloak also returns an **`id_token`** — a signed JWT that represents the user's identity (contains `sub`, `email`, `name`, etc.), separate from the `access_token` used to call APIs.

3. **The authenticated principal type changes.** Without `openid`, `oauth2Login()` gives you a generic `OAuth2User` (raw attribute map, provider-specific). With `openid`, you instead get an **`OidcUser`** (extends `OAuth2User`), which additionally exposes:
   ```java
   @GetMapping("/me")
   public String me(@AuthenticationPrincipal OidcUser user) {
       return user.getEmail() + " / " + user.getSubject() + " / " + user.getFullName();
   }
   ```

4. **You can switch `authorization-uri`/`token-uri` for a single `issuer-uri`.** Since Keycloak realms expose an OIDC discovery document, you can replace the two manually-set URIs with:
   ```yaml
   provider:
     keycloak-authcode:
       issuer-uri: http://localhost:8080/realms/master
   ```
   Spring auto-fetches `authorization_endpoint`, `token_endpoint`, and `jwks_uri` from `.../realms/master/.well-known/openid-configuration` — one less place to keep in sync if Keycloak URLs change.

5. **Your resource-server side (`JwtIssuerAuthenticationManagerResolver`) is unaffected.** It validates `access_token`s on incoming API requests — that part of your existing code doesn't change, because it was already using `NimbusJwtDecoder` against Keycloak's `jwks_uri`, which is identical whether the token came from a plain OAuth2 or an OIDC-flavored login.

6. **Optional: customize identity mapping.** If you want to map Keycloak's `id_token` claims (e.g. realm roles) into Spring authorities the same way you did for the resource server with `rolePermissionConverter`, add:
   ```java
   http.oauth2Login(oauth2 -> oauth2
       .userInfoEndpoint(userInfo -> userInfo.oidcUserService(customOidcUserService()))
   );
   ```

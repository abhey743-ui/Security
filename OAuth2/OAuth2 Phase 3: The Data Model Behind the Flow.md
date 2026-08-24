# OAuth2 Phase 3: The Data Model Behind the Flow

Phases 1 and 2 covered the **filters** — the code that actually runs on each request. This phase covers the **classes those filters build, pass around, and save**. Once you know these shapes, the filter code from the last two docs reads a lot more clearly, since you can now picture exactly what's inside `authorizationRequest`, `clientRegistration`, and `authorizedClient`.

I'll go bottom-up: the smallest building blocks first, then how they get bundled together, then how that bundle gets saved.

---

## Step 1 — `ClientRegistration`: your static config, as an object

This is the class version of everything you write in `application.yml` or build with `ClientRegistration.withRegistrationId(...)`. **It stores the Authorization Server's details** — the URLs needed to fetch a token, plus your app's credentials (client ID/secret). It's built once at startup and never changes at runtime.

```java
private String registrationId;                    // e.g. "keycloak-authcode"
private String clientId;                           // e.g. "PatientService"
private String clientSecret;
private ClientAuthenticationMethod clientAuthenticationMethod; // how the client authenticates itself to the token endpoint
private AuthorizationGrantType authorizationGrantType;         // authorization_code / client_credentials
private String redirectUri;
private Set<String> scopes = Collections.emptySet();
private ProviderDetails providerDetails = new ProviderDetails(); // see below
private String clientName;
```

### The nested `ProviderDetails` class

`ClientRegistration` doesn't hold the Authorization Server's URLs directly — it delegates that to an **internal nested class**, `ProviderDetails`:

```java
private String authorizationUri;   // Keycloak's login page
private String tokenUri;           // where codes get exchanged for tokens
private String jwkSetUri;          // where to fetch Keycloak's public keys, to verify JWTs
private String issuerUri;          // the OIDC discovery base URL (Section 4 of the earlier doc)
private Map<String, Object> configurationMetadata; // raw discovery-document data, if issuer-uri was used
```

> 💬 **Why split it into a nested class instead of flattening everything?** Because "who am I" (`clientId`, `clientSecret`, `redirectUri`) and "where do I talk to the Authorization Server" (`authorizationUri`, `tokenUri`, `jwkSetUri`) are conceptually different concerns — `ProviderDetails` can be swapped or reused independently of the client's own identity.

### The internal `Builder` class

`ClientRegistration` also has a nested **`Builder`** — this is what powers the fluent API you've already been using:

```java
ClientRegistration.withRegistrationId("keycloak-authcode")
    .clientId("PatientService")
    .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
    .tokenUri("...")
    .build();
```

`ClientRegistration` itself has no public constructor — you're only ever allowed to build one through `Builder`, which keeps the object immutable once built (all its fields are `private final` at runtime, even though the builder mutates a draft version first).

---

## Step 2 — The Token classes: what Keycloak actually gives you back

Once Step 7 of Phase 2 (`accessTokenResponseClient.getTokenResponse(...)`) runs, Keycloak hands back token values. Spring wraps these in a small class hierarchy:

```java
public abstract class AbstractOAuth2Token {
    private final String tokenValue;   // the actual token string
    private final Instant issuedAt;    // when it was issued
    private final Instant expiresAt;   // when it stops being valid
}
```

This is the **shared parent** — both token types below extend it, since "a string value with an issued time and an expiry time" is common to both.

```java
public class OAuth2RefreshToken extends AbstractOAuth2Token {
    // no extra fields — a refresh token is JUST a value + timestamps
}
```

```java
public class OAuth2AccessToken extends AbstractOAuth2Token {
    private final TokenType tokenType;   // almost always "Bearer"
    private final Set<String> scopes;    // what this specific token is allowed to do
}
```

> 💬 **Why does `OAuth2AccessToken` need more fields than `OAuth2RefreshToken`?** A refresh token has exactly one job — get exchanged for a new access token — so it doesn't need a type or scopes of its own. An access token, on the other hand, gets sent on every API call, so downstream services need to know its `tokenType` (to build the `Authorization: Bearer <value>` header correctly) and its `scopes` (to know what it's actually allowed to access).

---

## Step 3 — `OAuth2AuthorizedClient`: bundling it all together

This is the class that holds **everything about one authenticated user's relationship with one client registration** — literally the object built in Phase 2, Step 9, right before `saveAuthorizedClient(...)` was called.

```java
private static final long serialVersionUID = 620L;
private final ClientRegistration clientRegistration;  // which client this belongs to (Step 1)
private final String principalName;                   // which user this belongs to
private final OAuth2AccessToken accessToken;           // the access token (Step 2)
private final OAuth2RefreshToken refreshToken;         // the refresh token (Step 2), if one was issued
```

Think of `OAuth2AuthorizedClient` as the answer to: *"For this user, using this client registration, what tokens do we currently have?"* It's the exact object your Feign interceptor's `OAuth2AuthorizedClientManager.authorize(...)` call (from the earlier config doc) hands back to you when it's time to attach a token to an outgoing request.

---

## Step 4 — `OAuth2AuthorizedClientService`: how it gets stored

This is the **interface** responsible for loading, saving, and removing `OAuth2AuthorizedClient` objects long-term (not tied to a single HTTP request/response — just keyed by registration ID + principal name).

```java
public interface OAuth2AuthorizedClientService {

    // Load a previously saved client for a given registration + user
    <T extends OAuth2AuthorizedClient> @Nullable T loadAuthorizedClient(
        String clientRegistrationId, String principalName);

    // Save (or update) a client after successful authentication
    void saveAuthorizedClient(
        OAuth2AuthorizedClient authorizedClient, Authentication principal);

    // Remove a client — e.g. on logout, or token revocation
    void removeAuthorizedClient(
        String clientRegistrationId, String principalName);
}
```

### Implementation 1 — `InMemoryOAuth2AuthorizedClientService` (the default)

```java
private final Map<OAuthClientIdAndUserId, OAuth2AuthorizedClient> authorizedClients;
```

This is what runs automatically if you don't configure anything else. Every authorized client is kept in a plain in-memory `Map`, keyed by a combination of client registration ID and user ID. Simple, fast — but **gone on restart, and doesn't scale across multiple app instances.**

### Implementation 2 — `JdbcOAuth2AuthorizedClientService` (database-backed)

Saves/loads/deletes `OAuth2AuthorizedClient` rows in an actual database table, so tokens survive restarts and are shared across instances. The trade-off: **Spring requires a specific schema** for this table — you have to create it yourself (Spring ships a reference SQL script, but you own the table structure).

---

## Step 5 — Clearing Up `Service` vs. `Repository`

Your own `SecurityChain` config from earlier already wires both of these together — this is the piece that ties this whole doc back to your actual code:

```java
@Bean
public OAuth2AuthorizedClientService authorizedClientService(
        ClientRegistrationRepository clientRegistrationRepository) {
    return new InMemoryOAuth2AuthorizedClientService(clientRegistrationRepository);
}

@Bean
public OAuth2AuthorizedClientRepository authorizedClientRepository(
        OAuth2AuthorizedClientService authorizedClientService) {
    return new AuthenticatedPrincipalOAuth2AuthorizedClientRepository(authorizedClientService);
}
```

| | `OAuth2AuthorizedClientService` | `OAuth2AuthorizedClientRepository` |
|---|---|---|
| Keyed by | `clientRegistrationId` + `principalName` (plain strings) | `HttpServletRequest` / `HttpServletResponse` |
| Used by | The actual storage layer (in-memory, JDBC, etc.) | The **filters**, directly (this is what `OAuth2LoginAuthenticationFilter` calls in Phase 2, Step 9) |
| Role | *Where* the data physically lives | *How* a filter reads/writes that data during a request |

**In plain terms:** the `Repository` is a thin adapter the filters talk to; it doesn't store anything itself — it just delegates to whatever `Service` you gave it. `AuthenticatedPrincipalOAuth2AuthorizedClientRepository` is the bridge: it takes the current request's authenticated principal, extracts the username, and forwards the actual load/save/remove work to the `Service`.

> 🔑 This is exactly why, for your **stateless** app, swapping the default session-based `OAuth2AuthorizedClientRepository` isn't enough by itself if the underlying `Service` (e.g. `InMemoryOAuth2AuthorizedClientService`) also can't survive across instances — you'd want a `JdbcOAuth2AuthorizedClientService` (or a custom one) underneath a custom `Repository`, not just a custom `Repository` alone.

---

## Full Picture

```
ClientRegistration (static config: who am I, where's the Auth Server)
        │
        ▼
AbstractOAuth2Token ──┬── OAuth2AccessToken   (tokenType, scopes)
                       └── OAuth2RefreshToken  (just value + timestamps)
        │
        ▼
OAuth2AuthorizedClient
   = ClientRegistration + principalName + accessToken + refreshToken
        │
        ▼
OAuth2AuthorizedClientRepository  ← called directly by filters (per-request)
        │  delegates to
        ▼
OAuth2AuthorizedClientService     ← actually stores/loads the data
        │
   ┌────┴─────────────────────────┐
   ▼                                ▼
InMemoryOAuth2AuthorizedClientService   JdbcOAuth2AuthorizedClientService
(default, volatile)                     (persistent, needs a defined schema)
```

Send the next diagram whenever you're ready.

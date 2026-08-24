# Authentication & Authorization Explained: Spring Security, OAuth2 & OIDC

A complete reference covering three related but distinct topics:

1. **Traditional Spring Security username/password authentication**
2. **OAuth2** — the authorization framework
3. **OpenID Connect (OIDC)** — the identity layer built on top of OAuth2

---

## Table of Contents

- [1. Spring Security: Username/Password Authentication](#1-spring-security-usernamepassword-authentication)
- [2. OAuth2 Explained](#2-oauth2-explained)
- [3. OpenID Connect (OIDC) Explained](#3-openid-connect-oidc-explained)
- [4. How They Compare](#4-how-they-compare)
- [5. Which One Should You Use?](#5-which-one-should-you-use)
- [6. Security Best Practices](#6-security-best-practices)
- [7. Glossary](#7-glossary)

---

## 1. Spring Security: Username/Password Authentication

This is the "classic" form of authentication: a user submits a username and password directly to your application, and your application verifies them itself (no third party involved).

### 1.1 Core Concept

Your application owns the credentials (or at least the hashed version of them) and is fully responsible for verifying identity. This contrasts with OAuth2/OIDC, where a separate Authorization Server handles verification.

### 1.2 The Request Flow

```
┌─────────┐        1. POST /login (username, password)        ┌──────────────────────┐
│ Browser │ ───────────────────────────────────────────────►  │  Spring Security      │
│         │                                                     │  Filter Chain         │
└─────────┘                                                     └──────────┬────────────┘
                                                                             │
                                                     2. Wraps creds into    │
                                                     UsernamePasswordAuthenticationToken
                                                                             ▼
                                                                  ┌──────────────────────┐
                                                                  │  AuthenticationManager│
                                                                  └──────────┬────────────┘
                                                                             │ 3. delegates to
                                                                             ▼
                                                                  ┌──────────────────────┐
                                                                  │ DaoAuthenticationProvider│
                                                                  └──────────┬────────────┘
                                                     4. loadUserByUsername()  │
                                                                             ▼
                                                                  ┌──────────────────────┐
                                                                  │   UserDetailsService  │
                                                                  └──────────┬────────────┘
                                                     5. returns UserDetails  │
                                                                             ▼
                                                                  ┌──────────────────────┐
                                                                  │    PasswordEncoder    │
                                                                  │  (compares hash)      │
                                                                  └──────────┬────────────┘
                                                     6. match? → Authentication object
                                                                             ▼
                                                                  ┌──────────────────────┐
                                                                  │  SecurityContext      │
                                                                  │  (session created)    │
                                                                  └──────────────────────┘
```

### 1.3 Key Components

| Component | Responsibility |
|---|---|
| `UsernamePasswordAuthenticationFilter` | Intercepts the login POST request, extracts credentials, builds an unauthenticated `Authentication` token |
| `AuthenticationManager` | Coordinates the authentication attempt; delegates to one or more `AuthenticationProvider`s |
| `AuthenticationProvider` (usually `DaoAuthenticationProvider`) | Contains the actual authentication logic |
| `UserDetailsService` | Loads user data (username, hashed password, roles/authorities) from your data store (DB, LDAP, etc.) |
| `UserDetails` | Represents the loaded user — username, password hash, authorities, account flags (enabled, locked, expired) |
| `PasswordEncoder` | Hashes passwords on registration and verifies them on login (never store plaintext) |
| `SecurityContext` / `SecurityContextHolder` | Holds the authenticated `Authentication` object for the duration of the request/session |
| `SecurityFilterChain` | The ordered list of servlet filters Spring Security runs on every request |

### 1.4 Minimal Configuration Example (Spring Boot 3 / Spring Security 6)

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public UserDetailsService userDetailsService(PasswordEncoder encoder) {
        UserDetails user = User.builder()
            .username("john")
            .password(encoder.encode("password123"))
            .roles("USER")
            .build();
        return new InMemoryUserDetailsManager(user);
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/public/**").permitAll()
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/login")
                .defaultSuccessUrl("/home", true)
                .permitAll()
            )
            .logout(logout -> logout.permitAll());

        return http.build();
    }
}
```

### 1.5 Custom `UserDetailsService` Backed by a Database

```java
@Service
public class DbUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public DbUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        AppUser user = userRepository.findByUsername(username)
            .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));

        return User.builder()
            .username(user.getUsername())
            .password(user.getPasswordHash())
            .authorities(user.getRoles().stream()
                .map(SimpleGrantedAuthority::new)
                .toList())
            .disabled(!user.isEnabled())
            .build();
    }
}
```

### 1.6 Where It Falls Short

- Your app must securely store and manage credentials (or delegate to something like LDAP).
- No built-in support for "Login with Google/GitHub".
- No standard token format shared across services — you'd need to build your own session/token strategy for microservices.
- This is why OAuth2 / OIDC exist: to delegate authentication to a trusted third party and standardize tokens.

---

## 2. OAuth2 Explained

### 2.1 What OAuth2 Actually Is

**OAuth2 is an authorization framework, not an authentication protocol.** It defines how a third-party application can get *limited access* to a user's resources on another service, **without ever seeing the user's password**.

Classic example: a photo-printing app wants to access your Google Photos without you giving it your Google password.

### 2.2 The Four Roles

| Role | Description | Example |
|---|---|---|
| **Resource Owner** | The user who owns the data | You |
| **Client** | The application requesting access | The photo-printing app |
| **Authorization Server** | Issues tokens after authenticating the user and getting consent | Google's OAuth server |
| **Resource Server** | Hosts the protected data, validates the access token | Google Photos API |

### 2.3 Tokens

- **Access Token** — a credential used to call the Resource Server's APIs. Usually short-lived (minutes to hours). Often a JWT, but the spec doesn't require any specific format — it's opaque to the client.
- **Refresh Token** — a long-lived credential used to obtain a new access token without the user logging in again.

> Important: OAuth2 access tokens say nothing about *who the user is* — only that the bearer is allowed to do certain things. That identity gap is exactly what OIDC solves (see Section 3).

### 2.4 Grant Types (Flows)

| Grant Type | Use Case | Still Recommended? |
|---|---|---|
| **Authorization Code** (+ PKCE) | Web apps, mobile apps, SPAs — the user is present in a browser | ✅ Yes — the standard for anything with a user |
| **Client Credentials** | Machine-to-machine, no user involved (service-to-service) | ✅ Yes |
| **Refresh Token** | Getting a new access token silently | ✅ Yes |
| **Device Code** | Input-constrained devices (smart TVs, CLI tools) | ✅ Yes |
| **Implicit** | Old SPA flow, token returned directly in the URL fragment | ❌ Deprecated — insecure, replaced by Auth Code + PKCE |
| **Resource Owner Password Credentials (ROPC)** | Client collects username/password directly | ❌ Deprecated — defeats the purpose of OAuth2 |

### 2.5 Authorization Code Flow (with PKCE) — Step by Step

```
User (Browser)          Client App               Authorization Server        Resource Server
     │                       │                            │                        │
     │  1. Click "Login"     │                            │                        │
     │──────────────────────►│                            │                        │
     │                       │ 2. Redirect to /authorize   │                        │
     │                       │    (+ code_challenge)       │                        │
     │◄──────────────────────│                            │                        │
     │ 3. Redirect to Auth Server                          │                        │
     │─────────────────────────────────────────────────────►│                       │
     │ 4. Login + consent screen                            │                        │
     │◄─────────────────────────────────────────────────────│                       │
     │ 5. Approve                                           │                        │
     │─────────────────────────────────────────────────────►│                       │
     │ 6. Redirect back with ?code=XYZ                      │                        │
     │◄─────────────────────────────────────────────────────│                       │
     │ 7. Browser delivers code to Client                   │                        │
     │──────────────────────►│                            │                        │
     │                       │ 8. POST /token              │                        │
     │                       │   (code + code_verifier)    │                        │
     │                       │─────────────────────────────►│                        │
     │                       │ 9. access_token (+ refresh)  │                        │
     │                       │◄─────────────────────────────│                        │
     │                       │ 10. Call API with access_token                        │
     │                       │───────────────────────────────────────────────────────►│
     │                       │ 11. Protected data                                     │
     │                       │◄───────────────────────────────────────────────────────│
```

**Why PKCE (Proof Key for Code Exchange)?** It prevents an attacker who intercepts the authorization `code` from exchanging it for a token, since only the original client knows the `code_verifier` that matches the `code_challenge` sent in step 2.

### 2.6 Spring Security as an OAuth2 Client

```java
@Bean
public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http
        .authorizeHttpRequests(auth -> auth
            .anyRequest().authenticated()
        )
        .oauth2Login(Customizer.withDefaults()); // enables "Login with X" flow
    return http.build();
}
```

```yaml
# application.yml
spring:
  security:
    oauth2:
      client:
        registration:
          github:
            client-id: your-client-id
            client-secret: your-client-secret
            scope: read:user
        provider:
          github:
            authorization-uri: https://github.com/login/oauth/authorize
            token-uri: https://github.com/login/oauth/access_token
            user-info-uri: https://api.github.com/user
```

### 2.7 Spring Security as an OAuth2 Resource Server (validating tokens)

```java
@Bean
public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http
        .authorizeHttpRequests(auth -> auth
            .requestMatchers("/api/public/**").permitAll()
            .anyRequest().authenticated()
        )
        .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));
    return http.build();
}
```

```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: https://your-auth-server.com/
```

This tells Spring to validate incoming `Authorization: Bearer <token>` headers as JWTs signed by the trusted issuer — no session, no cookies, fully stateless.

---

## 3. OpenID Connect (OIDC) Explained

### 3.1 What OIDC Adds to OAuth2

**OIDC is a thin identity layer on top of OAuth2.** OAuth2 alone answers "is this client allowed to do X?" — it does not standardize *who the user is*. OIDC fixes that by adding:

1. A new token: the **ID Token** (a JWT that represents the authenticated user's identity)
2. A standard `openid` scope
3. A standard `/userinfo` endpoint
4. Standard **claims** (`sub`, `email`, `name`, `picture`, etc.)
5. A discovery document (`/.well-known/openid-configuration`) so clients can auto-configure

### 3.2 ID Token vs. Access Token

| | Access Token | ID Token |
|---|---|---|
| Purpose | Authorize API calls | Prove user identity to the client |
| Audience | Resource Server | The Client application itself |
| Format | Often opaque or JWT | Always a JWT |
| Who reads it | Resource Server | Client app |
| Contains user identity? | Not guaranteed | Yes — standardized claims |

### 3.3 Anatomy of an ID Token (JWT)

An ID token has three Base64Url-encoded parts separated by dots: `header.payload.signature`

**Example payload (decoded):**

```json
{
  "iss": "https://accounts.google.com",
  "sub": "110169484474386276334",
  "aud": "your-client-id.apps.googleusercontent.com",
  "exp": 1719431345,
  "iat": 1719427745,
  "email": "user@example.com",
  "email_verified": true,
  "name": "Jane Doe",
  "picture": "https://.../photo.jpg"
}
```

| Claim | Meaning |
|---|---|
| `iss` | Issuer — who created and signed the token |
| `sub` | Subject — unique, stable ID for the user |
| `aud` | Audience — which client this token is intended for |
| `exp` / `iat` | Expiration / issued-at timestamps |
| `email`, `name`, `picture` | Standard profile claims (only if scopes requested them) |

### 3.4 The OIDC Flow (Authorization Code Flow + `openid` scope)

Identical to the OAuth2 Authorization Code flow in Section 2.5, with two differences:

1. The client adds `scope=openid profile email` to the `/authorize` request.
2. The token response now includes **both**:
   ```json
   {
     "access_token": "eyJhbGciOi...",
     "id_token": "eyJhbGciOi...",
     "refresh_token": "eyJhbGciOi...",
     "token_type": "Bearer",
     "expires_in": 3600
   }
   ```

The client validates the `id_token` signature and claims locally to establish "this user is logged in" — it does not need to call an API to know who the user is.

### 3.5 Discovery Document

Most OIDC providers expose:

```
GET https://your-provider.com/.well-known/openid-configuration
```

This returns the authorization endpoint, token endpoint, `jwks_uri` (public keys for verifying signatures), supported scopes, claims, and grant types — enabling zero-config client setup.

### 3.6 Spring Security OIDC Login Configuration

Spring Security's `oauth2Login()` (Section 2.6) automatically becomes **OIDC login** the moment the provider supports `openid` scope and returns an `id_token` — Spring detects this and populates an `OidcUser` (which extends `OAuth2User`) instead of a plain `OAuth2User`.

```yaml
spring:
  security:
    oauth2:
      client:
        registration:
          google:
            client-id: your-client-id
            client-secret: your-client-secret
            scope: openid, profile, email   # 'openid' triggers OIDC behavior
        provider:
          google:
            issuer-uri: https://accounts.google.com # auto-discovers all endpoints
```

```java
@GetMapping("/me")
public String me(@AuthenticationPrincipal OidcUser principal) {
    return "Hello, " + principal.getFullName() + " (" + principal.getEmail() + ")";
}
```

Notice how much simpler this is than the manual `provider:` block in Section 2.6 — because OIDC's discovery document lets Spring fetch all the endpoint URLs automatically from `issuer-uri`.

---

## 4. How They Compare

| | Username/Password (Spring Security form login) | OAuth2 | OIDC |
|---|---|---|---|
| **What it solves** | Verifying credentials your app owns | Delegated, scoped *authorization* | Delegated *authentication* (identity) |
| **Who verifies the user** | Your own app | A third-party Authorization Server | A third-party Authorization Server (acting as an "Identity Provider") |
| **Password ever touches your app?** | Yes (hashed & stored) | No | No |
| **Standard token for "who is this user"?** | No (session-based) | No (access tokens are opaque to clients) | Yes — the ID Token |
| **Best for** | Simple apps, internal tools, full control needed | API access delegation, machine-to-machine | "Login with Google/GitHub/Microsoft", SSO |
| **Spring Security building block** | `UserDetailsService` + `PasswordEncoder` | `oauth2Login()` / `oauth2ResourceServer()` | `oauth2Login()` with `openid` scope → `OidcUser` |

**Key mental model:** *OAuth2 is about access. OIDC is about identity. Username/password auth is about verifying a secret your own app controls.* OIDC is literally OAuth2 + an identity layer — so in practice, "Login with Google" is OIDC, while "let this app read my Google Calendar" is plain OAuth2.

---

## 5. Which One Should You Use?

- **Building an internal admin tool with a fixed set of users?** → Username/password with Spring Security is simplest and gives you full control.
- **Need "Login with Google/GitHub/Microsoft"?** → OIDC (`oauth2Login` with `openid` scope).
- **Building an API that needs to let third-party apps act on behalf of users without seeing passwords?** → OAuth2 (your app becomes an Authorization Server, or you delegate to one like Keycloak/Auth0/Okta).
- **Building microservices that call each other, no user involved?** → OAuth2 Client Credentials grant.
- **Want Single Sign-On (SSO) across multiple of your own apps?** → OIDC with a central Identity Provider (Keycloak, Okta, Auth0, Azure AD).

Many real systems combine all three: username/password (or OIDC login) at the edge for humans, and OAuth2 Client Credentials between internal services.

---

## 6. Security Best Practices

- **Never store plaintext passwords.** Always use a strong adaptive hash (`BCryptPasswordEncoder`, `Argon2PasswordEncoder`) — never MD5/SHA1 alone.
- **Always use HTTPS** for anything involving tokens or credentials.
- **Use Authorization Code + PKCE**, never the Implicit or ROPC grants, even for SPAs.
- **Validate JWT signatures and claims** (`iss`, `aud`, `exp`) on every request — never trust an unverified token.
- **Keep access tokens short-lived**; use refresh tokens (with rotation) for longer sessions.
- **Store tokens securely** — httpOnly, secure cookies or in-memory storage; avoid `localStorage` for sensitive tokens where possible (XSS risk).
- **Scope tokens narrowly** — request only the OAuth2 scopes you actually need.
- **Rotate client secrets** and never commit them to source control — use environment variables or a secrets manager.
- **Enable account lockout / rate limiting** on username/password login endpoints to mitigate brute-force attacks.
- **Verify the `state` parameter** in OAuth2/OIDC flows to prevent CSRF attacks on the redirect.

---

## 7. Glossary

| Term | Definition |
|---|---|
| **Authentication** | Verifying *who* someone is |
| **Authorization** | Verifying *what* someone is allowed to do |
| **JWT (JSON Web Token)** | A compact, signed token format (`header.payload.signature`) used for access tokens and ID tokens |
| **Bearer Token** | A token that grants access to whoever "bears" (holds) it — no extra proof needed |
| **Scope** | A string representing a permission being requested (e.g., `read:user`, `openid`) |
| **Claim** | A piece of information asserted about a subject inside a JWT (e.g., `email`, `sub`) |
| **IdP (Identity Provider)** | A service that authenticates users and issues identity tokens (e.g., Google, Okta, Keycloak) |
| **SSO (Single Sign-On)** | Logging in once and being authenticated across multiple applications |
| **PKCE** | Proof Key for Code Exchange — protects the Authorization Code flow from interception attacks |

---

*This document is a general technical reference and can be adapted for internal team wikis, onboarding docs, or GitHub repo documentation.*

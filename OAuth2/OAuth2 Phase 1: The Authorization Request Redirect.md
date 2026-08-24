# OAuth2 Phase 1: The Authorization Request Redirect

This covers the **very first step** of the Authorization Code flow — what happens the moment a user hits your login trigger (e.g. `/oauth2/authorization/keycloak-authcode`), *before* they ever see Keycloak's login screen. This is all handled internally by `OAuth2AuthorizationRequestRedirectFilter`.

Think of this phase as: **"build the login URL, remember what we sent, then bounce the browser to it."**

---

## The Big Picture (in plain words)

1. A request comes in that matches your OAuth2 login path.
2. Spring builds an object called `OAuth2AuthorizationRequest` — basically a form with everything Keycloak needs to know (who's asking, what scopes they want, where to send the user back to).
3. Spring **saves a copy of that request** somewhere (session, cookie, etc.) so it can double-check things later when Keycloak sends the user back.
4. Spring turns that request object into a real URL (with query params like `client_id`, `scope`, `state`...).
5. Spring sends an HTTP redirect (302) so the browser jumps to Keycloak's login page.

That's it — no login has happened yet. This phase only *prepares and redirects*.

---

## Step-by-Step Walkthrough

### Step 1 — Entry point

```java
this.authorizationRedirectStrategy.sendRedirect(request, response, ...);
```

This is the outer method on the filter — the thing that kicks the whole phase off. It doesn't do the real work itself; it just orchestrates the steps below.

> 💬 **Why it exists:** Before Spring can redirect anyone anywhere, it first has to build the full `OAuth2AuthorizationRequest` object — since the redirect URL is *generated from* that object, not written by hand.

---

### Step 2 — Build the request object

```java
OAuth2AuthorizationRequest authorizationRequest =
        this.authorizationRequestResolver.resolve(request);
```

- `authorizationRequestResolver` looks at the incoming request (e.g. it sees `keycloak-authcode` in the URL), finds the matching `ClientRegistration` you configured, and uses it to build an `OAuth2AuthorizationRequest`.
- **This is the single most important line in the whole phase.** Everything else after this just uses, saves, or redirects with this object.

Think of `OAuth2AuthorizationRequest` as a **filled-in form** — one that's about to become a URL query string.

---

### Step 3 — What's actually inside `OAuth2AuthorizationRequest`

These are the real fields Spring Security keeps on this object:

| Field | Plain-English meaning |
|---|---|
| `authorizationUri` | Keycloak's login page URL (e.g. `.../realms/master/protocol/openid-connect/auth`) |
| `authorizationGrantType` | Which grant this is for — here, always `authorization_code` |
| `responseType` | Tells Keycloak "I expect a `code` back" (not a token directly) |
| `clientId` | Your app's registered client ID (`PatientService`) |
| `redirectUri` | Where Keycloak should send the browser back to after login |
| `scopes` | What access you're asking for (e.g. `openid`, `profile`, `email`) |
| `state` | A random string — Spring's built-in CSRF protection for this flow (explained below) |
| `additionalParameters` | Any extra query params you want to tack on |
| `authorizationRequestUri` | The **final, fully-built URL** — this is what the browser actually gets redirected to |
| `attributes` | Internal bookkeeping data (not sent to Keycloak, just used by Spring itself) |

> 🔑 **About `state`:** This value gets saved now and checked again later when Keycloak redirects back. If they don't match, Spring rejects the callback. This stops attackers from tricking your app into accepting a authorization code that wasn't actually requested by this browser.

---

### Step 4 — Save the request before redirecting

```java
this.authorizationRequestRepository.saveAuthorizationRequest(
        authorizationRequest, request, response);
```

This is the step people forget about, but it's critical.

Once the browser is sent off to Keycloak, **your server has no memory of what it just asked for** — HTTP is stateless. So before redirecting, Spring stashes a copy of the `authorizationRequest` (usually in the session, or a cookie for stateless apps) via the `AuthorizationRequestRepository` interface.

> 🟢 *Why an interface, not a fixed implementation?* Because "where do I remember this?" differs per app. Session-based apps use `HttpSessionOAuth2AuthorizationRequestRepository`. Stateless apps (like yours, with `SessionCreationPolicy.STATELESS`) need a custom implementation — usually cookie-based — since there's no session to store it in.

When Keycloak redirects the user back later with `?code=...&state=...`, Spring pulls this saved request back out and compares the `state` to confirm nothing was tampered with.

---

### Step 5 — Actually redirect the browser

```java
this.authorizationRedirectStrategy.sendRedirect(request, response,
        authorizationRequest.getAuthorizationRequestUri());
```

Now that the request is built *and* saved, Spring fires the real HTTP redirect.

- `authorizationRequest.getAuthorizationRequestUri()` returns the finished URL — `authorizationUri` plus all the query parameters (`client_id`, `scope`, `state`, `redirect_uri`, etc.) glued together correctly.
- `RedirectStrategy` is the interface responsible for *how* the redirect actually happens.
- `DefaultRedirectStrategy` is Spring's built-in implementation — it just calls `response.sendRedirect(url)` under the hood, which sends an HTTP `302 Found` back to the browser.

At this point, the browser follows the redirect and lands on Keycloak's actual login screen — which is *outside* your application entirely.

---

## Tying It All Together

```
Request hits /oauth2/authorization/{registrationId}
        │
        ▼
authorizationRequestResolver.resolve(request)
        │  builds OAuth2AuthorizationRequest using your ClientRegistration
        ▼
OAuth2AuthorizationRequest (authorizationUri, clientId, scopes, state, redirectUri, ...)
        │
        ▼
authorizationRequestRepository.saveAuthorizationRequest(...)
        │  remembers this request (session/cookie) so it can verify Keycloak's response later
        ▼
authorizationRedirectStrategy.sendRedirect(..., authorizationRequest.getAuthorizationRequestUri())
        │  builds the full URL and fires an HTTP 302
        ▼
Browser is redirected to Keycloak's login page
```

---

## Key Interfaces in This Phase

| Interface | Job | Default Implementation |
|---|---|---|
| `OAuth2AuthorizationRequestResolver` | Builds the `OAuth2AuthorizationRequest` from the incoming request + your `ClientRegistration` | `DefaultOAuth2AuthorizationRequestResolver` |
| `AuthorizationRequestRepository` | Remembers the request so it can be validated when Keycloak redirects back | `HttpSessionOAuth2AuthorizationRequestRepository` (session-based — needs overriding for stateless apps) |
| `RedirectStrategy` | Performs the actual browser redirect | `DefaultRedirectStrategy` |

> All three of these are **interfaces** on purpose — Spring exposes them as override points precisely so you can swap in cookie-based storage, custom redirect logic, or extra request parameters without touching the filter itself.

---

## What Happens Next (Phase 2 — not covered here)

After this phase, the user logs in on Keycloak's page. Keycloak then redirects the browser *back* to your `redirect-uri` with a `?code=...&state=...`. That's handled by a **different** filter (`OAuth2LoginAuthenticationFilter`), which:
1. Pulls the saved `OAuth2AuthorizationRequest` back out and checks `state` matches
2. Exchanges the `code` for tokens by calling the `tokenUri`
3. Builds the authenticated `Authentication` object (`OAuth2AuthenticationToken`, or `OidcUser` if `openid` scope was used)

Send that diagram over whenever you're ready and I'll break it down the same way.

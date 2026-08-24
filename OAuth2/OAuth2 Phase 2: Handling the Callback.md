OAuth2 Phase 2: Handling the Callback (OAuth2LoginAuthenticationFilter)

Phase 1 ended with the browser being redirected to Keycloak's login page. This phase picks up after the user logs in on Keycloak, when Keycloak redirects the browser back to your app's redirect-uri with ?code=...&state=... attached. This is where the authorization code actually gets turned into real tokens.

Think of this phase as: "catch the code Keycloak sent back, trade it for real tokens, and log the user in."

Where This Filter Fits
AbstractAuthenticationProcessingFilter   ← generic parent: handles ANY login attempt
        │
        ▼
OAuth2LoginAuthenticationFilter          ← the actual filter for THIS flow
AbstractAuthenticationProcessingFilter is Spring Security's generic base class for "something is trying to authenticate right now." It's the same parent class family that powers form login too — it defines the overall lifecycle (attemptAuthentication() → success/failure handling → save to context).
OAuth2LoginAuthenticationFilter extends it and fills in the OAuth2-specific logic. It owns attemptAuthentication(), plus successfulAuthentication() / unsuccessfulAuthentication() (inherited, triggered automatically based on what attemptAuthentication() returns or throws).

attemptAuthentication() is the entry point — its whole job is to fetch the code from the request, process it, and keep pushing the authentication forward until it either succeeds or throws.

Step-by-Step: Inside attemptAuthentication()
Step 1 — Turn raw request params into a usable map
java
MultiValueMap<String, String> params =
    OAuth2AuthorizationResponseUtils.toMultiMap(request.getParameterMap());

request.getParameterMap() gives you Keycloak's raw callback query params (code, state, possibly error) in a slightly awkward Map<String, String[]> form. toMultiMap() converts it into a MultiValueMap<String, String> — friendlier to work with for the next steps.

OAuth2AuthorizationResponseUtils has two jobs here:

toMultiMap — the conversion above
convert — used a bit later, to turn those params into a proper OAuth2AuthorizationResponse object (Step 4)
Step 2 — Retrieve (and remove) the saved request from Phase 1
java
OAuth2AuthorizationRequest authorizationRequest =
    this.authorizationRequestRepository.removeAuthorizationRequest(request, response);

This is the payoff of Phase 1's saveAuthorizationRequest() call. Spring pulls that saved object back out — and removes it at the same time, since it's single-use. This is also implicitly where state gets validated: if there's no matching saved request, or it doesn't line up, the flow fails here rather than trusting whatever the browser sent.

Step 3 — Figure out which client this callback belongs to
java
String registrationId =
    authorizationRequest.getAttribute(OAuth2ParameterNames.REGISTRATION_ID);
ClientRegistration clientRegistration =
    this.clientRegistrationRepository.findByRegistrationId(registrationId);

Remember attributes from the OAuth2AuthorizationRequest fields in Phase 1? This is what it's for — Spring stashed the registrationId (keycloak-authcode) as an attribute back when the request was first built, and now retrieves it to look up the full ClientRegistration bean again (client ID, secret, token URI, etc.) from your ClientRegistrationRepository.

Step 4 — Build the authorization response + exchange pair
java
OAuth2AuthorizationResponse authorizationResponse =
    OAuth2AuthorizationResponseUtils.convert(params, redirectUri);

OAuth2AuthorizationExchange authorizationExchange =
    new OAuth2AuthorizationExchange(authorizationRequest, authorizationResponse);
authorizationResponse wraps what Keycloak actually sent back (the code, state).
OAuth2AuthorizationExchange pairs what you asked for (authorizationRequest, from Phase 1) with what you got back (authorizationResponse) — a complete record of the round trip, used to validate consistency (e.g. matching state, matching redirect_uri).
Step 5 — Wrap it into an (unauthenticated) token
java
OAuth2LoginAuthenticationToken authenticationRequest =
    new OAuth2LoginAuthenticationToken(clientRegistration,
        new OAuth2AuthorizationExchange(authorizationRequest, authorizationResponse));

This is the OAuth2 equivalent of UsernamePasswordAuthenticationToken before it's verified — it just carries the data needed for authentication, it isn't authenticated yet. isAuthenticated() is false at this point.

Step 6 — Hand off to the AuthenticationManager
java
OAuth2LoginAuthenticationToken authenticationResult =
    (OAuth2LoginAuthenticationToken) this.getAuthenticationManager()
        .authenticate(authenticationRequest);

Just like the username/password flow, everything funnels through the same AuthenticationManager → ProviderManager → AuthenticationProvider chain. This is intentional — Spring Security keeps one consistent authentication pipeline no matter which login mechanism triggered it.

AuthenticationManager
        │
        ▼
   ProviderManager
        │
        ▼
AuthenticationProvider  ── picks ONE of the following, based on scope:
        │
   ┌────┴─────────────────────────────┐
   ▼                                   ▼
OidcAuthorizationCodeAuthenticationProvider   OAuth2LoginAuthenticationProvider
(runs if "openid" IS in scope)                (runs if "openid" is NOT in scope)

This branch point is exactly where OIDC vs. plain OAuth2 diverges internally — same filter, same manager, but a different AuthenticationProvider is selected depending on whether you asked for the openid scope back in your ClientRegistration.

Step 7 — The provider exchanges the code for real tokens

Inside whichever provider ran, this field is what actually talks to Keycloak:

java
private final OAuth2AccessTokenResponseClient<OAuth2AuthorizationCodeGrantRequest>
    accessTokenResponseClient;

And the actual network call:

java
return this.accessTokenResponseClient.getTokenResponse(
    new OAuth2AuthorizationCodeGrantRequest(
        authorizationCodeAuthentication.getClientRegistration(),
        authorizationCodeAuthentication.getAuthorizationExchange()));

This is the real HTTP POST to Keycloak's token-uri, sending the code (+ client credentials) and getting back access_token, refresh_token, and — if openid was in scope — an id_token too. Everything before this step was just preparation; this is the one line that actually leaves your server.

Step 8 — Build the final, fully-authenticated token
java
OAuth2LoginAuthenticationToken authenticationResult = new OAuth2LoginAuthenticationToken(
    authorizationCodeAuthentication.getClientRegistration(),
    authorizationCodeAuthentication.getAuthorizationExchange(),
    oidcUser, mappedAuthorities,
    accessTokenResponse.getAccessToken(),
    accessTokenResponse.getRefreshToken());

return authenticationResult;

This is the same token type as Step 5, but now fully populated — it has the resolved user principal, mapped authorities/roles, and the real tokens attached. isAuthenticated() is now true. This is what bubbles back up out of getAuthenticationManager().authenticate(...) in Step 6.

Step 9 — Persist the tokens for later API calls
java
this.authorizedClientRepository.saveAuthorizedClient(
    authorizedClient, oauth2Authentication, request, response);

This is the same OAuth2AuthorizedClientRepository bean discussed in the previous doc. This is the moment the access/refresh tokens actually get stored somewhere your app can retrieve them from later — e.g. if you need to call another API on behalf of this now-logged-in user.

This is also exactly why, for a stateless app, the default session-based repository won't cut it here either — same gotcha as flagged before, same bean, same fix needed.

Step 10 — Save to the security context (handled by the parent filter)

Once attemptAuthentication() returns successfully, control goes back up to AbstractAuthenticationProcessingFilter, which:

Calls successfulAuthentication()
Stores the result in SecurityContextHolder
Persists the SecurityContext (session, or your custom repository if stateless)

From this point on, the user is authenticated for subsequent requests.

Full Flow, Tied Together
Browser lands on redirect-uri with ?code=...&state=...
        │
        ▼
OAuth2LoginAuthenticationFilter.attemptAuthentication()
        │
        ├─ toMultiMap(request params)
        ├─ removeAuthorizationRequest()      ← retrieves & validates Phase 1's saved request
        ├─ findByRegistrationId()            ← re-fetches ClientRegistration
        ├─ convert() → OAuth2AuthorizationResponse
        ├─ build OAuth2AuthorizationExchange (request + response)
        ├─ build unauthenticated OAuth2LoginAuthenticationToken
        │
        ▼
AuthenticationManager.authenticate()
        │
        ▼
ProviderManager → AuthenticationProvider
        │
        ├── OidcAuthorizationCodeAuthenticationProvider   (if scope has "openid")
        └── OAuth2LoginAuthenticationProvider              (if not)
        │
        ▼
accessTokenResponseClient.getTokenResponse(...)   ← REAL call to Keycloak's token endpoint
        │
        ▼
Fully-authenticated OAuth2LoginAuthenticationToken returned
        │
        ▼
authorizedClientRepository.saveAuthorizedClient(...)   ← tokens stored for future use
        │
        ▼
AbstractAuthenticationProcessingFilter (parent)
        │
        ▼
SecurityContextHolder + session ← user is now logged in
Key Classes at a Glance
Class / Interface	Role
AbstractAuthenticationProcessingFilter	Generic lifecycle for any login attempt (form login, OAuth2, etc.)
OAuth2LoginAuthenticationFilter	OAuth2/OIDC-specific implementation of that lifecycle
OAuth2AuthorizationResponseUtils	Utility for converting raw request params into structured OAuth2 objects
OAuth2AuthorizationExchange	Pairs the original request (Phase 1) with Keycloak's response (this phase)
OAuth2LoginAuthenticationToken	The Authentication object — unauthenticated on the way in, fully populated on the way out
AuthenticationManager / ProviderManager	Same generic delegation pipeline used by every auth type in Spring Security
OAuth2LoginAuthenticationProvider	Handles plain OAuth2 login (no openid scope)
OidcAuthorizationCodeAuthenticationProvider	Handles OIDC login (openid scope present) — additionally validates/parses the id_token
OAuth2AccessTokenResponseClient	Makes the actual HTTP call to the token endpoint
OAuth2AuthorizedClientRepository	Stores the resulting tokens for future use — same bean from your SecurityChain config
Next: Phase 3

Whatever's next in your diagrams — likely the resource-server side (validating the access_token on subsequent API calls) or the SecurityContext/session persistence details — send it over and I'll break it down the same way.

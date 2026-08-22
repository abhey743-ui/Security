package com.Patients.Security;

/*
 * ==========================================================================
 * WHAT LogoutFilter ACTUALLY IS
 * ==========================================================================
 * org.springframework.security.web.authentication.logout.LogoutFilter is a
 * plain servlet Filter. Its constructor is:
 *
 *     new LogoutFilter(LogoutSuccessHandler successHandler, LogoutHandler... handlers)
 *
 * On every request it checks: "does this request match my logout matcher?"
 * (default: POST /logout). If yes, it runs EACH LogoutHandler in order
 * (session invalidation, clearing SecurityContext, deleting cookies, your own
 * custom cleanup...), then finally calls the LogoutSuccessHandler to decide
 * what response to send back. If the request doesn't match, it just passes
 * through to the next filter — that's why you already saw "LogoutFilter.class"
 * used as an anchor point in your chain (Spring Security inserts a default
 * LogoutFilter automatically even if you never call .logout(...) yourself).
 *
 * ==========================================================================
 * THE METHOD YOU CONFIGURE IT WITH
 * ==========================================================================
 * You don't normally construct LogoutFilter yourself. You configure it through
 * HttpSecurity's DSL method:
 *
 *     httpSecurity.logout(logout -> logout. ... )
 *
 * This returns a LogoutConfigurer<HttpSecurity>, the builder that assembles a
 * LogoutFilter for you and slots it into the chain at the correct position.
 * Relevant methods on it:
 *
 *   .logoutUrl("/logout")                 -> which URL triggers logout (default: POST /logout)
 *   .logoutRequestMatcher(RequestMatcher) -> custom matcher if you need a different HTTP method/path
 *   .addLogoutHandler(LogoutHandler)      -> plug in YOUR custom cleanup logic (can call multiple times)
 *   .logoutSuccessHandler(LogoutSuccessHandler) -> what response to send once logout is done
 *   .deleteCookies("cookieName", ...)     -> shortcut that adds a CookieClearingLogoutHandler for you
 *   .invalidateHttpSession(boolean)       -> default true (irrelevant for you — see note below)
 *   .clearAuthentication(boolean)         -> default true, clears SecurityContextHolder
 *   .permitAll()                          -> registers the logout URL as publicly accessible
 *
 * ==========================================================================
 * THE INTERFACES YOU IMPLEMENT FOR CUSTOM LOGIC
 * ==========================================================================
 * 1) LogoutHandler — the extension point for "what should happen during logout."
 *      void logout(HttpServletRequest request, HttpServletResponse response, Authentication authentication);
 *    Multiple handlers run in the ORDER you add them via addLogoutHandler(...).
 *    Built-in implementations Spring already gives you:
 *      - SecurityContextLogoutHandler  (clears SecurityContextHolder, invalidates HttpSession)
 *      - CookieClearingLogoutHandler   (added automatically if you use .deleteCookies(...))
 *      - CsrfLogoutHandler             (removes the CSRF token)
 *
 * 2) LogoutSuccessHandler — the extension point for "what response to send back."
 *      void onLogoutSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication);
 *    Built-in implementations:
 *      - SimpleUrlLogoutSuccessHandler        (redirects to a URL — meant for browser apps)
 *      - HttpStatusReturningLogoutSuccessHandler (just returns an HTTP status, no redirect —
 *                                                  this is the one that fits a stateless REST API)
 *
 * ==========================================================================
 * WHY THIS MATTERS FOR YOUR SETUP SPECIFICALLY
 * ==========================================================================
 * Your app is STATELESS (SessionCreationPolicy.STATELESS) and auth is a signed
 * JWT, not a session. That changes what "logout" can mean:
 *   - SecurityContextLogoutHandler's session-invalidation half does nothing
 *     useful (there is no HttpSession to invalidate).
 *   - The JWT itself stays cryptographically valid until it expires — logging
 *     out does NOT make the token stop working unless you explicitly track
 *     revoked tokens somewhere (a denylist) AND check that denylist during
 *     token validation on every subsequent request.
 *
 * So a *meaningful* logout handler here typically needs to pull the raw token
 * off the request and persist "this token/jti is revoked until it would have
 * expired anyway" — that's the custom LogoutHandler below.
 */

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.stereotype.Component;

/**
 * Custom LogoutHandler: this is "how to provide custom filter logic."
 * You implement LogoutHandler, and LogoutFilter calls this.logout(...) as
 * part of its handler chain when a request matches the logout URL.
 *
 * Adapt TokenRevocationService to whatever store you use (DB table, Redis,
 * etc.) — the point here is just the shape of the integration, not the
 * storage mechanism.
 */
@Component
class TokenRevocationLogoutHandler implements LogoutHandler {

    // e.g. a repository/service that records "this token is revoked until <exp>"
    // private final TokenRevocationService tokenRevocationService;

    @Override
    public void logout(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            // tokenRevocationService.revoke(token);
            // e.g. decode the token's "exp" claim and store (token or its "jti")
            // with a TTL matching that expiry, so your resource-server validation
            // step can reject it on future requests until it would have expired anyway.
        }
        // authentication may be null if the token was already invalid/expired —
        // guard any authentication-based logic accordingly.
    }
}

/*
 * ==========================================================================
 * WIRING IT INTO SecurityFilterChain — ONLY the logout-relevant addition.
 * Everything else in your chain (sessionManagement, authorizeHttpRequests,
 * oauth2ResourceServer, cors, etc.) stays exactly as it already is; this is
 * just the extra `.logout(...)` block you add to that same builder chain.
 * ==========================================================================
 *
 *   httpSecurity
 *       // ... your existing .sessionManagement(...), .authorizeHttpRequests(...),
 *       //     .oauth2ResourceServer(...), .cors(...) stay unchanged ...
 *       .logout(logout -> logout
 *               .logoutUrl("/logout")
 *               .addLogoutHandler(tokenRevocationLogoutHandler)   // your custom logic
 *               .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler())
 *               .permitAll()
 *       )
 *       .build();
 *
 * Note: you do NOT need addFilterAfter/addFilterBefore for this. .logout(...)
 * builds and positions the LogoutFilter for you automatically — manually
 * placing filters is only needed for filters Spring Security's DSL doesn't
 * have a dedicated configurer method for (like your UsernamePasswordAuthenticationFilterImpl).
 */

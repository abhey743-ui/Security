package com.Patients.Security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.CsrfTokenRequestHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import java.io.IOException;
import java.util.List;

/**
 * Security configuration focused on the CSRF setup used in this project.
 *
 * IMPORTANT:
 * - CSRF protection is ENABLED here.
 * - The token is stored/exposed using a cookie repository.
 * - The frontend can receive the cookie named XSRF-TOKEN.
 * - The frontend sends the token back in the X-XSRF-TOKEN header.
 * - /contact is intentionally excluded from CSRF validation.
 * - CsrfTokenFilterGen accesses the token during the request so the
 *   token can be resolved/initialized by Spring Security's CSRF machinery.
 */
@Configuration
public class SecurityChain_CSRF_Explained {

    /**
     * CSRF TOKEN REPOSITORY
     *
     * This bean tells Spring Security WHERE/HOW the CSRF token should be
     * stored and exposed.
     *
     * CookieCsrfTokenRepository uses a browser cookie for the token.
     *
     * withHttpOnlyFalse() is important for a separate SPA/frontend because
     * the frontend JavaScript must be able to read the CSRF cookie and copy
     * its value into the CSRF request header.
     *
     * Typical browser values:
     *
     *   Cookie:
     *       XSRF-TOKEN=abc123
     *
     *   Later request header:
     *       X-XSRF-TOKEN: abc123
     *
     * By default these are the conventional names used by Spring's cookie
     * repository. They can also be set explicitly with setCookieName() and
     * setHeaderName() if you want the names to be obvious in your project.
     */
    @Bean
    public CookieCsrfTokenRepository cookieCsrfTokenRepository() {
        CookieCsrfTokenRepository repository =
                CookieCsrfTokenRepository.withHttpOnlyFalse();

        // Explicitly document the names used by the browser/frontend.
        repository.setCookieName("XSRF-TOKEN");
        repository.setHeaderName("X-XSRF-TOKEN");

        return repository;
    }

    /**
     * CSRF TOKEN REQUEST ATTRIBUTE HANDLER
     *
     * Spring Security can expose the CsrfToken as a request attribute.
     * Your custom CsrfTokenFilterGen reads that request attribute using:
     *
     *     request.getAttribute(CsrfToken.class.getName())
     *
     * This bean gives Spring Security the request handler used for that
     * token exposure/resolution behavior.
     */
    @Bean
    public CsrfTokenRequestHandler csrfTokenRequestAttributeHandler() {
        return new CsrfTokenRequestAttributeHandler();
    }

    /**
     * AUTHENTICATION MANAGER
     *
     * This is not a CSRF bean, but it is a common dependency for the custom
     * username/password authentication filter used by this application.
     * Spring obtains the AuthenticationManager from the configured
     * AuthenticationConfiguration.
     */
    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    /**
     * MAIN SPRING SECURITY FILTER CHAIN
     *
     * This is where the CSRF configuration is actually attached to Spring
     * Security.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            CookieCsrfTokenRepository cookieCsrfTokenRepository,
            CsrfTokenRequestHandler csrfTokenRequestAttributeHandler,
            UsernamePasswordAuthenticationFilterImpl usernamePasswordAuthenticationFilter
    ) throws Exception {

        http
            // -------------------------------------------------------------
            // STATELESS SESSION POLICY
            // -------------------------------------------------------------
            // Your application uses stateless authentication (for example,
            // JWTs). This is separate from CSRF itself.
            .sessionManagement(session ->
                    session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            // -------------------------------------------------------------
            // AUTHORIZATION
            // -------------------------------------------------------------
            // /create/patient, /login and /contact can be reached without
            // already being authenticated.
            //
            // NOTE: permitAll() DOES NOT disable CSRF.
            // A permitted POST can still require a valid CSRF token unless
            // it is explicitly ignored by the CSRF configuration below.
            .authorizeHttpRequests(auth -> auth
                    .requestMatchers("/error", "/create/patient", "/login", "/contact").permitAll()
                    .anyRequest().authenticated()
            )

            // Disable Spring's default form-login page because this project
            // has its own authentication flow.
            .formLogin(form -> form.disable())

            // -------------------------------------------------------------
            // CSRF CONFIGURATION
            // -------------------------------------------------------------
            .csrf(csrf -> csrf
                    // Tell Spring Security to use our cookie-based token
                    // repository.
                    .csrfTokenRepository(cookieCsrfTokenRepository)

                    // /contact is deliberately excluded from CSRF validation.
                    // That means a state-changing request to /contact does
                    // not have to provide a valid CSRF token.
                    .ignoringRequestMatchers("/contact")

                    // Tell Spring Security how the CsrfToken should be
                    // exposed as a request attribute so that code such as
                    // CsrfTokenFilterGen can access it.
                    .csrfTokenRequestHandler(csrfTokenRequestAttributeHandler)
            )

            // -------------------------------------------------------------
            // CUSTOM CSRF TOKEN FILTER
            // -------------------------------------------------------------
            // This does NOT mean "run only during login".
            // It places CsrfTokenFilterGen in Spring Security's filter chain
            // after your custom username/password authentication filter.
            //
            // The filter itself reads the CsrfToken from the request. It does
            // not validate the request and it does not replace Spring's real
            // CSRF protection.
            .addFilterAfter(
                    new CsrfTokenFilterGen(),
                    UsernamePasswordAuthenticationFilterImpl.class
            )

            // -------------------------------------------------------------
            // CORS
            // -------------------------------------------------------------
            // Your frontend and backend are separate origins, so CORS must
            // allow the frontend to call the backend.
            //
            // IMPORTANT for production:
            // allowedOrigins("*") should normally be replaced with your real
            // frontend origin, especially when credentials/cookies are used.
            .cors(cors -> cors.configurationSource(request -> {
                CorsConfiguration configuration = new CorsConfiguration();
                configuration.setAllowedOrigins(List.of("*"));
                configuration.setAllowedHeaders(List.of("*"));
                configuration.setExposedHeaders(List.of("*"));
                configuration.setAllowedMethods(List.of(
                        "GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"
                ));
                return configuration;
            }));

        return http.build();
    }

    /**
     * CUSTOM CSRF TOKEN FILTER
     *
     * This is the same basic filter you shared.
     *
     * What it does:
     * 1. Spring Security's CSRF machinery has access to the CsrfToken.
     * 2. This filter asks the request for that token.
     * 3. csrfToken.getToken() obtains the token value.
     * 4. filterChain.doFilter(...) continues the request.
     *
     * What it does NOT do:
     * - It does not authenticate the user.
     * - It does not validate the CSRF token.
     * - It does not create the cookie name.
     * - It does not itself decide whether /contact is ignored.
     *
     * Those responsibilities belong to the configured CSRF machinery and
     * CsrfTokenRepository.
     */
    public static class CsrfTokenFilterGen extends OncePerRequestFilter {

        @Override
        protected void doFilterInternal(
                HttpServletRequest request,
                HttpServletResponse response,
                FilterChain filterChain
        ) throws ServletException, IOException {

            // Try to obtain the CsrfToken that Spring Security has exposed
            // on the current request.
            CsrfToken csrfToken =
                    (CsrfToken) request.getAttribute(CsrfToken.class.getName());

            // Be defensive: if no token is available for this request, do
            // not dereference null. The normal Spring CSRF configuration is
            // still responsible for actual token validation.
            if (csrfToken != null) {
                String tokenValue = csrfToken.getToken();

                // We intentionally do not print the token in logs.
                // CSRF tokens are security-sensitive values.
                // Accessing the value here makes the purpose of the filter
                // explicit without leaking it anywhere.
            }

            // Always continue the filter chain.
            filterChain.doFilter(request, response);
        }
    }

    /*
     * =====================================================================
     * REQUEST FLOW TO KEEP IN YOUR HEAD
     * =====================================================================
     *
     * 1. Browser initially has no CSRF token.
     *
     * 2. A request reaches Spring Security and the CSRF token is requested /
     *    resolved by the configured CSRF machinery.
     *
     * 3. With CookieCsrfTokenRepository, the token can be exposed to the
     *    browser as:
     *
     *        Set-Cookie: XSRF-TOKEN=ABC123
     *
     * 4. The browser stores the cookie.
     *
     * 5. For a protected POST/PUT/PATCH/DELETE, the frontend reads the
     *    cookie and sends:
     *
     *        X-XSRF-TOKEN: ABC123
     *
     * 6. Spring's CSRF protection compares the supplied token with the token
     *    associated with the browser request/token repository.
     *
     * 7. If the token is valid, the request continues to authentication or
     *    the controller. If it is invalid/missing, Spring can reject the
     *    request with 403.
     *
     * IMPORTANT:
     * - The CSRF token is NOT the JWT.
     * - The CSRF token does NOT mean the user is logged in.
     * - An anonymous browser can have a CSRF token.
     * - GET requests are normally not CSRF-validated, but a GET can still be
     *   a point at which the token is loaded/exposed if the application asks
     *   Spring Security for the token.
     * - An existing token is normally reused rather than generating a new
     *   token for every request.
     */
}

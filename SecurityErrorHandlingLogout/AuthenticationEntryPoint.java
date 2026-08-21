/*
 * ================================================================
 * CustomAuthenticationEntryPoint
 * ================================================================
 *
 * This file explains and demonstrates the exact AuthenticationEntryPoint
 * implementation used in the project.
 *
 * The important idea:
 *
 * AuthenticationEntryPoint is responsible for handling a request when
 * Spring Security determines that the request is NOT authenticated and
 * authentication is required.
 *
 * It is NOT the component that authenticates the user.
 *
 * It is NOT the component that checks the password.
 *
 * It is NOT the component that validates a JWT.
 *
 * Instead:
 *
 *     A protected request arrives
 *              |
 *              v
 *     Spring Security checks authentication
 *              |
 *              v
 *       No valid authentication
 *              |
 *              v
 *     AuthenticationEntryPoint
 *              |
 *              v
 *     "Please login..."
 *
 * In your project, this replaces Spring Security's default
 * authentication-entry behavior with your own response.
 */


package com.Patients.Security.SecurityUtils;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;


/**
 * @Component
 *
 * Tells Spring:
 *
 *     "Create and manage an instance of this class as a Spring bean."
 *
 * Because this class is a bean, Spring can discover it through component
 * scanning.
 *
 * IMPORTANT:
 *
 * @Component itself does NOT make Spring Security call this class.
 *
 * The class is actually used because your SecurityFilterChain explicitly
 * registers an instance of it here:
 *
 *     .exceptionHandling(exception ->
 *         exception.authenticationEntryPoint(
 *             new CustomAuthenticationEntryPoint()
 *         )
 *     )
 *
 * In your exact configuration, you use new CustomAuthenticationEntryPoint()
 * directly, so the @Component annotation is not actually necessary for
 * that particular wiring style.
 *
 * If you instead injected the bean, @Component would be useful.
 */
@Component
public class CustomAuthenticationEntryPoint implements AuthenticationEntryPoint {


    /**
     * ================================================================
     * AuthenticationEntryPoint INTERFACE
     * ================================================================
     *
     * AuthenticationEntryPoint is a Spring Security interface.
     *
     * It represents the component that Spring Security can call when an
     * unauthenticated user tries to access something that requires
     * authentication.
     *
     * The interface gives us one main method:
     *
     *     commence(...)
     *
     * Think of it as:
     *
     *     "Spring Security, when authentication is required but the
     *      request is not authenticated, call this method."
     *
     * Spring Security owns the decision to call the entry point.
     * Your class only defines WHAT should happen after Spring makes
     * that decision.
     */
    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException)
            throws IOException, ServletException {


        /*
         * ============================================================
         * request
         * ============================================================
         *
         * HttpServletRequest represents the incoming HTTP request.
         *
         * For example:
         *
         *     GET /patients
         *
         * The entry point can inspect the request if necessary:
         *
         *     request.getRequestURI()
         *     request.getMethod()
         *     request.getHeader(...)
         *
         * Example:
         *
         *     String path = request.getRequestURI();
         *
         *
         * In your current implementation, you do not use request.
         */


        /*
         * ============================================================
         * response
         * ============================================================
         *
         * HttpServletResponse represents the HTTP response that Spring
         * will send back to the browser/client.
         *
         * Your implementation writes a simple message into that
         * response body.
         *
         * In production APIs, you would commonly also set:
         *
         *     response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
         *
         * and possibly:
         *
         *     response.setContentType("application/json");
         *
         * Your current code only writes the message, exactly as your
         * original implementation does.
         */


        /*
         * ============================================================
         * authException
         * ============================================================
         *
         * This represents the authentication-related exception that
         * caused Spring Security to invoke the entry point.
         *
         * You can inspect it for details, for example:
         *
         *     authException.getMessage()
         *
         * In your current implementation, you do not use it.
         *
         * Important distinction:
         *
         *     AuthenticationException
         *          |
         *          +--> used with AuthenticationEntryPoint
         *
         *     AccessDeniedException
         *          |
         *          +--> handled by AccessDeniedHandler
         *
         * So AuthenticationEntryPoint and AccessDeniedHandler have
         * different jobs.
         */


        /*
         * ============================================================
         * YOUR ACTUAL RESPONSE
         * ============================================================
         *
         * This is exactly the behavior from your project:
         *
         *     response.getWriter().write(
         *         "Please login your are not authenticated"
         *     );
         *
         * getWriter() gives us a Writer for the HTTP response body.
         *
         * write(...) places the message in that body.
         *
         * So the client receives something like:
         *
         *     HTTP response body:
         *
         *     Please login your are not authenticated
         *
         * Again:
         *
         * This method is NOT deciding whether the user is authenticated.
         *
         * Spring Security already made that decision.
         *
         * This method only handles the "authentication is required,
         * but authentication is missing/invalid" situation.
         */

        response.getWriter().write(
                "Please login your are not authenticated"
        );
    }
}


/*
 * ====================================================================
 * HOW SPRING SECURITY USES THIS CLASS
 * ====================================================================
 *
 * This is the relevant part of your SecurityChain:
 *
 *     @Bean
 *     public SecurityFilterChain securityFilterChain(
 *             HttpSecurity httpSecurity,
 *             ...
 *     ) {
 *
 *         return httpSecurity
 *
 *             .authorizeHttpRequests(auth -> auth
 *
 *                 .requestMatchers(
 *                     "/error",
 *                     "/create/patient",
 *                     "/login"
 *                 ).permitAll()
 *
 *                 .anyRequest().authenticated()
 *             )
 *
 *             .exceptionHandling(exception ->
 *                 exception
 *                     .authenticationEntryPoint(
 *                         new CustomAuthenticationEntryPoint()
 *                     )
 *                     .accessDeniedHandler(
 *                         new CustomAccessDeniedHandler()
 *                     )
 *             )
 *
 *             .build();
 *     }
 *
 *
 * The MOST IMPORTANT line for this class is:
 *
 *     .authenticationEntryPoint(
 *         new CustomAuthenticationEntryPoint()
 *     )
 *
 * It tells Spring Security:
 *
 *     "Use this object whenever an AuthenticationEntryPoint is needed."
 *
 *
 * --------------------------------------------------------------------
 * WHY .anyRequest().authenticated() MATTERS
 * --------------------------------------------------------------------
 *
 * You also have:
 *
 *     .anyRequest().authenticated()
 *
 * That means:
 *
 *     "Any request not already permitted must have authentication."
 *
 * So imagine:
 *
 *     GET /patients
 *
 * If /patients is not included in permitAll(), Spring Security requires
 * authentication.
 *
 * If the request has no valid authentication, Spring Security needs to
 * decide what to do with that unauthenticated request.
 *
 * That is where the AuthenticationEntryPoint comes in.
 *
 *
 * --------------------------------------------------------------------
 * YOUR REQUEST FLOW
 * --------------------------------------------------------------------
 *
 * Example:
 *
 *     Browser
 *        |
 *        | GET /patients
 *        v
 *     Spring Security
 *        |
 *        | Is /patients permitted?
 *        |
 *        +---- NO
 *        |
 *        | Does request have valid authentication?
 *        |
 *        +---- NO
 *        |
 *        v
 *     CustomAuthenticationEntryPoint
 *        |
 *        v
 *     commence(...)
 *        |
 *        v
 *     response body:
 *
 *     "Please login your are not authenticated"
 *
 *
 * --------------------------------------------------------------------
 * WHAT IF THE USER IS AUTHENTICATED?
 * --------------------------------------------------------------------
 *
 * Suppose the request contains a valid JWT:
 *
 *     Authorization: Bearer <valid-token>
 *
 * and Spring Security successfully authenticates it.
 *
 * Then the AuthenticationEntryPoint is NOT called simply because the
 * endpoint is protected.
 *
 * The request is allowed to continue to authorization/business logic.
 *
 *
 * --------------------------------------------------------------------
 * WHAT IF THE USER IS AUTHENTICATED BUT NOT AUTHORIZED?
 * --------------------------------------------------------------------
 *
 * This is a different situation.
 *
 * Example:
 *
 *     User is authenticated.
 *     User has no permission to access /admin.
 *
 * That is NOT the job of AuthenticationEntryPoint.
 *
 * This is where AccessDeniedHandler is relevant.
 *
 *
 * Mental model:
 *
 *     Authentication
 *          |
 *          +--> "Who are you?"
 *
 *     Authorization
 *          |
 *          +--> "Are you allowed to do this?"
 *
 *
 * AuthenticationEntryPoint:
 *
 *     "You are required to be authenticated, but you are not."
 *
 * AccessDeniedHandler:
 *
 *     "You are authenticated, but you are not allowed to access this."
 *
 *
 * --------------------------------------------------------------------
 * AUTHENTICATIONENTRYPOINT VS ACCESSDENIEDHANDLER
 * --------------------------------------------------------------------
 *
 *                         Is user authenticated?
 *
 *                         NO              YES
 *                         |                |
 *                         v                v
 *                  Authentication    Authorization
 *                     required?        allowed?
 *
 *                       NO ->           NO ->
 *                       EntryPoint       AccessDeniedHandler
 *
 *
 * This distinction is extremely important when learning Spring
 * Security.
 *
 *
 * --------------------------------------------------------------------
 * WHY SPRING CALLS commence(...)
 * --------------------------------------------------------------------
 *
 * You do NOT normally call:
 *
 *     customAuthenticationEntryPoint.commence(...)
 *
 * yourself.
 *
 * Spring Security invokes it as part of its security processing when
 * authentication is required but the request is unauthenticated.
 *
 * The chain is conceptually:
 *
 *     Incoming HTTP request
 *              |
 *              v
 *     Spring Security filter chain
 *              |
 *              v
 *     Authentication/security decision
 *              |
 *              v
 *     Authentication required but unavailable
 *              |
 *              v
 *     AuthenticationEntryPoint
 *              |
 *              v
 *     commence(...)
 *              |
 *              v
 *     HTTP response
 *
 *
 * --------------------------------------------------------------------
 * WHY THE INTERFACE EXISTS
 * --------------------------------------------------------------------
 *
 * The interface gives Spring Security a standard contract.
 *
 * Spring Security does not need to know your custom class internals.
 *
 * It only needs to know:
 *
 *     "I have an AuthenticationEntryPoint."
 *
 * And that object must provide:
 *
 *     commence(...)
 *
 * This is polymorphism:
 *
 *     AuthenticationEntryPoint
 *              ^
 *              |
 *     CustomAuthenticationEntryPoint
 *
 * Spring Security can work with the interface while you provide your
 * own implementation.
 *
 *
 * --------------------------------------------------------------------
 * YOUR EXACT SECURITY CONFIGURATION
 * --------------------------------------------------------------------
 *
 * This is the relevant configuration you shared:
 *
 *     .exceptionHandling(exception ->
 *         exception
 *             .authenticationEntryPoint(
 *                 new CustomAuthenticationEntryPoint()
 *             )
 *             .accessDeniedHandler(
 *                 new CustomAccessDeniedHandler()
 *             )
 *     )
 *
 * The first method:
 *
 *     authenticationEntryPoint(...)
 *
 * connects unauthenticated-request handling to your class.
 *
 * The second method:
 *
 *     accessDeniedHandler(...)
 *
 * handles authorization failures for authenticated users.
 *
 *
 * --------------------------------------------------------------------
 * ONE VERY IMPORTANT DETAIL ABOUT YOUR @Component
 * --------------------------------------------------------------------
 *
 * Your class has:
 *
 *     @Component
 *
 * but your SecurityChain currently does:
 *
 *     new CustomAuthenticationEntryPoint()
 *
 * These are two different ways of obtaining an object.
 *
 * Option A - let Spring manage it:
 *
 *     @Component
 *     public class CustomAuthenticationEntryPoint
 *             implements AuthenticationEntryPoint {
 *         ...
 *     }
 *
 * and inject it into the SecurityChain.
 *
 * Option B - construct it yourself:
 *
 *     new CustomAuthenticationEntryPoint()
 *
 * in the security configuration.
 *
 * Your current code uses Option B.
 *
 * Therefore, @Component is not required for the exact line:
 *
 *     .authenticationEntryPoint(
 *         new CustomAuthenticationEntryPoint()
 *     )
 *
 * If you want Spring dependency injection to manage the entry point,
 * you can inject it instead.
 *
 *
 * --------------------------------------------------------------------
 * A MORE API-FRIENDLY VERSION
 * --------------------------------------------------------------------
 *
 * Your current implementation is valid as a simple learning example,
 * but an API commonly returns an HTTP 401 status and JSON rather than
 * only plain text.
 *
 * For example:
 *
 *     response.setStatus(
 *         HttpServletResponse.SC_UNAUTHORIZED
 *     );
 *
 *     response.setContentType("application/json");
 *
 *     response.getWriter().write(
 *         "{\"error\":\"Unauthorized\"}"
 *     );
 *
 * The important lesson is that the AuthenticationEntryPoint is the
 * place where you customize the response for an unauthenticated
 * request.
 *
 *
 * --------------------------------------------------------------------
 * FINAL MENTAL MODEL
 * --------------------------------------------------------------------
 *
 *     USER REQUEST
 *          |
 *          v
 *     SPRING SECURITY
 *          |
 *          v
 *     Is authentication required?
 *          |
 *       +--+--+
 *       |     |
 *      NO    YES
 *       |     |
 *       v     v
 *    continue Is user authenticated?
 *             |
 *          +--+--+
 *          |     |
 *         YES   NO
 *          |     |
 *          v     v
 *       continue AuthenticationEntryPoint
 *                       |
 *                       v
 *                   commence()
 *                       |
 *                       v
 *                 HTTP response
 *
 *
 * Remember:
 *
 *     AuthenticationEntryPoint
 *     =
 *     "What response should Spring Security give when authentication
 *      is required but the request is not authenticated?"
 *
 * It is a HANDLER for the failure to authenticate, not the mechanism
 * that performs authentication.
 */

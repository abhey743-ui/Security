/*
 * ================================================================
 * CustomAccessDeniedHandler
 * ================================================================
 *
 * This file focuses ONLY on the CONCEPT of AccessDeniedHandler.
 *
 * The goal is to understand the difference between:
 *
 *     AuthenticationEntryPoint
 *
 * and
 *
 *     AccessDeniedHandler
 *
 * without repeating basic explanations about @Component, beans,
 * dependency injection, etc.
 *
 *
 * ================================================================
 * THE CORE IDEA
 * ================================================================
 *
 * AccessDeniedHandler is about AUTHORIZATION.
 *
 * The request is already authenticated, but the authenticated user
 * is NOT allowed to perform the requested operation.
 *
 *
 * Think:
 *
 *     Authentication:
 *         "Who are you?"
 *
 *     Authorization:
 *         "Now that I know who you are, are you allowed to do this?"
 *
 *
 * AccessDeniedHandler deals with:
 *
 *     "I know who you are, but you are NOT allowed to do this."
 *
 *
 * ================================================================
 * YOUR IMPLEMENTATION
 * ================================================================
 */

package com.Patients.Security.SecurityUtils;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;


@Component
public class CustomAccessDeniedHandler implements AccessDeniedHandler {

    /*
     * ============================================================
     * AccessDeniedHandler INTERFACE
     * ============================================================
     *
     * This interface represents the component Spring Security uses
     * when an AUTHENTICATED request is denied because the user does
     * not have sufficient authority/permission.
     *
     * The important method is:
     *
     *     handle(...)
     *
     * Spring Security invokes that method when it reaches an
     * authorization failure that should be handled by the configured
     * AccessDeniedHandler.
     *
     *
     * The important distinction:
     *
     *     AuthenticationEntryPoint
     *         -> authentication is missing/required
     *
     *     AccessDeniedHandler
     *         -> authentication exists, but authorization fails
     *
     *
     * ============================================================
     * YOUR METHOD
     * ============================================================
     */

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException)
            throws IOException, ServletException {

        /*
         * ========================================================
         * accessDeniedException
         * ========================================================
         *
         * This exception represents the authorization failure.
         *
         * Conceptually:
         *
         *     Spring Security
         *          |
         *          | "The user is authenticated,
         *          |  but does not have the required authority."
         *          v
         *     AccessDeniedException
         *          |
         *          v
         *     AccessDeniedHandler
         *
         *
         * This is therefore different from:
         *
         *     AuthenticationException
         *
         * which is associated with AuthenticationEntryPoint.
         *
         *
         * ========================================================
         * YOUR RESPONSE
         * ========================================================
         *
         * Your implementation sends:
         *
         *     "Yor are unauthorised !"
         *
         * to the response body.
         *
         * The handler is therefore customizing WHAT the client
         * receives after an authorization failure.
         *
         * It is not making the authorization decision itself.
         *
         * Spring Security has already made the decision that access
         * is denied.
         */

        response.getWriter().write("Yor are unauthorised !");
    }
}


/*
 * =================================================================
 * EXACT CONCEPTUAL FLOW
 * =================================================================
 *
 * Imagine:
 *
 *     GET /admin
 *
 * and suppose the endpoint requires:
 *
 *     ROLE_ADMIN
 *
 *
 * The flow is:
 *
 *     Request
 *        |
 *        v
 *     Spring Security
 *        |
 *        v
 *     Authenticate the request
 *        |
 *        |---- No authentication
 *        |        |
 *        |        v
 *        |   AuthenticationEntryPoint
 *        |
 *        |
 *        +---- Authentication succeeds
 *                 |
 *                 v
 *            Check authorization
 *                 |
 *                 |---- Allowed
 *                 |       |
 *                 |       v
 *                 |    Continue
 *                 |
 *                 |
 *                 +---- Denied
 *                         |
 *                         v
 *                 AccessDeniedHandler
 *                         |
 *                         v
 *                    handle(...)
 *                         |
 *                         v
 *                 Your custom response
 *
 *
 * The handler is therefore AFTER the system has established that
 * the request is authenticated but NOT authorized.
 *
 *
 * =================================================================
 * AUTHENTICATIONENTRYPOINT VS ACCESSDENIEDHANDLER
 * =================================================================
 *
 * This is the distinction worth memorizing:
 *
 *
 *     CASE 1
 *     --------------------------------
 *     User is NOT authenticated.
 *
 *     Example:
 *
 *         GET /patients
 *
 *     Endpoint requires authentication.
 *     Request contains no valid authentication.
 *
 *     Result:
 *
 *         AuthenticationEntryPoint
 *
 *
 *     CASE 2
 *     --------------------------------
 *     User IS authenticated.
 *     User does NOT have required permission.
 *
 *     Example:
 *
 *         GET /admin
 *
 *     User:
 *
 *         authenticated = YES
 *         role = USER
 *
 *     Endpoint:
 *
 *         requires ADMIN
 *
 *     Result:
 *
 *         AccessDeniedHandler
 *
 *
 * So:
 *
 *     NOT AUTHENTICATED
 *            ↓
 *     AuthenticationEntryPoint
 *
 *     AUTHENTICATED BUT NOT AUTHORIZED
 *            ↓
 *     AccessDeniedHandler
 *
 *
 * =================================================================
 * WHO CALLS handle()?
 * =================================================================
 *
 * Normally, YOU do not call:
 *
 *     customAccessDeniedHandler.handle(...)
 *
 * yourself.
 *
 * Spring Security invokes the handler as part of its security
 * processing when an authorization decision results in access being
 * denied.
 *
 * Conceptually:
 *
 *     incoming request
 *            |
 *            v
 *     Security filters
 *            |
 *            v
 *     authentication established
 *            |
 *            v
 *     authorization check
 *            |
 *            v
 *     AccessDeniedException
 *            |
 *            v
 *     AccessDeniedHandler
 *            |
 *            v
 *     handle(...)
 *
 *
 * =================================================================
 * HOW IT CONNECTS TO YOUR SECURITYCHAIN
 * =================================================================
 *
 * In the SecurityChain you shared earlier, you have:
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
 *
 * The important part for this class is:
 *
 *     .accessDeniedHandler(
 *         new CustomAccessDeniedHandler()
 *     )
 *
 *
 * That tells Spring Security:
 *
 *     "When an AccessDenied situation needs to be handled,
 *      use this handler."
 *
 *
 * Therefore:
 *
 *     Security decision
 *          |
 *          | access denied
 *          v
 *     configured AccessDeniedHandler
 *          |
 *          v
 *     CustomAccessDeniedHandler.handle(...)
 *
 *
 * =================================================================
 * THE MOST IMPORTANT DIFFERENCE: 401 VS 403
 * =================================================================
 *
 * Conceptually:
 *
 *     Authentication problem
 *         -> HTTP 401 Unauthorized
 *
 *     Authorization problem
 *         -> HTTP 403 Forbidden
 *
 *
 * In many REST APIs, the intended meaning is:
 *
 *     401:
 *         "You have not successfully authenticated."
 *
 *     403:
 *         "You are authenticated, but this resource/action is
 *          forbidden for you."
 *
 *
 * IMPORTANT:
 *
 * Your current handler only writes:
 *
 *     "Yor are unauthorised !"
 *
 * It does NOT explicitly set:
 *
 *     response.setStatus(403);
 *
 * So the code as written is focused on the body and does not
 * explicitly establish the HTTP status in this method.
 *
 * If you want an explicit REST-style response, you could use:
 *
 *     response.setStatus(HttpServletResponse.SC_FORBIDDEN);
 *
 * before writing the body.
 *
 *
 * =================================================================
 * EXAMPLE WITH ROLES
 * =================================================================
 *
 * Imagine:
 *
 *     /admin/report
 *
 * requires:
 *
 *     ROLE_ADMIN
 *
 *
 * User A:
 *
 *     authenticated = NO
 *
 * Result:
 *
 *     AuthenticationEntryPoint
 *
 *
 * User B:
 *
 *     authenticated = YES
 *     role = USER
 *
 * Result:
 *
 *     AccessDeniedHandler
 *
 *
 * User C:
 *
 *     authenticated = YES
 *     role = ADMIN
 *
 * Result:
 *
 *     Request proceeds
 *
 *
 * Therefore:
 *
 *                 /admin/report
 *                       |
 *            +----------+----------+
 *            |                     |
 *       authenticated?             |
 *            |                     |
 *           NO                    YES
 *            |                     |
 *            v                     v
 *       EntryPoint          authorization check
 *                                  |
 *                         +--------+--------+
 *                         |                 |
 *                       ALLOW             DENY
 *                         |                 |
 *                         v                 v
 *                     continue       AccessDeniedHandler
 *
 *
 * =================================================================
 * DO NOT CONFUSE THIS WITH CSRF
 * =================================================================
 *
 * AccessDeniedHandler is about AUTHORIZATION.
 *
 * CSRF is a different security mechanism.
 *
 *
 * For example:
 *
 *     CSRF failure
 *         -> Spring Security's CSRF processing is involved
 *
 *     Authenticated user lacks required authority
 *         -> AccessDeniedHandler is relevant
 *
 *
 * They can appear in the same application, but they answer different
 * security questions.
 *
 *
 * =================================================================
 * DO NOT CONFUSE THIS WITH LOGIN
 * =================================================================
 *
 * AccessDeniedHandler does NOT:
 *
 *     - perform login
 *     - validate username/password
 *     - generate a JWT
 *     - load a user from the database
 *     - decide what role the user should receive
 *
 * Those responsibilities belong to other parts of the security
 * architecture.
 *
 * AccessDeniedHandler simply handles the result:
 *
 *     "This authenticated user is not allowed to access this."
 *
 *
 * =================================================================
 * FINAL MENTAL MODEL
 * =================================================================
 *
 * Think of the security decision as two separate questions:
 *
 *
 *     QUESTION 1:
 *
 *         "Who are you?"
 *
 *         Authentication
 *
 *         If the answer is:
 *
 *             "I cannot authenticate this request."
 *
 *         -> AuthenticationEntryPoint
 *
 *
 *     QUESTION 2:
 *
 *         "Now that I know who you are, may you do this?"
 *
 *         Authorization
 *
 *         If the answer is:
 *
 *             "No, your authority is insufficient."
 *
 *         -> AccessDeniedHandler
 *
 *
 * The simplest rule to remember:
 *
 *
 *     NOT LOGGED IN
 *          ↓
 *     AuthenticationEntryPoint
 *
 *
 *     LOGGED IN BUT NO PERMISSION
 *          ↓
 *     AccessDeniedHandler
 *
 *
 * Your class is therefore the custom "WHAT SHOULD I RETURN TO THE
 * CLIENT?" handler for the SECOND situation.
 *
 */

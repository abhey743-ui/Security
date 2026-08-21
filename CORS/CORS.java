package com.example.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.lang.Nullable;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import jakarta.servlet.http.HttpServletRequest;

import java.util.List;

/**
 * CORS configuration focused ONLY on CORS.
 *
 * This example intentionally does not add any custom filters.
 *
 * ------------------------------------------------------------
 * What is CORS?
 * ------------------------------------------------------------
 *
 * CORS = Cross-Origin Resource Sharing.
 *
 * It controls which browser origins are allowed to call this
 * backend from JavaScript when frontend and backend are on
 * different origins.
 *
 * Example:
 *
 * Frontend:
 *     http://localhost:3000
 *
 * Backend:
 *     http://localhost:8080
 *
 * These are different origins because the port is different.
 *
 * The browser may therefore enforce CORS rules when JavaScript
 * on the frontend calls the backend.
 *
 * IMPORTANT:
 *
 * CORS is primarily a BROWSER security mechanism.
 *
 * It is NOT authentication.
 * It is NOT authorization.
 * It is NOT CSRF protection.
 *
 * ------------------------------------------------------------
 * Origin
 * ------------------------------------------------------------
 *
 * An origin is:
 *
 *     scheme + host + port
 *
 * Example:
 *
 *     http://localhost:3000
 *
 * is different from:
 *
 *     http://localhost:8080
 *
 * and different from:
 *
 *     https://localhost:3000
 *
 * ------------------------------------------------------------
 * Main CORS properties used below
 * ------------------------------------------------------------
 *
 * 1. setAllowedOrigins(...)
 *
 *    Controls WHICH origins are allowed.
 *
 * 2. setAllowedMethods(...)
 *
 *    Controls WHICH HTTP methods are allowed for cross-origin
 *    requests.
 *
 * 3. setAllowedHeaders(...)
 *
 *    Controls WHICH request headers the browser may send.
 *
 * 4. setExposedHeaders(...)
 *
 *    Controls WHICH response headers browser JavaScript is
 *    allowed to read.
 *
 * 5. setAllowCredentials(...)
 *
 *    Allows credentials such as cookies to participate in a
 *    cross-origin request.
 *
 * 6. setMaxAge(...)
 *
 *    Controls how long the browser may cache a successful
 *    preflight result.
 */
@Configuration
public class CorsConfig {

    /**
     * This creates a CORS configuration for Spring Security.
     *
     * The CorsConfigurationSource is consulted by Spring Security
     * when it needs to determine the CORS rules for a request.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {

        return new CorsConfigurationSource() {

            @Override
            public @Nullable CorsConfiguration getCorsConfiguration(
                    HttpServletRequest request) {

                CorsConfiguration corsConfiguration =
                        new CorsConfiguration();

                /*
                 * -------------------------------------------------
                 * 1. ALLOWED ORIGINS
                 * -------------------------------------------------
                 *
                 * This is your original configuration:
                 *
                 *     setAllowedOrigins(List.of("*"))
                 *
                 * It means:
                 *
                 *     "Allow requests from any origin."
                 *
                 * This is useful for development/testing when you
                 * intentionally want every origin to be allowed.
                 *
                 * Example:
                 *
                 *     http://localhost:3000
                 *     http://localhost:4200
                 *     https://myfrontend.com
                 *
                 * would all match.
                 *
                 * -------------------------------------------------
                 *
                 * IMPORTANT WITH COOKIES / CREDENTIALS:
                 *
                 * If your frontend needs cookies in a cross-origin
                 * request, do NOT use "*" together with credentials.
                 *
                 * In that situation, explicitly list the trusted
                 * frontend origin(s), for example:
                 *
                 *     corsConfiguration.setAllowedOrigins(
                 *         List.of("http://localhost:3000")
                 *     );
                 *
                 * More on this below.
                 */
                corsConfiguration.setAllowedOrigins(
                        List.of("*")
                );

                /*
                 * -------------------------------------------------
                 * 2. ALLOWED METHODS
                 * -------------------------------------------------
                 *
                 * Your original snippet did not specify methods.
                 *
                 * You can explicitly say which HTTP methods are
                 * allowed for cross-origin requests.
                 *
                 * Example:
                 *
                 *     GET
                 *     POST
                 *     PUT
                 *     PATCH
                 *     DELETE
                 *     OPTIONS
                 *
                 * OPTIONS is especially important because browsers
                 * use an OPTIONS "preflight" request before some
                 * cross-origin requests.
                 *
                 * You can use:
                 *
                 *     List.of("GET", "POST", "PUT", "PATCH",
                 *             "DELETE", "OPTIONS")
                 *
                 * rather than allowing every method.
                 *
                 * This example is intentionally explicit.
                 */
                corsConfiguration.setAllowedMethods(
                        List.of(
                                HttpMethod.GET.name(),
                                HttpMethod.POST.name(),
                                HttpMethod.PUT.name(),
                                HttpMethod.PATCH.name(),
                                HttpMethod.DELETE.name(),
                                HttpMethod.OPTIONS.name()
                        )
                );

                /*
                 * -------------------------------------------------
                 * 3. ALLOWED REQUEST HEADERS
                 * -------------------------------------------------
                 *
                 * Your original code:
                 *
                 *     setAllowedHeaders(List.of("*"))
                 *
                 * means the browser is allowed to use request
                 * headers required by the cross-origin request,
                 * subject to browser/CORS processing.
                 *
                 * This is convenient for development.
                 *
                 * In production, you can be more restrictive:
                 *
                 *     List.of(
                 *         "Authorization",
                 *         "Content-Type",
                 *         "X-XSRF-TOKEN"
                 *     )
                 *
                 * If your application sends a custom header such as
                 * X-XSRF-TOKEN, that header must be permitted by the
                 * CORS configuration for the browser to send it in a
                 * cross-origin request.
                 */
                corsConfiguration.setAllowedHeaders(
                        List.of("*")
                );

                /*
                 * -------------------------------------------------
                 * 4. EXPOSED RESPONSE HEADERS
                 * -------------------------------------------------
                 *
                 * This is DIFFERENT from allowed request headers.
                 *
                 * allowedHeaders:
                 *
                 *     "Which request headers may the browser send?"
                 *
                 * exposedHeaders:
                 *
                 *     "Which response headers may frontend
                 *      JavaScript read?"
                 *
                 * Example:
                 *
                 * Suppose backend returns:
                 *
                 *     X-Request-Id: 12345
                 *
                 * The frontend cannot necessarily read that custom
                 * response header from JavaScript unless it is exposed.
                 *
                 * Your original:
                 *
                 *     setExposedHeaders(List.of("*"))
                 *
                 * is broad.
                 *
                 * A production configuration can instead specify:
                 *
                 *     List.of("Authorization", "X-Request-Id")
                 *
                 * depending on what the frontend actually needs.
                 */
                corsConfiguration.setExposedHeaders(
                        List.of("*")
                );

                /*
                 * -------------------------------------------------
                 * 5. CREDENTIALS
                 * -------------------------------------------------
                 *
                 * Credentials are relevant when the browser should
                 * include credentials in a cross-origin request,
                 * such as cookies.
                 *
                 * Example frontend:
                 *
                 *     fetch("http://localhost:8080/login", {
                 *         credentials: "include"
                 *     });
                 *
                 * If your application uses cross-origin cookies,
                 * you normally configure:
                 *
                 *     corsConfiguration.setAllowCredentials(true);
                 *
                 * BUT remember:
                 *
                 *     allowCredentials(true)
                 *
                 * should not be combined with a wildcard "*" allowed
                 * origin in the usual explicit-origin configuration.
                 *
                 * Therefore, if you need credentials, prefer:
                 *
                 *     setAllowedOrigins(
                 *         List.of("http://localhost:3000")
                 *     );
                 *
                 * and then:
                 *
                 *     setAllowCredentials(true);
                 *
                 * We leave credentials disabled here because this
                 * section is based on your original wildcard setup.
                 */
                // corsConfiguration.setAllowCredentials(true);

                /*
                 * -------------------------------------------------
                 * 6. PREFLIGHT CACHE
                 * -------------------------------------------------
                 *
                 * The browser may send a preflight OPTIONS request.
                 *
                 * setMaxAge(...) tells the browser how long it may
                 * cache the successful preflight information.
                 *
                 * Example:
                 *
                 *     corsConfiguration.setMaxAge(3600L);
                 *
                 * means roughly one hour.
                 *
                 * This can reduce repeated OPTIONS requests.
                 */
                corsConfiguration.setMaxAge(3600L);

                /*
                 * Return the configuration for this request.
                 */
                return corsConfiguration;
            }
        };
    }

    /**
     * ------------------------------------------------------------
     * Connect CORS to Spring Security
     * ------------------------------------------------------------
     *
     * This is the part equivalent to your original code:
     *
     *     .cors(cors ->
     *         cors.configurationSource(corsConfigurationSource())
     *     )
     *
     * We intentionally do NOT add authentication filters,
     * CSRF filters, JWT filters, etc. here.
     *
     * This example is ONLY demonstrating CORS configuration.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity httpSecurity,
            CorsConfigurationSource corsConfigurationSource)
            throws Exception {

        return httpSecurity

                /*
                 * Tell Spring Security:
                 *
                 * "Use the CorsConfigurationSource bean above
                 *  when processing CORS."
                 */
                .cors(cors ->
                        cors.configurationSource(corsConfigurationSource)
                )

                .build();
    }

    /*
     * ============================================================
     * ALTERNATIVE: SIMPLE SHARED BEAN
     * ============================================================
     *
     * Instead of returning a different CorsConfiguration from the
     * getCorsConfiguration(...) method, you can also create the
     * configuration once and register it through a
     * UrlBasedCorsConfigurationSource.
     *
     * That approach is useful when the same CORS policy should apply
     * to a known URL pattern.
     *
     * Example concept:
     *
     *     CorsConfiguration config = new CorsConfiguration();
     *
     *     config.setAllowedOrigins(
     *         List.of("http://localhost:3000")
     *     );
     *
     *     config.setAllowedMethods(
     *         List.of("GET", "POST", "PUT", "DELETE", "OPTIONS")
     *     );
     *
     *     config.setAllowedHeaders(
     *         List.of("Authorization", "Content-Type", "X-XSRF-TOKEN")
     *     );
     *
     *     config.setAllowCredentials(true);
     *
     *     UrlBasedCorsConfigurationSource source =
     *         new UrlBasedCorsConfigurationSource();
     *
     *     source.registerCorsConfiguration("/**", config);
     *
     *     return source;
     *
     * Then:
     *
     *     .cors(cors ->
     *         cors.configurationSource(source)
     *     )
     *
     * This is often cleaner when you have one global policy.
     */

    /*
     * ============================================================
     * ANOTHER WAY: DEFAULT / WEBMVC CORS
     * ============================================================
     *
     * In Spring applications, CORS can also be configured at the
     * MVC layer using WebMvcConfigurer.
     *
     * Example:
     *
     *     @Configuration
     *     public class WebCorsConfig implements WebMvcConfigurer {
     *
     *         @Override
     *         public void addCorsMappings(CorsRegistry registry) {
     *
     *             registry.addMapping("/**")
     *                     .allowedOrigins("http://localhost:3000")
     *                     .allowedMethods(
     *                         "GET",
     *                         "POST",
     *                         "PUT",
     *                         "PATCH",
     *                         "DELETE",
     *                         "OPTIONS"
     *                     )
     *                     .allowedHeaders(
     *                         "Authorization",
     *                         "Content-Type",
     *                         "X-XSRF-TOKEN"
     *                     )
     *                     .allowCredentials(true)
     *                     .maxAge(3600);
     *         }
     *     }
     *
     * If Spring Security protects your APIs, make sure CORS is also
     * integrated correctly with Spring Security. In your setup,
     * the explicit http.cors(...) configuration makes that
     * relationship clear.
     */

    /*
     * ============================================================
     * PRE-FLIGHT REQUEST
     * ============================================================
     *
     * Sometimes the browser first sends:
     *
     *     OPTIONS /api/patients
     *
     * with headers such as:
     *
     *     Origin: http://localhost:3000
     *     Access-Control-Request-Method: POST
     *     Access-Control-Request-Headers: Authorization, Content-Type
     *
     * This is the browser asking:
     *
     *     "Backend, am I allowed to make the real cross-origin
     *      POST request with these headers?"
     *
     * If the CORS policy allows it, the server responds with CORS
     * response headers.
     *
     * Then the browser sends the real request:
     *
     *     POST /api/patients
     *
     * The OPTIONS request is generally a browser-generated
     * preflight, not an application-specific API operation.
     */

    /*
     * ============================================================
     * REQUEST HEADERS VS RESPONSE HEADERS
     * ============================================================
     *
     * Request:
     *
     *     Origin
     *     Authorization
     *     Content-Type
     *     X-XSRF-TOKEN
     *
     *     ^ allowedHeaders controls what the browser can send.
     *
     * Response:
     *
     *     X-Request-Id
     *     X-Whatever
     *
     *     ^ exposedHeaders controls what browser JavaScript can
     *       read from the response.
     *
     * Do not confuse the two.
     */

    /*
     * ============================================================
     * VERY IMPORTANT: CORS IS NOT CSRF
     * ============================================================
     *
     * CORS:
     *
     *     "Which frontend origins can make/read cross-origin
     *      browser requests?"
     *
     * CSRF:
     *
     *     "Can this state-changing request be trusted as having
     *      been intentionally made by the legitimate application?"
     *
     * They solve different problems.
     *
     * A project can use both.
     *
     * Example:
     *
     *     CORS
     *       -> allow http://localhost:3000
     *
     *     CSRF
     *       -> require X-XSRF-TOKEN for POST/PUT/PATCH/DELETE
     *
     * ============================================================
     */
}

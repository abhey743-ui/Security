# CSRF — Complete Mental Model, Attack, Token Lifecycle, and Spring Security

> **Purpose:** This note explains CSRF from first principles and then maps the idea to a separate Frontend + Spring Boot Backend architecture.
>
> The goal is not to memorize Spring Security classes. The goal is to understand **why the browser, token, cookie, header, authentication, and filters behave the way they do**.

---

## 0. The one mental model to remember

CSRF is a problem that exists mainly because a browser can **automatically attach authentication information, especially cookies, to a request**.

The defensive idea is:

```text
Browser
   |
   | authenticated request
   | + CSRF token supplied in a place the attacker cannot normally read/write
   v
Spring Backend
   |
   | validate CSRF token
   v
Process request
```

A CSRF token is **not a login token**.

A CSRF token is **not a password**.

A CSRF token is **not a JWT**.

A CSRF token is an extra value used to prove that a state-changing browser request came from a context that knows the application's CSRF token.

---

# 1. What is CSRF?

**CSRF = Cross-Site Request Forgery.**

It is an attack where an attacker tricks a victim's browser into making a request to a website where the victim is already authenticated.

The dangerous part is that the browser may automatically include authentication cookies.

The attacker does **not** necessarily need to know the victim's password or session cookie.

The attacker is trying to make the victim's browser say to the real backend:

```text
"Please perform this action using my current authenticated account."
```

The victim may think they are merely visiting the attacker's page.

---

# 2. Why does CSRF exist?

Suppose a user logs into:

```text
https://bank.example
```

The server gives the browser an authentication cookie:

```http
Set-Cookie: SESSIONID=abc123
```

The browser stores it.

Later, the user visits:

```text
https://evil.example
```

If `evil.example` can cause the browser to send a request to `bank.example`, the browser may automatically attach the bank's authentication cookie according to the browser's cookie rules.

That is the root of the problem.

The attacker wants the real backend to see:

```http
POST /transfer
Cookie: SESSIONID=abc123
```

without the victim intentionally performing the transfer.

---

# 3. Concrete CSRF attack example

Imagine your bank has:

```http
POST /transfer
```

with:

```json
{
  "to": "attacker-account",
  "amount": 1000
}
```

The victim is already logged in.

The attacker creates a malicious webpage that causes the victim's browser to submit a request to the bank.

Conceptually:

```text
Victim opens evil.example
        |
        | malicious request
        v
bank.example/transfer
        |
        | browser automatically includes bank authentication cookie
        v
Bank thinks request belongs to logged-in user
        |
        v
Transfer happens
```

The attacker does **not** need to read the response from the bank for the attack itself to be dangerous. They only need to cause the unwanted state-changing request.

---

# 4. Why authentication cookies are the key part

Consider an authenticated request:

```http
POST /profile/email
Cookie: SESSIONID=abc123
```

A server may be tempted to think:

```text
Cookie is valid
        ->
User is authenticated
        ->
Allow request
```

But authentication answers only:

> **Who is this request acting as?**

It does not automatically answer:

> **Did the user intentionally make this request from my application?**

CSRF protection adds another check.

---

# 5. CSRF token: what is it?

A CSRF token is a value generated and tracked by the server-side security system.

Example:

```text
ABC123-very-random-value
```

The important property is that the attacker on another site should not be able to know the victim's valid token.

The browser can possess the token, while the attacker cannot simply read it from the victim's browser.

Then the backend can require:

```text
Authentication credential
        +
CSRF token
        =
Accept state-changing request
```

---

# 6. The CSRF token is NOT authentication

This is one of the most important distinctions.

You can have:

```text
CSRF token exists
        |
        v
User is NOT logged in
```

That is completely valid.

For example, an anonymous browser can have:

```text
XSRF-TOKEN=ABC123
```

before the user has entered a username or password.

Later the browser may send:

```text
CSRF token + username/password
```

and the backend authenticates the user.

So these are separate concepts:

| Thing | Purpose |
|---|---|
| Username/password | Prove identity during authentication |
| Session/JWT | Maintain authenticated state |
| CSRF token | Protect state-changing browser requests against CSRF |

---

# 7. Browser starts with no CSRF token — is that normal?

**Yes.**

When a user opens your application for the first time, there may initially be no CSRF token in the browser.

For example:

```text
Browser cookies:
    no XSRF-TOKEN
```

That is not an error.

The token can be created or loaded when Spring Security's CSRF machinery needs/accesses it.

A key distinction:

> **A token can be generated during processing of a request, including a GET when the token is actually requested/accessed. That does not mean every GET automatically creates a brand-new token.**

Spring Security's current documentation describes CSRF tokens as deferred by default: the token is loaded/generated when it is needed, such as for an unsafe request or when application code renders/uses the token. citehttps://docs.spring.io/spring-security/reference/7.0-SNAPSHOT/servlet/exploits/csrf.html

---

# 8. Does every GET generate a CSRF token?

**No.**

This was the confusing part.

Do not use this mental model:

```text
GET #1 -> new token
GET #2 -> new token
GET #3 -> new token
POST  -> new token
```

Instead think:

```text
Request
   |
   | Is the CSRF token needed/accessed?
   v
Yes -------------------- No
 |                       |
v                        v
Load/generate token      Continue without forcing token load
```

If a token already exists, Spring can load/reuse the existing token rather than inventing a new one for every request.

Spring Security documents deferred CSRF token loading specifically to avoid unnecessarily loading the token on every request. citehttps://docs.spring.io/spring-security/reference/7.0-SNAPSHOT/servlet/exploits/csrf.html

---

# 9. Then why can a GET be involved in getting the token?

Because a GET is still a normal HTTP request flowing through the Spring Security filter chain.

Your application can have an endpoint such as:

```http
GET /csrf
```

whose purpose is to access/expose the CSRF token for a JavaScript client.

The important point is:

```text
GET /csrf
```

is **not protected by CSRF in the same way a POST is** merely because it is a GET.

Instead, the GET can be used as the opportunity for the browser to obtain the token that will later be required for unsafe/state-changing requests.

---

# 10. A clean SPA / separate frontend + backend architecture

Your architecture is approximately:

```text
                Browser
                   |
          ---------------------
          |                   |
          v                   v
     Frontend app        Spring Boot API
     React/Angular       api.example.com
     /login
```

The login page belongs to the frontend.

Spring Boot does not need to serve the HTML login page.

A clean flow is:

```text
1. Browser loads frontend /login

2. Frontend initializes

3. Frontend requests CSRF token from backend

4. Backend exposes token / sets CSRF cookie

5. Browser stores the CSRF cookie

6. User fills username/password

7. Frontend sends POST /login

8. Frontend includes the CSRF token where configured

9. Spring validates CSRF

10. Spring authenticates username/password

11. Authentication state is established (for example session or JWT)
```

The token is not created *because* login succeeded.

The token is a separate mechanism that can exist before login.

---

# 11. Cookie + header: the classic Spring setup

Spring Security's `CookieCsrfTokenRepository` uses these conventional names by default:

```text
Cookie:  XSRF-TOKEN
Header:  X-XSRF-TOKEN
```

Official Spring documentation describes `CookieCsrfTokenRepository` as persisting the CSRF token in the `XSRF-TOKEN` cookie and reading it from the `X-XSRF-TOKEN` request header. citehttps://docs.spring.io/spring-security/reference/7.0-SNAPSHOT/servlet/exploits/csrf.html

So a browser might store:

```text
XSRF-TOKEN=ABC123
```

and the frontend can send:

```http
X-XSRF-TOKEN: ABC123
```

The important security idea is that the token must reach the server through something an attacker from another site cannot simply cause the browser to supply automatically in the same way as an authentication cookie.

---

# 12. Why put the token in a cookie if cookies are the problem?

This is a very important question.

The cookie is only one half of the pattern.

Imagine:

```text
Cookie:
XSRF-TOKEN=ABC123
```

An attacker may cause a cross-site request, and the browser may attach cookies according to cookie rules.

But the CSRF defense checks for the token in a header such as:

```http
X-XSRF-TOKEN: ABC123
```

A basic cross-site attacker does not get to freely read your `XSRF-TOKEN` cookie from your origin and construct the matching custom header.

So the useful combination is:

```text
Cookie                 Header
XSRF-TOKEN=ABC123      X-XSRF-TOKEN: ABC123
       |                          |
       +------------+-------------+
                    |
                    v
             Spring validation
```

The cookie helps the frontend obtain the value.

The header provides the value in a request location that the attacker cannot normally forge/read cross-origin.

---

# 13. Why is HttpOnly sometimes false for the CSRF cookie?

For a JavaScript frontend, the frontend often needs to read the CSRF cookie so it can copy the value into a header.

That is why examples using `CookieCsrfTokenRepository` often use:

```java
CookieCsrfTokenRepository.withHttpOnlyFalse()
```

Spring's documentation explains that `withHttpOnlyFalse()` allows JavaScript frameworks such as Angular to read the cookie. citehttps://docs.spring.io/spring-security/reference/7.0-SNAPSHOT/servlet/exploits/csrf.html

This does **not** mean:

```text
CSRF cookie = authentication cookie
```

They are different cookies with different purposes.

---

# 14. The most important login flow

Assume the browser initially has no CSRF token.

## Step 1 — Frontend login page

The browser loads:

```text
https://frontend.example/login
```

This is just the frontend page.

No user authentication has happened yet.

---

## Step 2 — Frontend obtains CSRF token

The frontend can call:

```http
GET https://api.example.com/csrf
```

The backend's CSRF configuration can expose/set the token.

Conceptually:

```http
Set-Cookie: XSRF-TOKEN=ABC123
```

Browser state:

```text
XSRF-TOKEN=ABC123
```

User is still anonymous.

---

## Step 3 — User clicks Login

The frontend submits:

```http
POST https://api.example.com/login
```

with the configured CSRF header:

```http
X-XSRF-TOKEN: ABC123
```

and the credentials.

---

## Step 4 — Spring validates CSRF

Conceptually:

```text
POST /login
     |
     +-- Is this a request that requires CSRF protection?
     |
     +-- Is the submitted token present?
     |
     +-- Does it match the expected token?
     |
     +-- YES -> continue
```

If the token is missing/invalid, Spring can reject the request.

Spring's `CsrfFilter` is responsible for making the token available, deciding whether the request requires CSRF protection, loading the expected token, resolving the submitted token, and comparing the values. citehttps://docs.spring.io/spring-security/reference/7.0-SNAPSHOT/servlet/exploits/csrf.html

---

## Step 5 — Authentication happens

After the request gets past CSRF validation, the authentication mechanism verifies the username/password.

If authentication succeeds, the application creates/establishes whatever authentication state it uses:

```text
Session
or
JWT
or
another authentication mechanism
```

That is the login step.

Notice the order:

```text
CSRF validation
       ↓
Authentication
```

They are separate checks.

---

# 15. Why a first login can fail and the second one can work

This behavior can be very confusing, but it usually means the frontend attempted login **before the browser had obtained a usable CSRF token**.

Conceptual sequence:

```text
Browser starts
   |
   | no XSRF-TOKEN yet
   |
   +---- POST /login ---------------------->
   |        no CSRF token
   |                              |
   |                              v
   |                           403 CSRF
   |
   +---- GET /csrf / initialization ------>
   |                              |
   |<------- XSRF-TOKEN=ABC123 -------------+
   |
   +---- POST /login ---------------------->
            X-XSRF-TOKEN: ABC123
            username/password
                         |
                         v
                    CSRF passes
                         |
                         v
                    Authentication
```

The **second login is not magically special**.

The browser simply had the necessary token by the time the second request was made.

This is exactly why a production frontend usually obtains/initializes its CSRF token deliberately rather than relying on the first protected request to fail and trigger token initialization.

---

# 16. Why normal POST endpoints can also use the same CSRF token

Suppose you have:

```http
POST /create/patient
```

The user may or may not already be authenticated, depending on your application rules.

CSRF protection is independent of that question.

The backend can require a CSRF token for the state-changing request:

```text
POST /create/patient
        |
        +-- CSRF token valid?
        |
        +-- Authentication required?
        |
        +-- Authorization sufficient?
```

These are separate security decisions.

So it is not unnatural for an anonymous registration request and an authenticated update request to both participate in the CSRF mechanism.

---

# 17. GET vs POST: the rule

Spring Security generally protects against CSRF on unsafe/state-changing HTTP methods rather than safe methods.

Typical safe methods:

```text
GET
HEAD
OPTIONS
TRACE
```

Typical unsafe methods:

```text
POST
PUT
PATCH
DELETE
```

The application should ensure that safe methods really are safe — for example, a GET should not perform a money transfer or delete data.

The CSRF documentation describes the filter as deciding whether the current request requires CSRF protection. citehttps://docs.spring.io/spring-security/reference/7.0-SNAPSHOT/servlet/exploits/csrf.html

---

# 18. "If the token already exists, does a GET create another token?"

Normally, no.

Think of the repository as the place that associates the expected token with the request/user context.

Conceptually:

```text
No token exists
    |
    v
Generate/store token ABC123
```

Then later:

```text
Token already exists
    |
    v
Load/reuse ABC123
```

Do not imagine:

```text
GET -> ABC123
GET -> XYZ999
GET -> LMN777
POST -> QRS555
```

The token lifecycle is not "new random value on every HTTP request."

Spring Security uses deferred token loading by default, and its documentation explicitly describes loading/generation as being deferred until the token is needed. citehttps://docs.spring.io/spring-security/reference/7.0-SNAPSHOT/servlet/exploits/csrf.html

---

# 19. Spring Security pieces you should know

There are several different pieces, and they are easy to mix up.

## `CsrfToken`

Represents the token itself.

It contains information such as:

```text
parameter name
header name
actual token value
```

---

## `CsrfTokenRepository`

Responsible for storing/loading/generating the expected token.

Examples include:

```text
HttpSessionCsrfTokenRepository
CookieCsrfTokenRepository
```

---

## `CookieCsrfTokenRepository`

A repository that persists the CSRF token in a cookie.

The common defaults are:

```text
Cookie:  XSRF-TOKEN
Header:  X-XSRF-TOKEN
```

Official Spring Security documentation confirms these defaults. citehttps://docs.spring.io/spring-security/reference/7.0-SNAPSHOT/servlet/exploits/csrf.html

---

## `CsrfFilter`

Spring Security's filter responsible for the CSRF processing pipeline.

Conceptually it does two large jobs:

1. Make the `CsrfToken` available to the application/request handling machinery.
2. Decide whether CSRF protection is required and, if required, load/resolve/compare the token and reject invalid requests.

Spring Security documents this two-part processing model directly. citehttps://docs.spring.io/spring-security/reference/7.0-SNAPSHOT/servlet/exploits/csrf.html

---

## `CsrfTokenRequestHandler`

Responsible for exposing/accessing the token through the request handling mechanism and resolving a submitted token from the request.

Spring documents that the token is available as a request attribute using `CsrfToken.class.getName()` and also supports request headers/parameters. citehttps://docs.spring.io/spring-security/reference/api/java/org/springframework/security/web/csrf/CsrfTokenRequestAttributeHandler.html

---

# 20. Your teacher's `CsrfTokenFilterGen`

Your teacher created:

```java
public class CsrfTokenFilterGen extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {

        CsrfToken csrfToken =
            (CsrfToken) request.getAttribute(CsrfToken.class.getName());

        csrfToken.getToken();

        filterChain.doFilter(request, response);
    }
}
```

The important line is:

```java
request.getAttribute(CsrfToken.class.getName())
```

This means:

> "Give me the CSRF token that Spring's CSRF machinery has made available on this request."

Then:

```java
csrfToken.getToken();
```

means:

> "Access the token value."

Your filter is **accessing the token**. It is not defining the cookie name or header name.

It is also not the thing that performs the core CSRF validation.

Spring Security's `CsrfFilter` owns the main validation process. citehttps://docs.spring.io/spring-security/reference/7.0-SNAPSHOT/servlet/exploits/csrf.html

---

# 21. What does `addFilterAfter(...)` mean?

You had:

```java
.addFilterAfter(
    new CsrfTokenFilterGen(),
    UsernamePasswordAuthenticationFilterImpl.class
)
```

This means:

> Put `CsrfTokenFilterGen` in the Spring Security filter chain after `UsernamePasswordAuthenticationFilterImpl`.

It does **not** mean:

```text
"Run this only after a successful login."
```

A filter chain is request processing infrastructure. A filter can execute for many requests depending on how the chain is configured and how the filters interact.

So don't interpret:

```java
addFilterAfter(...)
```

as:

```text
"Only when authentication happens."
```

It means ordering in the filter chain.

---

# 22. Your actual configuration

Your uploaded `SecurityChain` currently contains:

```java
.csrf(c -> c.disable())
```

So in the configuration you showed, Spring Security CSRF protection is disabled. Your earlier CSRF configuration and `addFilterAfter(new CsrfTokenFilterGen(), ...)` line are commented out. fileciteturn0file0L68-L80

That means your current application should **not be used as proof of normal enabled-CSRF behavior**.

You also have:

```java
.requestMatchers("/error", "/create/patient", "/login").permitAll()
```

which means those paths are allowed through authorization checks, but **`permitAll()` is not the same thing as disabling CSRF**. Authorization and CSRF are separate mechanisms. fileciteturn0file0L68-L74

---

# 23. `permitAll()` vs CSRF — do not confuse them

This:

```java
.requestMatchers("/login").permitAll()
```

means:

> An unauthenticated user is allowed to reach `/login` from the authorization perspective.

It does **not** automatically mean:

> CSRF is disabled for `/login`.

Similarly:

```java
.requestMatchers("/create/patient").permitAll()
```

does not automatically turn off CSRF validation.

Think of two gates:

```text
Request
   |
   +---- CSRF gate
   |
   +---- Authentication/Authorization gate
```

A request can be:

```text
permitAll + CSRF protected
```

or:

```text
authenticated + CSRF protected
```

or, if configured, CSRF can be disabled/ignored for selected paths.

---

# 24. Why your current browser may still show `XSRF-TOKEN`

If you previously ran your application with CSRF enabled and a cookie was set:

```text
XSRF-TOKEN=ABC123
```

then later change the server to:

```java
.csrf(c -> c.disable())
```

disabling Spring Security CSRF does not magically guarantee that an old cookie disappears from the browser immediately.

The browser may still display an old cookie until it is expired/deleted.

So when debugging:

```text
DevTools
 -> Application
 -> Cookies
 -> XSRF-TOKEN
```

check what is actually present.

An old cookie is not proof that CSRF is currently enabled.

---

# 25. The difference between the CSRF cookie and the authentication cookie

Do not mix these up.

For example:

```text
XSRF-TOKEN   -> CSRF protection
SESSIONID    -> authentication/session
```

or your application may use:

```text
XSRF-TOKEN   -> CSRF protection
JWT          -> authentication
```

The CSRF cookie says roughly:

> "Here is a CSRF value the frontend can use."

The authentication cookie/session/JWT says roughly:

> "This request is associated with this authenticated identity."

They solve different problems.

---

# 26. Why the attacker cannot simply use the CSRF token

The whole system relies on the attacker not being able to obtain the victim's token from the application's origin and then place it into the required request header.

Same-origin policy and normal browser security boundaries are part of why this works.

Conceptually:

```text
victim browser
     |
     +-- has XSRF-TOKEN=ABC123
     |
     +-- can make same-origin frontend code read/use it

attacker.example
     |
     +-- tries to make POST to bank.example
     |
     +-- cannot normally read bank.example's XSRF-TOKEN
     |
     +-- cannot normally create matching header value
```

Therefore the forged request is missing the expected CSRF token and is rejected.

---

# 27. Why a CSRF token belongs in a header instead of only a cookie

If the server checked only:

```http
Cookie: XSRF-TOKEN=ABC123
```

that would not solve the core problem, because cookies are automatically handled by the browser.

The classic CSRF defense needs the token supplied in a value the attacker cannot cause the victim browser to attach automatically in the same way.

A request header such as:

```http
X-XSRF-TOKEN: ABC123
```

provides that additional proof.

Spring's documentation describes this cookie + header convention and the reason the token is supplied from a non-automatic request location. citehttps://docs.spring.io/spring-security/reference/7.0-SNAPSHOT/servlet/exploits/csrf.html

---

# 28. Complete request timeline

Here is the whole story in one picture:

```text
                  FIRST VISIT
                       |
                       v
             Browser has no CSRF token
                       |
                       v
             Frontend loads /login
                       |
                       v
             Frontend requests /csrf
                       |
                       v
            Spring CSRF machinery
               loads/generates token
                       |
                       v
               XSRF-TOKEN=ABC123
                       |
                       v
                 Browser stores it
                       |
                       v
              User enters credentials
                       |
                       v
                 POST /login
                       |
          X-XSRF-TOKEN: ABC123
                       |
                       v
               CSRF validation
                       |
                     PASS
                       |
                       v
              Authentication check
                       |
                     PASS
                       |
                       v
                  User logged in
                       |
                       v
            Later POST /api/resource
                       |
          X-XSRF-TOKEN: ABC123
                       |
                       v
               CSRF validation
                       |
                     PASS
```

---

# 29. What happens if the CSRF token is missing?

Suppose the backend expects a token but the frontend sends:

```http
POST /login
Cookie: XSRF-TOKEN=ABC123
```

but does not provide the required header:

```http
X-XSRF-TOKEN: ABC123
```

Depending on the configuration and request type, Spring Security can reject the request because the expected token is missing or invalid.

That is the purpose of the validation step.

---

# 30. What happens if the token is wrong?

Suppose the server expects:

```text
ABC123
```

but the request says:

```http
X-XSRF-TOKEN: WRONG
```

Then conceptually:

```text
expected = ABC123
provided = WRONG

ABC123 != WRONG

=> reject request
```

Spring Security has dedicated missing/invalid CSRF token exceptions for these situations. citehttps://docs.spring.io/spring-security/reference/api/java/org/springframework/security/web/csrf/package-summary.html

---

# 31. Why CSRF is usually irrelevant for GET

Imagine:

```http
GET /patients
```

If GET merely reads data, there is generally no state change that a CSRF attacker needs to forge.

That is why safe methods are normally outside the core CSRF validation requirement.

But if you make a GET perform a dangerous state change like:

```http
GET /deletePatient?id=123
```

you have created a bad API design.

An attacker could potentially cause a victim browser to visit that URL very easily.

So a strong rule is:

> **GET should be safe and should not perform state-changing operations.**

---

# 32. CSRF vs CORS

These are different.

## CORS

CORS controls whether frontend JavaScript from one origin is allowed to make/read cross-origin requests under browser CORS rules.

## CSRF

CSRF protects state-changing requests from unwanted cross-site actions when the browser has authentication context.

It is possible to configure CORS incorrectly and still have CSRF.

It is also possible to disable CSRF and still have strict CORS.

Do not use:

```text
CORS = CSRF protection
```

That is incorrect.

---

# 33. CSRF vs JWT

Another common confusion.

A JWT may be an authentication credential:

```text
Authorization: Bearer <JWT>
```

A CSRF token is a separate anti-forgery value:

```text
X-XSRF-TOKEN: <CSRF_TOKEN>
```

If your authentication token is stored somewhere that JavaScript explicitly reads and puts into an `Authorization` header, the classic cookie-based CSRF risk is different from a session-cookie architecture because the browser does not automatically attach an `Authorization` header to arbitrary cross-site requests.

That does not mean JWT automatically makes an application immune to every browser attack; it means the classic CSRF threat model is strongly tied to automatically attached credentials such as cookies.

---

# 34. How to debug CSRF in Chrome DevTools

When something feels "random," stop guessing and inspect the actual requests.

Open:

```text
DevTools
 -> Network
```

Then inspect each request in order.

For the response, look at:

```text
Response Headers
Set-Cookie
```

For the request, look at:

```text
Request Headers
Cookie
X-XSRF-TOKEN
```

Also inspect:

```text
Application
 -> Cookies
```

Look for:

```text
XSRF-TOKEN
```

Then answer these questions:

```text
1. Did a response set XSRF-TOKEN?
2. Is XSRF-TOKEN stored in the browser?
3. Does the POST contain X-XSRF-TOKEN?
4. Is the token value the expected one?
5. Is CSRF actually enabled in Spring?
6. Is another custom filter involved?
```

This turns "Spring did something weird" into an observable request/response sequence.

---

# 35. The exact header and cookie names

Recommended conventional names with `CookieCsrfTokenRepository`:

```text
Cookie name:
XSRF-TOKEN

Header name:
X-XSRF-TOKEN
```

Equivalent request example:

```http
POST /login HTTP/1.1
Host: api.example.com
Cookie: XSRF-TOKEN=ABC123
X-XSRF-TOKEN: ABC123
Content-Type: application/json

{
  "username": "john",
  "password": "secret"
}
```

Spring Security documents these exact default names for `CookieCsrfTokenRepository`. citehttps://docs.spring.io/spring-security/reference/7.0-SNAPSHOT/servlet/exploits/csrf.html

---

# 36. Typical Spring configuration

A cookie-based setup can look like:

```java
CookieCsrfTokenRepository csrfRepository =
        CookieCsrfTokenRepository.withHttpOnlyFalse();

http
    .csrf(csrf -> csrf
        .csrfTokenRepository(csrfRepository)
    );
```

If you want explicit names:

```java
CookieCsrfTokenRepository csrfRepository =
        CookieCsrfTokenRepository.withHttpOnlyFalse();

csrfRepository.setCookieName("XSRF-TOKEN");
csrfRepository.setHeaderName("X-XSRF-TOKEN");

http
    .csrf(csrf -> csrf
        .csrfTokenRepository(csrfRepository)
    );
```

These names are already the conventional defaults, so explicit setters are optional.

---

# 37. The role of your custom filter — final understanding

Your custom filter:

```java
CsrfToken csrfToken =
    (CsrfToken) request.getAttribute(CsrfToken.class.getName());

csrfToken.getToken();
```

should be understood as:

```text
Spring's CSRF machinery
        |
        | makes token available
        v
request attribute
        |
        | your filter reads it
        v
CsrfToken
```

Your teacher's filter is therefore **not the entire CSRF system**.

It is one small part of the request processing chain.

The repository, CSRF filter, request handler, cookie repository, frontend, and browser all participate in the overall behavior.

---

# 38. The most important misconceptions — destroyed

## ❌ "CSRF token is created when the user logs in"

No.

A CSRF token can exist before authentication.

---

## ❌ "If a user is not logged in, there cannot be a CSRF token"

No.

An anonymous browser can have one.

---

## ❌ "Every GET generates a brand-new CSRF token"

No.

Token loading is deferred and a stored token can be reused.

---

## ❌ "The CSRF token is the JWT"

No.

They serve completely different security purposes.

---

## ❌ "`permitAll()` means CSRF is disabled"

No.

Authorization and CSRF protection are separate concerns.

---

## ❌ "My custom `CsrfTokenFilterGen` is the thing that validates CSRF"

Not by itself.

Spring Security's CSRF processing is centered on `CsrfFilter` and the associated token repository/request handling components.

---

## ❌ "If I disable CSRF, an old XSRF-TOKEN cookie must instantly disappear"

No.

A browser cookie can remain until it is expired/deleted.

---

# 39. The entire concept in 10 lines

```text
1. A browser can automatically send authentication cookies.
2. An attacker can sometimes trick that browser into making a state-changing request.
3. That is the CSRF problem.
4. The server creates/tracks a random CSRF token.
5. The legitimate frontend can obtain the token.
6. The browser stores it, often as XSRF-TOKEN.
7. The frontend sends it back, often as X-XSRF-TOKEN.
8. Spring validates it for requests that require CSRF protection.
9. Only then does the application continue to authentication/business logic.
10. The CSRF token and the login/authentication state are separate things.
```

---

# 40. Final mental picture

```text
                   ┌─────────────────────┐
                   │       BROWSER       │
                   └──────────┬──────────┘
                              │
                              │ GET /csrf
                              ▼
                   ┌─────────────────────┐
                   │    SPRING SECURITY  │
                   │                     │
                   │ CsrfTokenRepository │
                   │ CsrfFilter          │
                   │ RequestHandler      │
                   └──────────┬──────────┘
                              │
                              │ token
                              ▼
                     XSRF-TOKEN=ABC123
                              │
                              ▼
                         BROWSER STORES
                              │
                              │
                 ┌────────────┴────────────┐
                 │                         │
                 │ POST /login             │ POST /create/patient
                 │                         │
                 │ X-XSRF-TOKEN: ABC123    │ X-XSRF-TOKEN: ABC123
                 │ username/password       │ request body
                 │                         │
                 └────────────┬────────────┘
                              │
                              ▼
                       CSRF VALIDATION
                              │
                         token matches?
                              │
                     ┌────────┴────────┐
                     │                 │
                    YES               NO
                     │                 │
                     ▼                 ▼
               Continue            Reject
                     │
                     ▼
              Authentication /
              Business logic
```

---

# 41. The sentence to remember forever

> **Authentication answers "Who are you?" CSRF protection answers "Did this state-changing browser request include the CSRF proof that the legitimate application expected?"**

They are related because both participate in securing a request, but they are **not the same mechanism**.

---

# References

- Spring Security — Cross Site Request Forgery (CSRF): https://docs.spring.io/spring-security/reference/7.0-SNAPSHOT/servlet/exploits/csrf.html
- Spring Security — `CsrfTokenRequestAttributeHandler`: https://docs.spring.io/spring-security/reference/api/java/org/springframework/security/web/csrf/CsrfTokenRequestAttributeHandler.html
- Spring Security — CSRF API package: https://docs.spring.io/spring-security/reference/7.1-SNAPSHOT/api/java/org/springframework/security/web/csrf/package-summary.html


# 2. Username + Password Login — The Full Flow (Rewritten)

## The one-paragraph version

You type your username and password into a form and hit submit. Spring Security catches that request before it reaches your app, pulls out the username and password, and passes them down a chain of objects — each one doing exactly one job — until it finds your stored account, checks your password against it, and (if it matches) marks you as "logged in" for the rest of your visit. Every box below is one of those objects. Nothing here is magic; it's a fairly plain chain of Java method calls.

---

## Plain-English map first

Before the deep dive, here's the whole thing in one breath, no class names:

> The **form** hands your username/password to a **filter**. The filter asks a **manager** to authenticate it. The manager finds the **one provider** that knows how to check passwords. That provider **fetches your account** from wherever it's stored, makes sure the account itself is okay (not locked/disabled), then **checks your password** against the stored hash. If it matches, a **"you're logged in" object** gets created and saved so every future request on this browser knows who you are.

Now the same thing, slower, with the real class names — this is what's actually running.

---

## Cast of characters

| Name | Interface or Class? | One-line job |
|---|---|---|
| `UsernamePasswordAuthenticationFilter` | Class | Grabs `username`/`password` from the request, builds a token, calls the manager |
| `AuthenticationManager` | **Interface** | The single entry point — "please authenticate this" |
| `ProviderManager` | Class (implements `AuthenticationManager`) | Picks the right provider(s) and delegates |
| `AuthenticationProvider` | **Interface** | "Can you handle this auth type? If so, authenticate it" |
| `DaoAuthenticationProvider` | Class | The provider used for username/password specifically |
| `AbstractUserDetailsAuthenticationProvider` | Abstract class | Shared skeleton logic every "look up a user, then check them" provider reuses |
| `UserDetailsService` | **Interface** | "Go fetch this user's stored credentials" |
| `UserDetails` | **Interface** | The shape of "a user record" Spring Security understands |
| `PasswordEncoder` | **Interface** | "Does this raw password match this stored hash?" |
| `SecurityContextHolder` | Static utility class | Where the finished, logged-in `Authentication` object gets stored |

---

## Part 1 — Finding the user

*(This is the diagram above, part 1.)*

### Step 1 — the filter builds an unproven claim
`UsernamePasswordAuthenticationFilter.attemptAuthentication()` reads `username`/`password` off the request and wraps them into a `UsernamePasswordAuthenticationToken` — at this point it's just a **claim**, not proof. `isAuthenticated()` is `false`, there are no roles attached yet. Think of it as a sticky note that says "someone claims to be Dave, with this password" — nobody's checked it yet.

### Step 2 — `AuthenticationManager`, the front door
```java
Authentication authenticate(Authentication authentication) throws AuthenticationException;
```
Just a contract, one method. Spring wires in `ProviderManager` as the real thing behind it.

### Step 3 — `ProviderManager` picks a specialist
An app might support password login *and* OAuth2 *and* LDAP all at once, so `ProviderManager` holds a **list** of `AuthenticationProvider`s and asks each one, in turn: *"do you support this kind of login?"* For a plain password login, the answer "yes" comes from `DaoAuthenticationProvider`.

> **Worth knowing:** if none of `ProviderManager`'s own providers can handle it, it can hand off to an optional **parent `AuthenticationManager`** — useful when an app has multiple login endpoints sharing the same providers.

### Step 4 — `DaoAuthenticationProvider`, the password specialist
It doesn't reinvent the whole process — the shared skeleton ("fetch user → check user's okay → check password → check again → build result") lives in its parent class, `AbstractUserDetailsAuthenticationProvider`. `DaoAuthenticationProvider` only fills in two specific blanks:
- **How do I fetch this user?** (`retrieveUser`)
- **How do I check the password?** (`additionalAuthenticationChecks`)

This "parent handles the process, child fills in the specifics" pattern is exactly what lets Spring Security support totally different login methods with one shared engine underneath.

### Step 5 — fetching the actual account
`retrieveUser()` calls:
```java
userDetailsService.loadUserByUsername(username)
```
`UserDetailsService` is the interface for "go get this user from wherever they're stored" — in-memory list, database, LDAP, your own custom lookup. (Full breakdown of this in file 3.) It hands back a `UserDetails` object: username, password **hash**, roles, and a few true/false flags (enabled, locked, expired).

---

## Part 2 — Verifying and finishing

*(This is the diagram above, part 2.)*

### Step 6 — is the account itself okay?
Before even looking at the password, `AbstractUserDetailsAuthenticationProvider` runs **pre-authentication checks**: is the account enabled? Not locked? Not expired? If any of these fail, it throws immediately — **the password is never even compared** for a disabled or locked account. This matters: a locked account with the *correct* password still gets rejected, because this check runs first.

### Step 7 — the actual password check
Now `DaoAuthenticationProvider.additionalAuthenticationChecks()` runs:
```java
this.passwordEncoder.get().matches(presentedPassword, userDetails.getPassword())
```
This returns `void` — it either does nothing (success) or throws `BadCredentialsException`. The real boolean check happens one layer down, inside `PasswordEncoder.matches()`. You configure which hashing algorithm this uses (commonly `BCryptPasswordEncoder`, wrapped in a `DelegatingPasswordEncoder` — see file 13) — Spring Security itself doesn't care which algorithm, it just calls whatever encoder bean you've registered.

**Important nuance for easy language:** the password is never "decrypted" and compared — hashes are one-way. What actually happens is the *raw password you typed* gets hashed the same way, and the two hashes are compared. This is why a data breach exposing your hashed password database still doesn't hand attackers your actual passwords.

### Step 8 — building the "you're really logged in" object
If the password matched, `createSuccessAuthentication()` builds a **new** `UsernamePasswordAuthenticationToken` — this one has `isAuthenticated() == true` and carries your roles/authorities. This is a genuinely different object from the "claim" built back in Step 1 — Spring Security never just flips a boolean on the original one.

### Step 9 — cleanup on the way back up
The finished token travels back: `DaoAuthenticationProvider` → `ProviderManager`. `ProviderManager` **erases the raw password** from the token (via a `CredentialsContainer` call) before forwarding it — so the plaintext password doesn't linger in memory any longer than it has to.

### Step 10 — you're logged in
Back in the filter (technically its parent, `AbstractAuthenticationProcessingFilter`):
1. The authenticated token is saved into `SecurityContextHolder` (full detail in file 4).
2. A session is created if needed, and a `JSESSIONID` cookie goes out to the browser.
3. `onAuthenticationSuccess()` runs — usually redirecting you to whatever page you originally asked for.

If anything failed in Steps 4–7, `onAuthenticationFailure()` runs instead — typically back to the login page with an error.

---

## Common points of confusion (worth reading if this is new to you)

- **"Where's the SQL query?"** — There isn't one in any class shown here. `UserDetailsService` is the only place a database gets touched, and *which* implementation you use decides that (file 3). Everything else is pure logic/comparison.
- **"Why two different `UsernamePasswordAuthenticationToken` objects?"** — Because "an unverified claim" and "a verified identity" are meaningfully different things, and keeping them as separate objects (rather than one mutable object with a flag) makes it impossible to accidentally treat an unverified claim as if it were verified.
- **"What if the password is right but the account is disabled?"** — Rejected, and rejected *before* the password is even checked (Step 6). This is deliberate — it avoids leaking, via response timing, whether a disabled account's password would've been correct.
- **"Does `AuthenticationManager.authenticate()` ever get called more than once per login?"** — No, once per login attempt. It's `ProviderManager` internally that may try multiple *providers* if you've configured more than one — but that's one call into the manager from the filter's point of view.

---

**Next up, whenever you're ready:** just tell me the next topic and I'll do the same treatment — diagram(s) first, then the full write-up.

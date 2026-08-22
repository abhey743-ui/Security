package com.Patients.Security;

import com.Patients.Entity.RolePermission;
import com.Patients.Repository.RolePermissionRepository;
import lombok.AllArgsConstructor;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * WHAT "Converter" MEANS HERE
 * ----------------------------
 * org.springframework.core.convert.converter.Converter<S, T> is a generic Spring
 * functional interface with one method: T convert(S source). It just means
 * "a thing that turns an S into a T" — nothing OAuth2-specific about it.
 *
 * You're implementing Converter<Jwt, Collection<GrantedAuthority>>, i.e. "a thing
 * that turns a verified Jwt into the list of authorities the user should have."
 *
 * HOW IT PLUGS INTO THE RESOURCE SERVER
 * --------------------------------------
 * Spring's JwtAuthenticationConverter is what actually builds the Authentication
 * object after a token's signature has been verified. By default it extracts
 * authorities itself (from the "scope"/"scp" claim, prefixed "SCOPE_"). You
 * override that default by handing it YOUR converter instead:
 *
 *     JwtAuthenticationConverter jwtAuthenticationConverter = new JwtAuthenticationConverter();
 *     jwtAuthenticationConverter.setJwtGrantedAuthoritiesConverter(rolePermissionConverter);
 *     jwtAuthenticationProvider.setJwtAuthenticationConverter(jwtAuthenticationConverter);
 *
 * (this is the wiring already sitting in ResourceServerConfig's
 * jwtIssuerAuthenticationManagerResolver bean, on the "APPLICATION" issuer branch).
 *
 * THE FULL REQUEST FLOW
 * ----------------------
 * 1. Request arrives with Authorization: Bearer <token>.
 * 2. JwtIssuerAuthenticationManagerResolver reads "iss", routes to the
 *    "APPLICATION" JwtAuthenticationProvider.
 * 3. NimbusJwtDecoder verifies the signature (HS256 + your secret) and standard
 *    claims (exp, nbf...). If invalid -> 401, this class is never called.
 * 4. JwtAuthenticationProvider calls jwtAuthenticationConverter.convert(jwt),
 *    which internally calls THIS class's convert(jwt) to get the authorities,
 *    then wraps everything into a JwtAuthenticationToken (principal name taken
 *    from the "userId" claim, per setPrincipalClaimName("userId")).
 * 5. That JwtAuthenticationToken becomes the request's
 *    SecurityContextHolder.getContext().getAuthentication() — available in any
 *    controller as @AuthenticationPrincipal Jwt jwt, or via
 *    SecurityContextHolder for role checks like hasRole(...) / hasAuthority(...).
 *
 * So: this class runs ONCE PER REQUEST, after signature verification, and its
 * whole job is "given this token, what can this user do?" — answered by hitting
 * the database rather than trusting whatever authorities the token itself claims.
 * That's a deliberate (and reasonable) design choice: permissions can change
 * server-side without needing to re-issue tokens.
 *
 * HOW @Component + @AllArgsConstructor GET IT HERE
 * ---------------------------------------------------
 * @Component makes this a Spring-managed bean, picked up by component scanning.
 * Lombok's @AllArgsConstructor generates a constructor taking
 * RolePermissionRepository, which Spring uses to inject it automatically.
 * Because it's a bean, it can simply be added as a method parameter anywhere —
 * e.g. jwtIssuerAuthenticationManagerResolver(RolePermissionConverter rolePermissionConverter)
 * — and Spring supplies the same singleton instance. No manual wiring needed;
 * you don't have to `new RolePermissionConverter(...)` anywhere yourself.
 */
@Component
@AllArgsConstructor
public class RolePermissionConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    private RolePermissionRepository rolePermissionRepository;

    @Override
    public Collection<GrantedAuthority> convert(Jwt source) {

        // NOTE: getClaims().get("userName").toString() throws NullPointerException
        // if the "userName" claim is absent. source.getClaimAsString("userName")
        // is the safer, idiomatic equivalent (returns null instead of throwing,
        // and avoids calling toString() on an Object).
        String userName = source.getClaims().get("userName").toString();

        List<RolePermission> result =
                rolePermissionRepository.findRolesAndPermissionsByUserName(userName);

        List<GrantedAuthority> authorities = new ArrayList<>();

        for (RolePermission rolePermission : result) {
            // "ROLE_" prefix is a Spring Security convention: hasRole("ADMIN")
            // checks for authority "ROLE_ADMIN" automatically (it adds the
            // prefix for you), so roles MUST be stored with this prefix to work
            // with hasRole(...) / @PreAuthorize("hasRole('ADMIN')").
            authorities.add(new SimpleGrantedAuthority("ROLE_" + rolePermission.getRole().getRoleName()));

            // No prefix here -> checked via hasAuthority("PERMISSION_NAME"),
            // NOT hasRole(...) (hasRole always expects the ROLE_ prefix).
            authorities.add(new SimpleGrantedAuthority(rolePermission.getPermission().getPermissionName()));
        }

        return authorities;
    }
}

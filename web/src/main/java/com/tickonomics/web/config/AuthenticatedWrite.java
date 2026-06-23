package com.tickonomics.web.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.security.access.prepost.PreAuthorize;

/**
 * Method-level authorization for state-changing (write) endpoints. Permits the call when auth is
 * globally disabled ({@code security.auth-disabled=true}, e.g. local dev) OR when the caller is
 * authenticated. This keeps write endpoints open in the auth-disabled sandbox while requiring a
 * valid principal (JWT) in production without lockout risk.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@PreAuthorize("@securityProperties.authDisabled() or isAuthenticated()")
public @interface AuthenticatedWrite {
}

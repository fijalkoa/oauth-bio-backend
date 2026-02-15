package com.fijalkoa.biosso.security;

import lombok.Getter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Collection;
import java.util.Collections;

/**
 * Partial Authentication Token for Biometric Verification Pending
 * 
 * After password authentication, if user has biometric enrollment active,
 * this token is returned indicating biometric verification is required.
 * 
 * Frontend can detect this and show biometric capture UI.
 * Only /api/biometric/verify endpoint is accessible with this token.
 */
@Getter
public class BiometricPendingAuthenticationToken implements Authentication {

    private static final long serialVersionUID = 1L;
    private static final String BIOMETRIC_PENDING_ROLE = "BIOMETRIC_VERIFICATION_REQUIRED";

    private final String email;
    private final String principal;
    private final boolean authenticated;

    public BiometricPendingAuthenticationToken(String email) {
        this.email = email;
        this.principal = email;
        this.authenticated = false;  // Not yet fully authenticated
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        // Only allow biometric verification endpoint
        return Collections.singleton(new SimpleGrantedAuthority(BIOMETRIC_PENDING_ROLE));
    }

    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
    public Object getDetails() {
        return null;
    }

    @Override
    public Object getPrincipal() {
        return principal;
    }

    @Override
    public boolean isAuthenticated() {
        return authenticated;
    }

    @Override
    public void setAuthenticated(boolean isAuthenticated) throws IllegalArgumentException {
        // Immutable token
    }

    @Override
    public String getName() {
        return email;
    }
}

package com.yizhaoqi.smartpai.service;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * Resolves the authenticated user name written to store audit fields.
 */
@Component
public class StoreOperatorResolver {

    /**
     * Uses the Spring Security principal after JWT authentication, with a system fallback for non-request callers.
     */
    public String resolve(Authentication authentication) {
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return "system";
        }
        String operator = authentication.getName();
        return operator == null || operator.isBlank() ? "system" : operator;
    }
}

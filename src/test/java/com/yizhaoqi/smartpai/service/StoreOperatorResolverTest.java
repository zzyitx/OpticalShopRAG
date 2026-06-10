package com.yizhaoqi.smartpai.service;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StoreOperatorResolverTest {

    private final StoreOperatorResolver resolver = new StoreOperatorResolver();

    @Test
    void shouldResolveAuthenticatedUsernameForAuditFields() {
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                "store-admin",
                null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
        );

        assertEquals("store-admin", resolver.resolve(authentication));
    }

    @Test
    void shouldUseSystemWhenAuthenticationIsMissingOrUnauthenticated() {
        UsernamePasswordAuthenticationToken unauthenticated =
                new UsernamePasswordAuthenticationToken("store-admin", null);

        assertEquals("system", resolver.resolve(null));
        assertEquals("system", resolver.resolve(unauthenticated));
    }

    @Test
    void shouldUseSystemForAnonymousAuthentication() {
        AnonymousAuthenticationToken anonymous = new AnonymousAuthenticationToken(
                "anonymous-key",
                "anonymousUser",
                List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))
        );

        assertEquals("system", resolver.resolve(anonymous));
    }
}

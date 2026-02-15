package com.fijalkoa.biosso.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.view.RedirectView;

/**
 * @deprecated AuthorizationController is deprecated
 * 
 * This controller is no longer used. Spring Authorization Server (SAS) provides all OAuth2/OIDC
 * endpoints automatically at:
 * - /oauth2/authorize - Authorization endpoint
 * - /oauth2/token - Token endpoint (handled by SAS)
 * - /oauth2/jwks - JWKS endpoint
 * - /.well-known/openid-configuration - OIDC discovery
 * - /oauth2/userinfo - UserInfo endpoint
 * - /oauth2/revoke - Token revocation
 * 
 * These methods redirect to SAS endpoints to maintain backward compatibility during migration.
 */
@Slf4j
@Controller
@RequestMapping("/oauth2")
public class AuthorizationController {

    /**
     * Redirects old /oauth2/authorize to Spring Authorization Server
     */
    @GetMapping("/authorize")
    public RedirectView authorizeRedirect() {
        log.warn("⚠️ Deprecated: /oauth2/authorize called. Use Spring Authorization Server endpoint instead.");
        return new RedirectView("/oauth2/authorize");
    }

    /**
     * Redirects old /oauth2/authorize POST to Spring Authorization Server
     */
    @PostMapping("/authorize")
    public RedirectView handleAuthorizationPost() {
        log.warn("⚠️ Deprecated: POST /oauth2/authorize called. Use Spring Authorization Server endpoint instead.");
        return new RedirectView("/oauth2/authorize");
    }
}


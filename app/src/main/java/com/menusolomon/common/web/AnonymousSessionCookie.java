package com.menusolomon.common.web;

import org.springframework.http.ResponseCookie;

public final class AnonymousSessionCookie {
    public static final String NAME = "menu_solomon_session";
    private static final long MAX_AGE_SECONDS = 60L * 60 * 24 * 365;

    private AnonymousSessionCookie() {
    }

    public static ResponseCookie issue(String rawToken, boolean secure) {
        return ResponseCookie.from(NAME, rawToken)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Lax")
                .path("/")
                .maxAge(MAX_AGE_SECONDS)
                .build();
    }
}

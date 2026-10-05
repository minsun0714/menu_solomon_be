package com.menusolomon.common.web;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;

public final class SessionCookies {

    public static HttpHeaders headers(String issuedToken) {
        HttpHeaders headers = new HttpHeaders();
        if (issuedToken != null) {
            headers.add(HttpHeaders.SET_COOKIE, ResponseCookie.from(WebConstants.SESSION_COOKIE_NAME, issuedToken)
                    .httpOnly(true).secure(true).sameSite("Lax").path("/").maxAge(31536000)
                    .build().toString());
        }
        return headers;
    }

    private SessionCookies() {
    }
}

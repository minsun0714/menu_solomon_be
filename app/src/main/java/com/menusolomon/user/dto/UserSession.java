package com.menusolomon.user.dto;

import com.menusolomon.user.domain.User;

/** The raw token is returned only when a new cookie must be issued; never part of API JSON. */
public record UserSession(User user, String issuedToken) {
}

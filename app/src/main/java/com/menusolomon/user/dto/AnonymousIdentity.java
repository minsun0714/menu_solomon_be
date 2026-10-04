package com.menusolomon.user.dto;

public record AnonymousIdentity(Long userId, String rawToken, boolean newlyIssued) {
}

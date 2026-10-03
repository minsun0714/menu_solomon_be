package com.menusolomon.common.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    INVALID_INVITE_TOKEN(HttpStatus.NOT_FOUND, "Invalid invite token"),
    TEAM_NOT_FOUND(HttpStatus.NOT_FOUND, "Team not found"),
    MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "Member not found"),
    ALREADY_TEAM_MEMBER(HttpStatus.CONFLICT, "User is already an active team member"),
    ADMIN_TRANSFER_REQUIRED(HttpStatus.CONFLICT, "Admin must transfer ownership before leaving"),
    PERMISSION_DENIED(HttpStatus.FORBIDDEN, "Permission denied"),
    BAD_REQUEST(HttpStatus.BAD_REQUEST, "Bad request");

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }
}

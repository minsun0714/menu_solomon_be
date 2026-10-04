package com.menusolomon.common.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "Request validation failed"),
    SESSION_REQUIRED(HttpStatus.UNAUTHORIZED, "A valid session is required"),
    NOT_TEAM_MEMBER(HttpStatus.FORBIDDEN, "팀원만 사용할 수 있습니다."),
    ADMIN_REQUIRED(HttpStatus.FORBIDDEN, "팀 관리자 권한이 필요합니다."),
    MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "팀원을 찾을 수 없습니다."),
    ADMIN_TRANSFER_REQUIRED(HttpStatus.CONFLICT, "관리자 권한을 위임한 후 탈퇴해 주세요."),
    TEAM_NOT_FOUND(HttpStatus.NOT_FOUND, "팀을 찾을 수 없습니다."),
    INVITATION_NOT_FOUND(HttpStatus.NOT_FOUND, "Invitation was not found"),
    TEAM_RESTAURANT_NOT_FOUND(HttpStatus.NOT_FOUND, "팀 식당을 찾을 수 없습니다."),
    RESTAURANT_ALREADY_REGISTERED(HttpStatus.CONFLICT, "같은 팀에 이미 등록된 식당입니다."),
    REVIEW_NOT_FOUND(HttpStatus.NOT_FOUND, "리뷰를 찾을 수 없습니다."),
    KAKAO_PLACE_NOT_FOUND(HttpStatus.NOT_FOUND, "카카오 장소를 찾을 수 없습니다."),
    KAKAO_API_ERROR(HttpStatus.BAD_GATEWAY, "카카오 장소 API 호출에 실패했습니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");

    private final HttpStatus status;
    private final String detail;

    ErrorCode(HttpStatus status, String detail) {
        this.status = status;
        this.detail = detail;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getDetail() {
        return detail;
    }
}

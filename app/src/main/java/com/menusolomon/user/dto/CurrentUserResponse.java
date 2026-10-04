package com.menusolomon.user.dto;

import com.menusolomon.user.domain.User;

public record CurrentUserResponse(String id, String nickname) {

    public static CurrentUserResponse from(User user) {
        return new CurrentUserResponse("user_" + user.getId(), user.getNickname());
    }
}

package com.menusolomon.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record NicknameUpdateRequest(
        @NotBlank @Size(min = 2, max = 12) @Pattern(regexp = "[^\\p{Cntrl}]*") String nickname
) {
    public NicknameUpdateRequest {
        if (nickname != null && nickname.codePoints().noneMatch(Character::isISOControl)) {
            nickname = nickname.strip();
        }
    }
}

package com.menusolomon.user.domain;

import com.menusolomon.common.exception.BusinessException;
import com.menusolomon.common.exception.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

@Entity
@Table(name = "users")
@Getter
public class User {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "anonymous_token_hash", nullable = false, unique = true) private String anonymousTokenHash;
    @Column(nullable = false) private String nickname;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected User() {}

    private User(String anonymousTokenHash, String nickname, Instant now) {
        this.anonymousTokenHash = anonymousTokenHash;
        this.nickname = nickname;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static User create(String anonymousTokenHash, String nickname, Instant now) { return new User(anonymousTokenHash, nickname, now); }
    public static User create(String anonymousTokenHash, Instant now) { return new User(anonymousTokenHash, suggestNickname(), now); }
    public static User createAnonymous(String anonymousTokenHash, Instant now) { return create(anonymousTokenHash, now); }
    public static String suggestNickname() { return "anonymous" + UUID.randomUUID().toString().replace("-", "").substring(0, 6); }
    public void changeNickname(String nickname, Instant now) {
        String normalized = nickname == null ? null : nickname.strip();
        if (normalized == null || normalized.isBlank() || normalized.length() < 2 || normalized.length() > 12
                || normalized.codePoints().anyMatch(Character::isISOControl)) throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        this.nickname = normalized;
        this.updatedAt = now;
    }
}

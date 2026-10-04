package com.menusolomon.user.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;

@Entity
@Table(name = "users")
@Getter
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "anonymous_token_hash", nullable = false, unique = true)
    private String anonymousTokenHash;

    @Column(nullable = false)
    private String nickname;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected User() {
    }

    private User(String anonymousTokenHash, String nickname, Instant now) {
        this.anonymousTokenHash = anonymousTokenHash;
        this.nickname = nickname;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static User create(String anonymousTokenHash, String nickname, Instant now) {
        return new User(anonymousTokenHash, nickname, now);
    }
}

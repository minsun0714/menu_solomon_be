package com.menusolomon.team.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;

@Entity
@Table(name = "teams")
@Getter
public class Team {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 30)
    private String name;

    @Column(length = 100)
    private String description;

    @Column(name = "invite_token", nullable = false, unique = true)
    private String inviteToken;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected Team() {
    }

    public Team(String name, String description, String inviteToken, Instant now) {
        this.name = name;
        this.description = description;
        this.inviteToken = inviteToken;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void regenerateInviteToken(String inviteToken, Instant now) {
        this.inviteToken = inviteToken;
        this.updatedAt = now;
    }
}

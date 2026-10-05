package com.menusolomon.user.service;

import com.menusolomon.user.domain.User;
import java.util.Optional;
import com.menusolomon.user.dto.UserSession;

public interface UserService {

    /** Reuses a stored identity or issues a server-generated token and creates its user. */
    UserSession getOrCreateSession(String rawSessionToken);

    User getOrCreateBySessionToken(String rawSessionToken);

    /** Resolves an existing identity without creating a user; unknown tokens require a session. */
    User getBySessionToken(String rawSessionToken);

    User updateNickname(String rawSessionToken, String nickname);

    /** Optional identity for public reads; never creates a user. */
    Optional<User> findBySessionToken(String rawSessionToken);
}

package com.menusolomon.user.service;

import com.menusolomon.user.domain.User;
import java.util.Optional;

public interface UserService {

    User getOrCreateBySessionToken(String rawSessionToken);

    /** Resolves an existing identity without creating a user; unknown tokens require a session. */
    User getBySessionToken(String rawSessionToken);

    /** Optional identity for public reads; never creates a user. */
    Optional<User> findBySessionToken(String rawSessionToken);
}

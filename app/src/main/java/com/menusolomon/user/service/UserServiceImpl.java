package com.menusolomon.user.service;

import com.menusolomon.common.exception.BusinessException;
import com.menusolomon.common.exception.ErrorCode;
import com.menusolomon.user.domain.User;
import com.menusolomon.user.dto.UserSession;
import com.menusolomon.user.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.Base64;
import java.security.SecureRandom;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserServiceImpl implements UserService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final Clock clock;

    public UserServiceImpl(UserRepository userRepository, Clock clock) {
        this.userRepository = userRepository;
        this.clock = clock;
    }

    @Override
    @Transactional
    public UserSession getOrCreateSession(String rawSessionToken) {
        return findBySessionToken(rawSessionToken).map(user -> new UserSession(user, null))
                .orElseGet(() -> {
                    byte[] bytes = new byte[32];
                    RANDOM.nextBytes(bytes);
                    String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
                    User user = userRepository.save(User.createAnonymous(hash(token), Instant.now(clock)));
                    return new UserSession(user, token);
                });
    }

    @Override
    @Transactional
    public User getOrCreateBySessionToken(String rawSessionToken) {
        requireToken(rawSessionToken);
        String tokenHash = hash(rawSessionToken);
        return userRepository.findByAnonymousTokenHash(tokenHash)
                .orElseGet(() -> userRepository.save(User.createAnonymous(tokenHash, Instant.now(clock))));
    }

    @Override
    @Transactional(readOnly = true)
    public User getBySessionToken(String rawSessionToken) {
        requireToken(rawSessionToken);
        return findBySessionToken(rawSessionToken)
                .orElseThrow(() -> new BusinessException(ErrorCode.SESSION_REQUIRED));
    }

    @Override
    @Transactional
    public User updateNickname(String rawSessionToken, String nickname) {
        User user = getBySessionToken(rawSessionToken);
        user.changeNickname(nickname, Instant.now(clock));
        return user;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<User> findBySessionToken(String rawSessionToken) {
        if (rawSessionToken == null || rawSessionToken.isBlank()) {
            return Optional.empty();
        }
        return userRepository.findByAnonymousTokenHash(hash(rawSessionToken));
    }

    private void requireToken(String rawSessionToken) {
        if (rawSessionToken == null || rawSessionToken.isBlank()) {
            throw new BusinessException(ErrorCode.SESSION_REQUIRED);
        }
    }

    private String hash(String rawSessionToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(rawSessionToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}

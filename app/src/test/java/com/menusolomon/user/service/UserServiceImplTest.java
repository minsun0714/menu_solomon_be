package com.menusolomon.user.service;

import static com.menusolomon.user.fixture.UserFixture.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.menusolomon.common.exception.BusinessException;
import com.menusolomon.common.exception.ErrorCode;
import com.menusolomon.user.domain.User;
import com.menusolomon.user.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    private static final String TOKEN = "opaque-session-token";
    // Independent known SHA-256 vector; do not derive the expectation using production code.
    private static final String TOKEN_HASH = "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08";
    private static final Instant NOW = Instant.parse("2026-10-04T08:30:00Z");

    @Mock
    private UserRepository userRepository;

    private UserServiceImpl userService;

    @BeforeEach
    void setUp() {
        userService = new UserServiceImpl(userRepository, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("동일 세션 토큰은 기존 사용자를 재사용하며 저장하지 않는다")
    void getOrCreate_existingToken_returnsExistingUserWithoutSave() {
        User existing = user(1L, TOKEN_HASH, "익명 사용자 1234");
        when(userRepository.findByAnonymousTokenHash(TOKEN_HASH)).thenReturn(Optional.of(existing));

        assertThat(userService.getOrCreateBySessionToken("test")).isSameAs(existing);

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("신규 사용자 저장 시 원문 토큰 대신 SHA-256 해시와 익명 닉네임을 저장한다")
    void getOrCreate_unknownToken_savesHashedTokenAndAnonymousProfile() {
        when(userRepository.findByAnonymousTokenHash(TOKEN_HASH)).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User result = userService.getOrCreateBySessionToken("test");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(result).isSameAs(captor.getValue());
        assertThat(result.getAnonymousTokenHash()).isEqualTo(TOKEN_HASH).isNotEqualTo("test");
        assertThat(result.getNickname()).matches("익명[0-9a-f]{6}");
        assertThat(result.getCreatedAt()).isEqualTo(NOW);
        assertThat(result.getUpdatedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("읽기 전용 사용자 조회는 알 수 없는 토큰의 사용자를 생성하지 않는다")
    void getBySessionToken_unknownToken_throwsSessionRequiredWithoutCreatingUser() {
        when(userRepository.findByAnonymousTokenHash(TOKEN_HASH)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getBySessionToken("test"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SESSION_REQUIRED);

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("필수 세션 조회는 기존 사용자를 반환한다")
    void getBySessionToken_existingToken_returnsExistingUser() {
        User existing = user(1L, TOKEN_HASH, "익명 사용자 1234");
        when(userRepository.findByAnonymousTokenHash(TOKEN_HASH)).thenReturn(Optional.of(existing));

        assertThat(userService.getBySessionToken("test")).isSameAs(existing);

        verify(userRepository, never()).save(any());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n"})
    @DisplayName("식별이 필요한 요청은 비어 있는 토큰을 거절하며 DB에 접근하지 않는다")
    void requiredIdentity_missingToken_throwsSessionRequired(String token) {
        assertThatThrownBy(() -> userService.getOrCreateBySessionToken(token))
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SESSION_REQUIRED);
        assertThatThrownBy(() -> userService.getBySessionToken(token))
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SESSION_REQUIRED);

        verifyNoInteractions(userRepository);
    }

    @Test
    @DisplayName("공개 조회의 쿠키 누락은 사용자 생성과 DB 조회를 하지 않는다")
    void findBySessionToken_withoutCookie_returnsEmptyWithoutRepositoryAccess() {
        assertThat(userService.findBySessionToken(null)).isEmpty();
        assertThat(userService.findBySessionToken(" ")).isEmpty();

        verifyNoInteractions(userRepository);
    }

    @Test
    @DisplayName("공개 조회의 알 수 없는 쿠키는 빈 결과이며 사용자를 생성하지 않는다")
    void findBySessionToken_unknownToken_returnsEmptyWithoutSave() {
        when(userRepository.findByAnonymousTokenHash(TOKEN_HASH)).thenReturn(Optional.empty());

        assertThat(userService.findBySessionToken("test")).isEmpty();

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("서로 다른 브라우저 토큰은 서로 다른 해시와 닉네임으로 사용자를 생성한다")
    void getOrCreate_differentTokens_createDistinctProfiles() {
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User first = userService.getOrCreateBySessionToken(TOKEN);
        User second = userService.getOrCreateBySessionToken("another-browser-token");

        assertThat(first.getAnonymousTokenHash()).isNotEqualTo(second.getAnonymousTokenHash());
        assertThat(first.getNickname()).isNotEqualTo(second.getNickname());
    }

    @Test
    @DisplayName("쿠키 없는 최초 요청은 서버 난수 토큰을 발급하고 그 해시만 저장한다")
    void getOrCreateSession_withoutCookie_issuesServerToken() throws Exception {
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var session = userService.getOrCreateSession(null);

        assertThat(session.issuedToken()).hasSizeGreaterThanOrEqualTo(32);
        String expectedHash = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                .digest(session.issuedToken().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        assertThat(session.user().getAnonymousTokenHash()).isEqualTo(expectedHash);
        verify(userRepository).save(session.user());
    }

    @Test
    @DisplayName("유효한 기존 쿠키는 같은 사용자를 반환하며 새 토큰을 발급하지 않는다")
    void getOrCreateSession_existingCookie_preservesIdentity() {
        User existing = user(1L, TOKEN_HASH, "익명 사용자 1234");
        when(userRepository.findByAnonymousTokenHash(TOKEN_HASH)).thenReturn(Optional.of(existing));

        var session = userService.getOrCreateSession("test");

        assertThat(session.user()).isSameAs(existing);
        assertThat(session.issuedToken()).isNull();
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("알 수 없는 클라이언트 토큰은 그대로 저장하지 않고 서버 토큰으로 교체한다")
    void getOrCreateSession_unknownCookie_rotatesToken() {
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var session = userService.getOrCreateSession("test");

        assertThat(session.issuedToken()).isNotEqualTo("test").hasSizeGreaterThanOrEqualTo(32);
        assertThat(session.user().getAnonymousTokenHash()).isNotEqualTo(TOKEN_HASH);
    }
    @Test
    @DisplayName("닉네임 변경은 기존 사용자의 이름과 갱신 시각만 변경한다")
    void updateNickname_existingSession_updatesSameUser() {
        User existing = User.create(TOKEN_HASH, "기존이름", NOW.minusSeconds(60));
        when(userRepository.findByAnonymousTokenHash(TOKEN_HASH)).thenReturn(Optional.of(existing));
        assertThat(userService.updateNickname("test", "  익명4d88d  ")).isSameAs(existing);
        assertThat(existing.getNickname()).isEqualTo("익명4d88d");
        assertThat(existing.getUpdatedAt()).isEqualTo(NOW);
        assertThat(existing.getCreatedAt()).isEqualTo(NOW.minusSeconds(60));
        assertThat(existing.getAnonymousTokenHash()).isEqualTo(TOKEN_HASH);
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("유효하지 않은 세션의 닉네임 변경은 새 사용자 생성 없이 거절한다")
    void updateNickname_unknownSession_doesNotCreateUser() {
        when(userRepository.findByAnonymousTokenHash(TOKEN_HASH)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> userService.updateNickname("test", "새이름"))
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SESSION_REQUIRED);
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("쿠키 없는 닉네임 변경은 사용자 저장소에 접근하지 않는다")
    void updateNickname_missingSession_doesNotAccessRepository() {
        assertThatThrownBy(() -> userService.updateNickname(null, "새이름"))
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SESSION_REQUIRED);
        verifyNoInteractions(userRepository);
    }

}

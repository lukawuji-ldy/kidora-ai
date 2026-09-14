package com.wuji.kidora.ai.agent.server.auth;

import com.wuji.kidora.ai.common.auth.AuthUser;
import com.wuji.kidora.ai.common.auth.JwtTokenService;
import com.wuji.kidora.ai.common.exception.ErrorCode;
import com.wuji.kidora.ai.common.exception.KidoraException;
import com.wuji.kidora.ai.memory.repo.LearnerProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AuthService 注册路径单测。
 *
 * @author liudy
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtTokenService jwtTokenService;
    @Mock
    private LearnerProfileRepository learnerProfileRepository;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(jdbcTemplate, passwordEncoder, jwtTokenService, learnerProfileRepository);
    }

    @Test
    void registerSuccessCreatesUserAndLearner() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq("newparent")))
                .thenReturn(0);
        when(passwordEncoder.encode("secret12")).thenReturn("hash");
        when(jwtTokenService.issueToken(any(AuthUser.class))).thenReturn("jwt-token");
        when(jdbcTemplate.update(anyString(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(1);

        LoginResponse resp = authService.register(new RegisterRequest(
                "newparent", "secret12", "小明", "ELEMENTARY"));

        assertEquals("jwt-token", resp.token());
        assertEquals("newparent", resp.username());
        assertEquals("newparent", resp.nickname());
        assertEquals("parent", resp.role());
        assertNotNull(resp.learnerId());
        assertTrue(resp.learnerId().startsWith("lrn_"));
        assertTrue(resp.userId().startsWith("u_"));

        ArgumentCaptor<String> cefrCap = ArgumentCaptor.forClass(String.class);
        verify(learnerProfileRepository).insert(
                eq(resp.learnerId()),
                eq(resp.userId()),
                eq("小明"),
                isNull(),
                cefrCap.capture(),
                eq("emma"));
        assertEquals("A1", cefrCap.getValue());
    }

    @Test
    void registerRejectsTakenUsername() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq("parent1")))
                .thenReturn(1);
        KidoraException ex = assertThrows(KidoraException.class, () ->
                authService.register(new RegisterRequest("parent1", "secret12", "Amy", "BEGINNER")));
        assertEquals(ErrorCode.BAD_REQUEST, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("占用"));
        verify(learnerProfileRepository, never()).insert(any(), any(), any(), any(), any(), any());
    }

    @Test
    void registerRejectsInvalidLevel() {
        KidoraException ex = assertThrows(KidoraException.class, () ->
                authService.register(new RegisterRequest("okuser", "secret12", "Amy", "C2")));
        assertEquals(ErrorCode.BAD_REQUEST, ex.getErrorCode());
    }

    @Test
    void registerRejectsShortPasswordAndBlankNickname() {
        assertThrows(KidoraException.class, () ->
                authService.register(new RegisterRequest("okuser", "123", "Amy", "BEGINNER")));
        assertThrows(KidoraException.class, () ->
                authService.register(new RegisterRequest("okuser", "secret12", "  ", "BEGINNER")));
    }

    @Test
    void getProfileReturnsActiveUser() throws Exception {
        stubUserRow("u_1", "parent1", "hash", "展示名", "parent", "ACTIVE");

        ProfileResponse profile = authService.getProfile("u_1");

        assertEquals("u_1", profile.userId());
        assertEquals("parent1", profile.username());
        assertEquals("展示名", profile.nickname());
        assertEquals("parent", profile.role());
    }

    @Test
    void updateNicknameReissuesToken() throws Exception {
        stubUserRow("u_1", "parent1", "hash", "旧昵称", "parent", "ACTIVE");
        when(jdbcTemplate.update(anyString(), eq("新昵称"), any(), eq("u_1"))).thenReturn(1);
        when(jwtTokenService.issueToken(any(AuthUser.class))).thenReturn("new-jwt");

        LoginResponse resp = authService.updateNickname("u_1", "新昵称");

        assertEquals("new-jwt", resp.token());
        assertEquals("新昵称", resp.nickname());
        assertEquals("parent1", resp.username());
        ArgumentCaptor<AuthUser> userCap = ArgumentCaptor.forClass(AuthUser.class);
        verify(jwtTokenService).issueToken(userCap.capture());
        assertEquals("新昵称", userCap.getValue().nickname());
    }

    @Test
    void changePasswordRequiresCurrentPassword() throws Exception {
        stubUserRow("u_1", "parent1", "old-hash", "昵称", "parent", "ACTIVE");
        when(passwordEncoder.matches("wrong", "old-hash")).thenReturn(false);

        KidoraException ex = assertThrows(KidoraException.class,
                () -> authService.changePassword("u_1", "wrong", "secret99"));
        assertEquals(ErrorCode.UNAUTHORIZED, ex.getErrorCode());
        verify(jdbcTemplate, never()).update(anyString(), any(), any(), any());
    }

    @Test
    void changePasswordUpdatesHash() throws Exception {
        stubUserRow("u_1", "parent1", "old-hash", "昵称", "parent", "ACTIVE");
        when(passwordEncoder.matches("oldpass1", "old-hash")).thenReturn(true);
        when(passwordEncoder.encode("secret99")).thenReturn("new-hash");
        when(jdbcTemplate.update(anyString(), eq("new-hash"), any(), eq("u_1"))).thenReturn(1);

        authService.changePassword("u_1", "oldpass1", "secret99");

        verify(jdbcTemplate).update(anyString(), eq("new-hash"), any(), eq("u_1"));
    }

    @SuppressWarnings("unchecked")
    private void stubUserRow(String userId, String username, String hash,
                             String nickname, String role, String status) throws Exception {
        java.sql.ResultSet rs = org.mockito.Mockito.mock(java.sql.ResultSet.class);
        when(rs.getString("user_id")).thenReturn(userId);
        when(rs.getString("username")).thenReturn(username);
        when(rs.getString("password_hash")).thenReturn(hash);
        when(rs.getString("nickname")).thenReturn(nickname);
        when(rs.getString("role")).thenReturn(role);
        when(rs.getString("status")).thenReturn(status);
        when(jdbcTemplate.query(anyString(), any(org.springframework.jdbc.core.RowMapper.class), eq(userId)))
                .thenAnswer(inv -> {
                    org.springframework.jdbc.core.RowMapper<Object> mapper = inv.getArgument(1);
                    return List.of(mapper.mapRow(rs, 0));
                });
    }
}

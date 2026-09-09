package com.wuji.kidora.ai.agent.server.auth;

import com.wuji.kidora.ai.common.auth.AuthUser;
import com.wuji.kidora.ai.common.auth.JwtTokenService;
import com.wuji.kidora.ai.common.exception.ErrorCode;
import com.wuji.kidora.ai.common.exception.KidoraException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * AuthService.
 *
 * @author liudy
 */
@Service
public class AuthService {

    private static final RowMapper<UserRow> MAPPER = (rs, rowNum) -> new UserRow(
            rs.getString("user_id"),
            rs.getString("username"),
            rs.getString("password_hash"),
            rs.getString("nickname"),
            rs.getString("role"),
            rs.getString("status")
    );

    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;

    public AuthService(JdbcTemplate jdbcTemplate, PasswordEncoder passwordEncoder, JwtTokenService jwtTokenService) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
    }

    public LoginResponse login(LoginRequest request) {
        if (request == null || !StringUtils.hasText(request.username()) || !StringUtils.hasText(request.password())) {
            throw new KidoraException(ErrorCode.BAD_REQUEST, "用户名或密码不能为空");
        }
        List<UserRow> rows = jdbcTemplate.query("""
                SELECT user_id, username, password_hash, nickname, role, status
                FROM app_user WHERE username = ? AND deleted = FALSE
                """, MAPPER, request.username().trim());
        if (rows.isEmpty()) {
            throw new KidoraException(ErrorCode.UNAUTHORIZED, "用户名或密码错误");
        }
        UserRow row = rows.get(0);
        if (!"ACTIVE".equalsIgnoreCase(row.status())) {
            throw new KidoraException(ErrorCode.FORBIDDEN, "账号已禁用");
        }
        if (!passwordEncoder.matches(request.password(), row.passwordHash())) {
            throw new KidoraException(ErrorCode.UNAUTHORIZED, "用户名或密码错误");
        }
        AuthUser user = new AuthUser(row.userId(), row.username(), row.nickname(), row.role());
        String token = jwtTokenService.issueToken(user);
        return new LoginResponse(token, "Bearer", user.userId(), user.username(), user.nickname(), user.role());
    }

    private record UserRow(
            String userId,
            String username,
            String passwordHash,
            String nickname,
            String role,
            String status
    ) {
    }
}

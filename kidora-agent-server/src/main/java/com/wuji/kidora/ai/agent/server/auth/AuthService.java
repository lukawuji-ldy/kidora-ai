package com.wuji.kidora.ai.agent.server.auth;

import com.wuji.kidora.ai.common.auth.AuthUser;
import com.wuji.kidora.ai.common.auth.JwtTokenService;
import com.wuji.kidora.ai.common.exception.ErrorCode;
import com.wuji.kidora.ai.common.exception.KidoraException;
import com.wuji.kidora.ai.common.util.IdGenerator;
import com.wuji.kidora.ai.memory.repo.LearnerProfileRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

/**
 * AuthService：登录、注册与个人资料（昵称 / 改密）。
 *
 * @author liudy
 */
@Service
public class AuthService {

    private static final int USERNAME_MIN = 3;
    private static final int USERNAME_MAX = 64;
    private static final int PASSWORD_MIN = 6;
    private static final int NICKNAME_MAX = 32;
    private static final String DEFAULT_PERSONA = "emma";

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
    private final LearnerProfileRepository learnerProfileRepository;

    public AuthService(JdbcTemplate jdbcTemplate,
                       PasswordEncoder passwordEncoder,
                       JwtTokenService jwtTokenService,
                       LearnerProfileRepository learnerProfileRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
        this.learnerProfileRepository = learnerProfileRepository;
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

    /**
     * 注册家长账号并创建首个儿童档案，同事务；成功后发 JWT。
     *
     * @param request 注册请求
     * @return 含 learnerId 的登录响应
     * @author liudy
     */
    @Transactional
    public LoginResponse register(RegisterRequest request) {
        if (request == null) {
            throw new KidoraException(ErrorCode.BAD_REQUEST, "请求不能为空");
        }
        String username = request.username() == null ? "" : request.username().trim();
        String password = request.password() == null ? "" : request.password();
        String childNickname = request.childNickname() == null ? "" : request.childNickname().trim();
        if (username.length() < USERNAME_MIN || username.length() > USERNAME_MAX) {
            throw new KidoraException(ErrorCode.BAD_REQUEST, "用户名长度须为 3～64");
        }
        if (password.length() < PASSWORD_MIN) {
            throw new KidoraException(ErrorCode.BAD_REQUEST, "密码至少 6 位");
        }
        if (!StringUtils.hasText(childNickname) || childNickname.length() > NICKNAME_MAX) {
            throw new KidoraException(ErrorCode.BAD_REQUEST, "儿童昵称长度须为 1～32");
        }
        String cefr = EnglishLevelMapper.toCefr(request.englishLevel());

        Integer exists = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM app_user WHERE username = ? AND deleted = FALSE",
                Integer.class, username);
        if (exists != null && exists > 0) {
            throw new KidoraException(ErrorCode.BAD_REQUEST, "用户名已被占用");
        }

        String userId = IdGenerator.nextBizId("u_");
        String learnerId = IdGenerator.nextBizId("lrn_");
        Timestamp now = Timestamp.from(Instant.now());
        String hash = passwordEncoder.encode(password);

        jdbcTemplate.update("""
                INSERT INTO app_user (id, user_id, username, password_hash, nickname, role, status, deleted,
                                      create_time, update_time)
                VALUES (?, ?, ?, ?, ?, 'parent', 'ACTIVE', FALSE, ?, ?)
                """, IdGenerator.nextLong(), userId, username, hash, username, now, now);

        learnerProfileRepository.insert(
                learnerId, userId, childNickname, null, cefr, DEFAULT_PERSONA);

        AuthUser user = new AuthUser(userId, username, username, "parent");
        String token = jwtTokenService.issueToken(user);
        return new LoginResponse(token, "Bearer", userId, username, username, "parent", learnerId);
    }

    /**
     * 读取当前家长资料。
     *
     * @param userId JWT 中的 userId
     * @return 资料（无密码）
     * @author liudy
     */
    public ProfileResponse getProfile(String userId) {
        UserRow row = requireActiveUser(userId);
        return new ProfileResponse(row.userId(), row.username(), row.nickname(), row.role());
    }

    /**
     * 更新展示昵称并重发 JWT。
     *
     * @param userId   当前用户
     * @param nickname 新昵称
     * @return 含新 token 的登录形响应
     * @author liudy
     */
    public LoginResponse updateNickname(String userId, String nickname) {
        String trimmed = nickname == null ? "" : nickname.trim();
        if (!StringUtils.hasText(trimmed) || trimmed.length() > NICKNAME_MAX) {
            throw new KidoraException(ErrorCode.BAD_REQUEST, "昵称长度须为 1～32");
        }
        UserRow row = requireActiveUser(userId);
        Timestamp now = Timestamp.from(Instant.now());
        int n = jdbcTemplate.update("""
                UPDATE app_user SET nickname = ?, update_time = ?
                WHERE user_id = ? AND deleted = FALSE
                """, trimmed, now, userId.trim());
        if (n == 0) {
            throw new KidoraException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        AuthUser user = new AuthUser(row.userId(), row.username(), trimmed, row.role());
        String token = jwtTokenService.issueToken(user);
        return new LoginResponse(token, "Bearer", user.userId(), user.username(), user.nickname(), user.role());
    }

    /**
     * 校验当前密码后更新密码哈希。
     *
     * @param userId          当前用户
     * @param currentPassword 当前密码
     * @param newPassword     新密码
     * @author liudy
     */
    public void changePassword(String userId, String currentPassword, String newPassword) {
        if (!StringUtils.hasText(currentPassword)) {
            throw new KidoraException(ErrorCode.BAD_REQUEST, "当前密码不能为空");
        }
        String next = newPassword == null ? "" : newPassword;
        if (next.length() < PASSWORD_MIN) {
            throw new KidoraException(ErrorCode.BAD_REQUEST, "新密码至少 6 位");
        }
        UserRow row = requireActiveUser(userId);
        if (!passwordEncoder.matches(currentPassword, row.passwordHash())) {
            throw new KidoraException(ErrorCode.UNAUTHORIZED, "当前密码不正确");
        }
        String hash = passwordEncoder.encode(next);
        Timestamp now = Timestamp.from(Instant.now());
        int n = jdbcTemplate.update("""
                UPDATE app_user SET password_hash = ?, update_time = ?
                WHERE user_id = ? AND deleted = FALSE
                """, hash, now, userId.trim());
        if (n == 0) {
            throw new KidoraException(ErrorCode.NOT_FOUND, "用户不存在");
        }
    }

    private UserRow requireActiveUser(String userId) {
        if (!StringUtils.hasText(userId)) {
            throw new KidoraException(ErrorCode.UNAUTHORIZED);
        }
        List<UserRow> rows = jdbcTemplate.query("""
                SELECT user_id, username, password_hash, nickname, role, status
                FROM app_user WHERE user_id = ? AND deleted = FALSE
                """, MAPPER, userId.trim());
        if (rows.isEmpty()) {
            throw new KidoraException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        UserRow row = rows.get(0);
        if (!"ACTIVE".equalsIgnoreCase(row.status())) {
            throw new KidoraException(ErrorCode.FORBIDDEN, "账号已禁用");
        }
        return row;
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

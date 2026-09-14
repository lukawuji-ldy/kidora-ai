package com.wuji.kidora.ai.cet.core.repo;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * CET 教具道具本地资源库只读仓储。
 *
 * @author liudy
 */
@Repository
public class CetPropAssetRepository {

    private static final String SELECT_COLUMNS = """
            SELECT id, lemma, aliases_json::text, theme, storage_path, content_type,
                   byte_size, status, checksum_sha256
            """;

    private final JdbcTemplate jdbcTemplate;

    public CetPropAssetRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 按 lemma 查询 ACTIVE 资产。
     *
     * @param lemma 规范词干
     * @return 行；无则 empty
     */
    public Optional<PropAssetRow> findActiveByLemma(String lemma) {
        if (!StringUtils.hasText(lemma)) {
            return Optional.empty();
        }
        String key = lemma.trim().toLowerCase(Locale.ROOT);
        List<PropAssetRow> rows = jdbcTemplate.query(SELECT_COLUMNS + """
                FROM cet_prop_asset
                WHERE lemma = ? AND status = 'ACTIVE'
                """, (rs, i) -> mapRow(rs), key);
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(rows.get(0));
    }

    /**
     * 全量 ACTIVE 资产，供 {@code PropVocabulary} 构建别名索引。
     *
     * @return 行列表（按 lemma 排序）
     */
    public List<PropAssetRow> listActive() {
        return jdbcTemplate.query(SELECT_COLUMNS + """
                FROM cet_prop_asset
                WHERE status = 'ACTIVE'
                ORDER BY lemma
                """, (rs, i) -> mapRow(rs));
    }

    /**
     * 需要署名的 ACTIVE 资产（CC-BY 系列），供 credits 页渲染。
     *
     * @return 署名行
     */
    public List<PropCreditRow> listAttributionRequired() {
        return jdbcTemplate.query("""
                SELECT lemma, theme, source_code, license_code, license_url, author, source_url
                FROM cet_prop_asset
                WHERE status = 'ACTIVE' AND attribution_required = TRUE
                ORDER BY source_code, lemma
                """, (rs, i) -> new PropCreditRow(
                rs.getString("lemma"),
                rs.getString("theme"),
                rs.getString("source_code"),
                rs.getString("license_code"),
                rs.getString("license_url"),
                rs.getString("author"),
                rs.getString("source_url")));
    }

    /**
     * 批量检索 ACTIVE 资产：lemma 命中或 aliases_json 含任一 token。
     *
     * @param tokens 已小写的 lemma/别名/hint 集合
     * @return 命中行（去重按 lemma）；空输入返回空列表
     */
    public List<PropAssetRow> findActiveMatching(Collection<String> tokens) {
        Set<String> keys = normalizeTokens(tokens);
        if (keys.isEmpty()) {
            return List.of();
        }
        String[] arr = keys.toArray(String[]::new);
        return jdbcTemplate.query(connection -> {
            Array sqlArray = connection.createArrayOf("varchar", arr);
            var ps = connection.prepareStatement(SELECT_COLUMNS + """
                    FROM cet_prop_asset
                    WHERE status = 'ACTIVE'
                      AND (
                        lemma = ANY(?)
                        OR EXISTS (
                          SELECT 1
                          FROM jsonb_array_elements_text(COALESCE(aliases_json, '[]'::jsonb)) al
                          WHERE lower(al) = ANY(?)
                        )
                      )
                    """);
            ps.setArray(1, sqlArray);
            ps.setArray(2, sqlArray);
            return ps;
        }, (rs, i) -> mapRow(rs));
    }

    private static Set<String> normalizeTokens(Collection<String> tokens) {
        Set<String> keys = new LinkedHashSet<>();
        if (tokens == null) {
            return keys;
        }
        for (String t : tokens) {
            if (!StringUtils.hasText(t)) {
                continue;
            }
            keys.add(t.trim().toLowerCase(Locale.ROOT));
        }
        return keys;
    }

    private static PropAssetRow mapRow(ResultSet rs) throws SQLException {
        return new PropAssetRow(
                rs.getLong("id"),
                rs.getString("lemma"),
                rs.getString("aliases_json"),
                rs.getString("theme"),
                rs.getString("storage_path"),
                rs.getString("content_type"),
                rs.getLong("byte_size"),
                rs.getString("status"),
                rs.getString("checksum_sha256")
        );
    }

    /**
     * 道具资产行。
     *
     * @param id             主键
     * @param lemma          词干
     * @param aliasesJson    别名 JSON 文本
     * @param theme          主题
     * @param storagePath    相对路径
     * @param contentType    MIME
     * @param byteSize       字节
     * @param status         状态
     * @param checksumSha256 文件 SHA-256（可空；用于 ETag）
     * @author liudy
     */
    public record PropAssetRow(long id, String lemma, String aliasesJson, String theme,
                               String storagePath, String contentType, long byteSize, String status,
                               String checksumSha256) {
    }

    /**
     * 署名信息行。
     *
     * @param lemma       词干
     * @param theme       主题
     * @param sourceCode  来源站点
     * @param licenseCode 许可协议
     * @param licenseUrl  许可协议链接
     * @param author      原作者
     * @param sourceUrl   原始素材链接
     * @author liudy
     */
    public record PropCreditRow(String lemma, String theme, String sourceCode, String licenseCode,
                                String licenseUrl, String author, String sourceUrl) {
    }
}

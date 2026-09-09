package com.wuji.kidora.ai.cet.core.repo;

import com.wuji.kidora.ai.common.util.IdGenerator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * CetSessionReportRepository.
 *
 * @author liudy
 */
@Repository
public class CetSessionReportRepository {

    public record ReportRow(String reportId, String childSummary, String parentSummary, String reportJson) {
    }

    private static final RowMapper<ReportRow> MAPPER = (rs, i) -> new ReportRow(
            rs.getString("report_id"),
            rs.getString("child_summary"),
            rs.getString("parent_summary"),
            rs.getString("report_json")
    );

    private final JdbcTemplate jdbcTemplate;

    public CetSessionReportRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public String upsert(String lessonSessionId, String learnerId, String childSummary, String reportJson) {
        Optional<ReportRow> existing = findBySession(lessonSessionId);
        if (existing.isPresent()) {
            jdbcTemplate.update("""
                    UPDATE cet_session_report SET child_summary = ?, report_json = ?::jsonb
                    WHERE lesson_session_id = ?
                    """, childSummary, reportJson, lessonSessionId);
            return existing.get().reportId();
        }
        String reportId = IdGenerator.nextBizId("rpt_");
        jdbcTemplate.update("""
                INSERT INTO cet_session_report
                (id, report_id, lesson_session_id, learner_id, child_summary, parent_summary, report_json, create_time)
                VALUES (?, ?, ?, ?, ?, NULL, ?::jsonb, ?)
                """,
                IdGenerator.nextLong(), reportId, lessonSessionId, learnerId, childSummary, reportJson,
                Timestamp.from(Instant.now()));
        return reportId;
    }

    public Optional<ReportRow> findBySession(String lessonSessionId) {
        List<ReportRow> rows = jdbcTemplate.query("""
                SELECT report_id, child_summary, parent_summary, report_json::text AS report_json
                FROM cet_session_report WHERE lesson_session_id = ?
                """, MAPPER, lessonSessionId);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }
}

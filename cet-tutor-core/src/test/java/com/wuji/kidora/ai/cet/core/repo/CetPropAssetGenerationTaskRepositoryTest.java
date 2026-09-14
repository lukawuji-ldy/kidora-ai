package com.wuji.kidora.ai.cet.core.repo;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 道具缺失任务写入测试。
 *
 * @author liudy
 */
class CetPropAssetGenerationTaskRepositoryTest {

    @Test
    void enqueueMissing_deduplicatesLemmasBeforeInsert() {
        JdbcTemplate jdbcTemplate = org.mockito.Mockito.mock(JdbcTemplate.class);
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);

        CetPropAssetGenerationTaskRepository repository =
                new CetPropAssetGenerationTaskRepository(jdbcTemplate);

        repository.enqueueMissing(
                List.of("rabbit", "Rabbit", "cat"),
                "pets",
                "plan_1",
                "lesson_1");

        verify(jdbcTemplate, times(2)).update(anyString(), any(Object[].class));
    }
}

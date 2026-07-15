package com.nantonijevic.habits.repository;

import com.nantonijevic.habits.domain.Habit;
import org.apache.ibatis.type.JdbcType;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.MyBatisSystemException;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.DayOfWeek;
import java.util.EnumSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@MybatisTest
class DayOfWeekSetTypeHandlerIntegrationTest {

    @Autowired
    private HabitMapper habitMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void mapsPersistedScheduledDays() {
        jdbcTemplate.update("""
            INSERT INTO habits (
                id,
                name,
                scheduled_days,
                created_at
            )
            VALUES (?, ?, ?, CURRENT_TIMESTAMP)
            """, 501L, "Workout", "TUESDAY,THURSDAY");

        Habit habit = habitMapper.findById(501L);

        assertThat(habit.getScheduledDays())
            .containsExactly(
                DayOfWeek.TUESDAY,
                DayOfWeek.THURSDAY
            );
    }

    @Test
    void rejectsEmptyPersistedScheduledDays() {
        jdbcTemplate.update("""
        INSERT INTO habits (
            id,
            name,
            scheduled_days,
            created_at
        )
        VALUES (?, ?, ?, CURRENT_TIMESTAMP)
        """, 502L, "Invalid habit", "");

        assertThatThrownBy(() -> habitMapper.findById(502L))
            .isInstanceOf(MyBatisSystemException.class)
            .hasRootCauseInstanceOf(
                InvalidScheduledDaysPersistenceException.class
            )
            .hasRootCauseMessage(
                "Invalid persisted scheduled_days value: <empty>"
            );
    }

    @Test
    void rejectsEmptyScheduledDaysBeforeWritingToDatabase() {
        DayOfWeekSetTypeHandler handler =
            new DayOfWeekSetTypeHandler();

        PreparedStatement preparedStatement =
            mock(PreparedStatement.class);

        EnumSet<DayOfWeek> emptySchedule =
            EnumSet.noneOf(DayOfWeek.class);

        assertThatThrownBy(
            () -> handler.setNonNullParameter(
                preparedStatement,
                1,
                emptySchedule,
                JdbcType.VARCHAR
            )
        )
            .isInstanceOf(
                InvalidScheduledDaysPersistenceException.class
            )
            .hasMessage(
                "Cannot persist an empty scheduled-days set"
            );

        verifyNoInteractions(preparedStatement);
    }

    @Test
    void rejectsNullScheduledDaysReadFromDatabase()
        throws SQLException {
        DayOfWeekSetTypeHandler handler =
            new DayOfWeekSetTypeHandler();

        ResultSet resultSet = mock(ResultSet.class);

        when(resultSet.getString("scheduled_days"))
            .thenReturn(null);

        assertThatThrownBy(
            () -> handler.getNullableResult(
                resultSet,
                "scheduled_days"
            )
        )
            .isInstanceOf(
                InvalidScheduledDaysPersistenceException.class
            )
            .hasMessage(
                "Invalid persisted scheduled_days value: <null>"
            );
    }
}

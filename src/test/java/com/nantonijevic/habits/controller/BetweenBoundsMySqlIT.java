package com.nantonijevic.habits.controller;

import com.nantonijevic.habits.AbstractIntegrationTest;
import com.nantonijevic.habits.domain.Habit;
import com.nantonijevic.habits.domain.HabitCompletion;
import com.nantonijevic.habits.domain.HabitSkip;
import com.nantonijevic.habits.repository.HabitCompletionRepository;
import com.nantonijevic.habits.repository.HabitSkipRepository;
import com.nantonijevic.habits.support.HabitTestFixtureRepository;
import com.nantonijevic.habits.support.InternalApiClientFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

@Testcontainers
@TestPropertySource(properties = {
    "spring.kafka.listener.auto-startup=false",
    "spring.cache.type=none"
})
@Import(InternalApiClientFixture.class)
@Transactional
class BetweenBoundsMySqlIT extends AbstractIntegrationTest {

    private static final LocalDate D =
        LocalDate.of(2026, 10, 24);

    private static final Instant CREATED_AT =
        Instant.parse("2026-10-19T00:00:00Z");

    @Container
    static final MySQLContainer<?> MYSQL =
        new MySQLContainer<>("mysql:8.0.36")
            .withDatabaseName("habits")
            .withUsername("habits")
            .withPassword("habits");

    @DynamicPropertySource
    static void configureMySql(
        DynamicPropertyRegistry registry
    ) {
        registry.add(
            "spring.datasource.url",
            MYSQL::getJdbcUrl
        );
        registry.add(
            "spring.datasource.username",
            MYSQL::getUsername
        );
        registry.add(
            "spring.datasource.password",
            MYSQL::getPassword
        );
        registry.add(
            "spring.datasource.driver-class-name",
            MYSQL::getDriverClassName
        );
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private InternalApiClientFixture apiClientFixture;

    @Autowired
    private HabitTestFixtureRepository habitFixtureRepository;

    @Autowired
    private HabitSkipRepository skipRepository;

    @Autowired
    private HabitCompletionRepository completionRepository;

    private Long habitA;
    private Long habitB;
    private Long excludedHabit;

    @BeforeEach
    void verifyBackendAndCreateHabits() {
        assertThat(mysqlVersion())
            .as("every boundary case must execute on MySQL 8.0.36")
            .startsWith("8.0.36");

        Long ownerId = apiClientFixture
            .provisionInternalClientWithIdentity()
            .clientId();
        habitA = saveHabit(ownerId, "A: requested habit");
        habitB = saveHabit(ownerId, "B: second batch habit");
        excludedHabit = saveHabit(ownerId, "C: excluded habit");
    }

    @Test
    void runsOnMySql8036WithMonthlySkipUniqueConstraint() {
        String version = mysqlVersion();
        assertThat(version).startsWith("8.0.36");
        assertThat(jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM information_schema.TABLE_CONSTRAINTS
            WHERE CONSTRAINT_SCHEMA = DATABASE()
              AND TABLE_NAME = 'habit_skips'
              AND CONSTRAINT_NAME = 'uq_habit_skips_habit_month'
              AND CONSTRAINT_TYPE = 'UNIQUE'
            """,
            Integer.class
        )).isEqualTo(1);
    }

    // Each invocation rolls back: one skip per habit/month,
    // including both selected habits and a same-date excluded control.
    @ParameterizedTest(name = "equal bounds D..D, skip offset {0}")
    @ValueSource(ints = {-1, 0, 1})
    void skipEqualBoundsIncludeOnlyD(int offset) {
        LocalDate date = seedSkips(offset);

        List<HabitSkip> single = skipRepository
            .findByHabitIdAndSkippedOnBetween(habitA, D, D);
        List<HabitSkip> batch = skipRepository
            .findByHabitIdInAndSkippedOnBetween(
                List.of(habitA, habitB), D, D
            );

        if (offset == 0) {
            assertThat(single)
                .as("single habit includes the named A0 row")
                .extracting(HabitSkip::habitId, HabitSkip::skippedOn)
                .containsExactly(tuple(habitA, date));
            assertThat(batch)
                .as("batch includes A0/B0 and excludes C0")
                .extracting(HabitSkip::habitId, HabitSkip::skippedOn)
                .containsExactlyInAnyOrder(
                    tuple(habitA, date), tuple(habitB, date)
                );
        } else {
            assertThat(single).as("single excludes D%+d", offset)
                .isEmpty();
            assertThat(batch).as("batch excludes D%+d", offset)
                .isEmpty();
        }
    }

    @ParameterizedTest(name = "inclusive D-1..D+1, skip offset {0}")
    @ValueSource(ints = {-1, 0, 1})
    void skipWideBoundsIncludeBothEndpoints(int offset) {
        LocalDate date = seedSkips(offset);

        assertThat(skipRepository
            .findByHabitIdAndSkippedOnBetween(
                habitA, D.minusDays(1), D.plusDays(1)
            ))
            .as("single includes named A row at offset %d", offset)
            .extracting(HabitSkip::habitId, HabitSkip::skippedOn)
            .containsExactly(tuple(habitA, date));

        assertThat(skipRepository
            .findByHabitIdInAndSkippedOnBetween(
                List.of(habitA, habitB),
                D.minusDays(1), D.plusDays(1)
            ))
            .as("batch includes A/B at offset %d and excludes C", offset)
            .extracting(HabitSkip::habitId, HabitSkip::skippedOn)
            .containsExactlyInAnyOrder(
                tuple(habitA, date), tuple(habitB, date)
            );
    }

    @ParameterizedTest(name = "reversed D+1..D-1, skip offset {0}")
    @ValueSource(ints = {-1, 0, 1})
    void skipReversedBoundsReturnEmptyWithoutException(int offset) {
        seedSkips(offset);

        assertThat(skipRepository
            .findByHabitIdAndSkippedOnBetween(
                habitA, D.plusDays(1), D.minusDays(1)
            )).isEmpty();
        assertThat(skipRepository
            .findByHabitIdInAndSkippedOnBetween(
                List.of(habitA, habitB),
                D.plusDays(1), D.minusDays(1)
            )).isEmpty();
    }

    @Test
    void completionEqualBoundsIncludeOnlyNamedA0Row() {
        seedCompletions();

        assertThat(completionRepository
            .findByHabitIdAndCompletedOnBetweenOrderByCompletedOnAsc(
                habitA, D, D
            ))
            .extracting(
                HabitCompletion::getHabitId,
                HabitCompletion::getCompletedOn
            )
            .containsExactly(tuple(habitA, D));
    }

    @Test
    void completionWideBoundsIncludeBothEndpoints() {
        seedCompletions();

        assertThat(completionRepository
            .findByHabitIdAndCompletedOnBetweenOrderByCompletedOnAsc(
                habitA, D.minusDays(1), D.plusDays(1)
            ))
            .as("A- and A+ must survive; same-date B/C rows must not")
            .extracting(
                HabitCompletion::getHabitId,
                HabitCompletion::getCompletedOn
            )
            .containsExactlyInAnyOrder(
                tuple(habitA, D.minusDays(1)),
                tuple(habitA, D),
                tuple(habitA, D.plusDays(1))
            );
    }

    @Test
    void completionReversedBoundsReturnEmptyWithoutException() {
        seedCompletions();

        assertThat(completionRepository
            .findByHabitIdAndCompletedOnBetweenOrderByCompletedOnAsc(
                habitA, D.plusDays(1), D.minusDays(1)
            )).isEmpty();
    }

    // OrderByCompletedOnAsc supplies the query's ordering guarantee.
    // This test records observed date order; the (habit_id, completed_on)
    // index can yield it even without ORDER BY.
    @Test
    void completionRowsComeBackInDateOrder() {
        seedCompletions();

        assertThat(completionRepository
            .findByHabitIdAndCompletedOnBetweenOrderByCompletedOnAsc(
                habitA, D.minusDays(1), D.plusDays(1)
            ))
            .extracting(HabitCompletion::getCompletedOn)
            .containsExactly(D.minusDays(1), D, D.plusDays(1));
    }

    private String mysqlVersion() {
        return jdbcTemplate.queryForObject("SELECT VERSION()", String.class);
    }

    private Long saveHabit(Long ownerId, String name) {
        return habitFixtureRepository
            .save(new Habit(ownerId, name, CREATED_AT))
            .getId();
    }

    private LocalDate seedSkips(int offset) {
        LocalDate date = D.plusDays(offset);
        skipRepository.saveAllAndFlush(List.of(
            new HabitSkip(excludedHabit, date),
            new HabitSkip(habitB, date),
            new HabitSkip(habitA, date)
        ));
        return date;
    }

    private void seedCompletions() {
        for (Long habitId : List.of(excludedHabit, habitB, habitA)) {
            completionRepository.saveAllAndFlush(List.of(
                new HabitCompletion(habitId, D.plusDays(1)),
                new HabitCompletion(habitId, D.minusDays(1)),
                new HabitCompletion(habitId, D)
            ));
        }
    }
}

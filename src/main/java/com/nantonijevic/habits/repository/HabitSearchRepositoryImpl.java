package com.nantonijevic.habits.repository;

import com.nantonijevic.habits.domain.Habit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Repository
public class HabitSearchRepositoryImpl implements HabitSearchRepository {

    private static final Map<String, String> SORT_COLUMNS = Map.of(
        "id", "id",
        "version", "version",
        "name", "name",
        "completionCount", "completion_count",
        "archived", "archived",
        "longestStreak", "longest_streak",
        "currentStreak", "current_streak",
        "lastCompletedAt", "last_completed_at",
        "createdAt", "created_at"
    );

    private final HabitMapper habitMapper;

    public HabitSearchRepositoryImpl(
        HabitMapper habitMapper
    ) {
        this.habitMapper = habitMapper;
    }

    @Override
    public Page<Habit> search(
        String name,
        boolean includeArchived,
        Pageable pageable
    ) {
        String orderBy = toOrderBy(pageable.getSort());

        Integer limit = pageable.isPaged()
            ? pageable.getPageSize()
            : null;

        Long offset = pageable.isPaged()
            ? pageable.getOffset()
            : null;

        List<Habit> content = habitMapper.search(
            name,
            includeArchived,
            orderBy,
            limit,
            offset
        );

        long total = habitMapper.count(name, includeArchived);

        return new PageImpl<>(content, pageable, total);
    }

    private static String toOrderBy(Sort sort) {
        if (sort.isUnsorted()) {
            return "created_at DESC, id DESC";
        }

        return sort.stream()
            .map(order -> {
                String column = SORT_COLUMNS.get(order.getProperty());

                if (column == null) {
                    throw new IllegalArgumentException(
                        "Unsupported sort property: " + order.getProperty()
                    );
                }

                String direction = order.isAscending() ? "ASC" : "DESC";
                return column + " " + direction;
            })
            .collect(Collectors.joining(", "));
    }
}

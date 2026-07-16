package com.nantonijevic.habits.repository;

import com.nantonijevic.habits.domain.Habit;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface HabitMapper {

    Habit findById(Long id);

    boolean existsById(Long id);

    List<Habit> findActive();

    int insert(Habit habit);

    int update(Habit habit);

    List<Habit> search(
            @Param("name") String name,
            @Param("includeArchived") boolean includeArchived,
            @Param("orderBy") String orderBy,
            @Param("limit") Integer limit,
            @Param("offset") Long offset
    );

    long count(
            @Param("name") String name,
            @Param("includeArchived") boolean includeArchived
    );

}

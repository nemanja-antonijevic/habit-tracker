package com.nantonijevic.habits.repository;

import com.nantonijevic.habits.domain.Habit;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface HabitMapper {

    Habit findById(
        @Param("ownerId") Long ownerId,
        @Param("id") Long id
    );

    boolean existsById(
        @Param("ownerId") Long ownerId,
        @Param("id") Long id
    );

    int deleteById(
        @Param("ownerId") Long ownerId,
        @Param("id") Long id
    );

    List<Habit> findActive(
        @Param("ownerId") Long ownerId
    );

    int insert(Habit habit);

    int update(Habit habit);

    List<Habit> search(
            @Param("ownerId") Long ownerId,
            @Param("name") String name,
            @Param("includeArchived") boolean includeArchived,
            @Param("orderBy") String orderBy,
            @Param("limit") Integer limit,
            @Param("offset") Long offset
    );

    long count(
            @Param("ownerId") Long ownerId,
            @Param("name") String name,
            @Param("includeArchived") boolean includeArchived
    );

}

package com.nantonijevic.habits.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.time.DayOfWeek;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.stream.Collectors;

@Converter
public class DayOfWeekSetConverter implements AttributeConverter<EnumSet<DayOfWeek>, String> {

    @Override
    public String convertToDatabaseColumn(EnumSet<DayOfWeek> attribute) {
        if (attribute == null || attribute.isEmpty()) {
            return "";
        }

        return attribute.stream()
                .map(DayOfWeek::name)
                .collect(Collectors.joining(","));
    }

    @Override
    public EnumSet<DayOfWeek> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return EnumSet.noneOf(DayOfWeek.class);
        }

        EnumSet<DayOfWeek> result = EnumSet.noneOf(DayOfWeek.class);

        Arrays.stream(dbData.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(DayOfWeek::valueOf)
                .forEach(result::add);

        return result;
    }
}
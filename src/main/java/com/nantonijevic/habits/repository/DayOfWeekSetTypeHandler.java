package com.nantonijevic.habits.repository;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.DayOfWeek;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.stream.Collectors;

public class DayOfWeekSetTypeHandler
    extends BaseTypeHandler<EnumSet<DayOfWeek>> {

    private static final int SAFE_RENDER_LIMIT = 64;

    @Override
    public void setNonNullParameter(
        PreparedStatement ps,
        int i,
        EnumSet<DayOfWeek> parameter,
        JdbcType jdbcType
    ) throws SQLException {
        if (parameter.isEmpty()) {
            throw new InvalidScheduledDaysPersistenceException(
                "Cannot persist an empty scheduled-days set"
            );
        }

        ps.setString(i, csv(parameter));
    }

    @Override
    public EnumSet<DayOfWeek> getNullableResult(
        ResultSet rs,
        String columnName
    ) throws SQLException {
        return parse(rs.getString(columnName));
    }

    @Override
    public EnumSet<DayOfWeek> getNullableResult(
        ResultSet rs,
        int columnIndex
    ) throws SQLException {
        return parse(rs.getString(columnIndex));
    }

    @Override
    public EnumSet<DayOfWeek> getNullableResult(
        CallableStatement cs,
        int columnIndex
    ) throws SQLException {
        return parse(cs.getString(columnIndex));
    }

    private String csv(EnumSet<DayOfWeek> set) {
        return set.stream()
            .map(DayOfWeek::name)
            .collect(Collectors.joining(","));
    }

    private EnumSet<DayOfWeek> parse(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            throw new InvalidScheduledDaysPersistenceException(
                "Invalid persisted scheduled_days value: "
                    + safeRender(dbData)
            );
        }

        EnumSet<DayOfWeek> result = EnumSet.noneOf(DayOfWeek.class);

        try {
            Arrays.stream(dbData.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(DayOfWeek::valueOf)
                .forEach(result::add);
        } catch (IllegalArgumentException exception) {
            throw new InvalidScheduledDaysPersistenceException(
                "Invalid persisted scheduled_days value: "
                    + safeRender(dbData)
            );
        }

        if (result.isEmpty()) {
            throw new InvalidScheduledDaysPersistenceException(
                "Invalid persisted scheduled_days value: "
                    + safeRender(dbData)
            );
        }

        return result;
    }

    private String safeRender(String raw) {
        if (raw == null) {
            return "<null>";
        }

        if (raw.isEmpty()) {
            return "<empty>";
        }

        String sanitized = raw.replaceAll("\\p{Cntrl}", "?");

        boolean wasSanitized = !sanitized.equals(raw);
        boolean wasTruncated = sanitized.length() > SAFE_RENDER_LIMIT;

        String preview = wasTruncated
            ? sanitized.substring(0, SAFE_RENDER_LIMIT) + "…"
            : sanitized;

        if (!wasSanitized && !wasTruncated) {
            return "'" + preview + "'";
        }

        String modification = wasSanitized && wasTruncated
            ? "sanitized, truncated"
            : wasSanitized ? "sanitized" : "truncated";

        return "'" + preview + "' ("
            + modification
            + ", originalLength="
            + raw.length()
            + ")";
    }
}

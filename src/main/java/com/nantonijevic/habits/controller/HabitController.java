package com.nantonijevic.habits.controller;

import com.nantonijevic.habits.domain.Habit;
import com.nantonijevic.habits.dto.BulkCompleteRequest;
import com.nantonijevic.habits.dto.BulkCompleteResponse;
import com.nantonijevic.habits.dto.CreateHabitRequest;
import com.nantonijevic.habits.dto.DueTodayCountResponse;
import com.nantonijevic.habits.dto.HabitCompletionRateResponse;
import com.nantonijevic.habits.dto.HabitCompletionResponse;
import com.nantonijevic.habits.dto.HabitDashboardResponse;
import com.nantonijevic.habits.dto.HabitResponse;
import com.nantonijevic.habits.dto.HabitStatsResponse;
import com.nantonijevic.habits.dto.HabitStatsView;
import com.nantonijevic.habits.dto.UpdateHabitRequest;
import com.nantonijevic.habits.service.HabitCommandService;
import com.nantonijevic.habits.service.HabitQueryService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.Clock;
import java.time.LocalDate;

@RestController
@RequestMapping("/habits")
public class HabitController {

    private final HabitCommandService habitCommandService;

    private final HabitQueryService habitQueryService;

    private final Clock clock;

    public HabitController(
        HabitCommandService habitCommandService,
        HabitQueryService habitQueryService,
        Clock clock
    ) {
        this.habitCommandService = habitCommandService;
        this.habitQueryService = habitQueryService;
        this.clock = clock;
    }

    @PostMapping
    public ResponseEntity<HabitResponse> create(@Valid @RequestBody CreateHabitRequest request) {
        LocalDate today = LocalDate.now(clock);
        Habit saved = habitCommandService.create(request.name(), request.scheduledDays());
        return ResponseEntity.created(URI.create("/habits/" + saved.getId()))
                .body(HabitResponse.from(
                    saved,
                    today,
                    clock.getZone()
                ));
    }

    @PostMapping("/bulk-complete")
    public BulkCompleteResponse bulkComplete(@Valid @RequestBody BulkCompleteRequest request) {
        LocalDate today = LocalDate.now(clock);

        return habitCommandService.bulkComplete(request.habitIds(), today);
    }

    @GetMapping
    public Page<HabitResponse> list(@RequestParam(defaultValue = "false") boolean includeArchived,
                                    @RequestParam(required = false) String name,
                                    Pageable pageable) {
        LocalDate today = LocalDate.now(clock);
        return habitQueryService.list(includeArchived, name, pageable)
                .map(habit -> HabitResponse.from(
                    habit,
                    today,
                    clock.getZone()
                ));
    }

    @GetMapping("/{id}")
    public HabitResponse getById(@PathVariable Long id) {
        LocalDate today = LocalDate.now(clock);
        Habit habit = habitQueryService.getById(id);
        return HabitResponse.from(
            habit,
            today,
            clock.getZone()
        );
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        habitCommandService.delete(id);
    }

    @PutMapping("/{id}")
    public HabitResponse update(@PathVariable Long id, @Valid @RequestBody UpdateHabitRequest request) {
        LocalDate today = LocalDate.now(clock);
        Habit habit = habitCommandService.update(id, request.version(), request.name(), request.scheduledDays());
        return HabitResponse.from(
            habit,
            today,
            clock.getZone()
        );
    }

    @PostMapping("/{id}/complete")
    public HabitResponse complete(@PathVariable Long id) {
        LocalDate today = LocalDate.now(clock);
        Habit habit = habitCommandService.complete(id, today);
        return HabitResponse.from(
            habit,
            today,
            clock.getZone()
        );
    }

    @PostMapping("/{id}/archive")
    public HabitResponse archive(@PathVariable Long id) {
        LocalDate today = LocalDate.now(clock);
        Habit habit = habitCommandService.archive(id);
        return HabitResponse.from(
            habit,
            today,
            clock.getZone()
        );
    }

    @PostMapping("/{id}/unarchive")
    public HabitResponse unarchive(@PathVariable Long id) {
        LocalDate today = LocalDate.now(clock);
        Habit habit = habitCommandService.unarchive(id);
        return HabitResponse.from(
            habit,
            today,
            clock.getZone()
        );
    }

    @GetMapping("/{id}/stats")
    public HabitStatsResponse getStats(@PathVariable Long id) {
        HabitStatsView view = habitQueryService.getStatsProjection(id, LocalDate.now(clock));
        return HabitStatsResponse.from(view);
    }

    @PostMapping("/{id}/uncomplete")
    public HabitResponse uncomplete(@PathVariable Long id) {
        LocalDate today = LocalDate.now(clock);
        Habit habit = habitCommandService.uncomplete(id, today);
        return HabitResponse.from(
            habit,
            today,
            clock.getZone()
        );
    }

    @GetMapping("/{id}/history")
    public Page<HabitCompletionResponse> getHistory(
            @PathVariable Long id,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            Pageable pageable) {
        return habitQueryService.getHistory(id, from, to, pageable)
                .map(HabitCompletionResponse::from);
    }

    @GetMapping("/{id}/completion-rate")
    public HabitCompletionRateResponse getCompletionRate(
        @PathVariable Long id,
        @RequestParam LocalDate from,
        @RequestParam LocalDate to) {
        return habitQueryService.getCompletionRate(
            id,
            from,
            to
        );
    }

    @GetMapping("/due-today")
    public Page<HabitResponse> dueToday(Pageable pageable) {
        LocalDate today = LocalDate.now(clock);
        return habitQueryService.dueToday(today, pageable)
                .map(habit -> HabitResponse.from(
                    habit,
                    today,
                    clock.getZone()
                ));
    }

    @GetMapping("/due-today/count")
    public DueTodayCountResponse dueTodayCount() {
        LocalDate today = LocalDate.now(clock);

        long count = habitQueryService.countDueToday(today);

        return new DueTodayCountResponse(count);
    }

    @GetMapping("/stats")
    public HabitDashboardResponse getDashboardStats() {
        return habitQueryService.getDashboardStats(LocalDate.now(clock));
    }
}

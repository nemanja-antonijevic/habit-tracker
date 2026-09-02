package com.nantonijevic.habits.controller;

import com.nantonijevic.habits.client.ClientContext;
import com.nantonijevic.habits.client.HabitResponseTransformer;
import com.nantonijevic.habits.client.ResolvedClientTier;
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

    private final HabitResponseTransformer habitResponseTransformer;

    public HabitController(
        HabitCommandService habitCommandService,
        HabitQueryService habitQueryService,
        Clock clock,
        HabitResponseTransformer habitResponseTransformer
    ) {
        this.habitCommandService = habitCommandService;
        this.habitQueryService = habitQueryService;
        this.clock = clock;
        this.habitResponseTransformer =
            habitResponseTransformer;
    }

    @PostMapping
    public ResponseEntity<HabitResponse> create(
        @Valid @RequestBody CreateHabitRequest request,
        @ResolvedClientTier ClientContext context
    ) {
        LocalDate today = LocalDate.now(clock);

        Habit saved = habitCommandService.create(
            context.clientId(),
            request.name(),
            request.scheduledDays()
        );

        HabitResponse response =
            habitResponseTransformer.transform(
                HabitResponse.from(
                    saved,
                    today,
                    clock.getZone()
                ),
                context.tier()
            );

        return ResponseEntity
            .created(
                URI.create("/habits/" + saved.getId())
            )
            .body(response);
    }

    @PostMapping("/bulk-complete")
    public BulkCompleteResponse bulkComplete(
        @Valid @RequestBody BulkCompleteRequest request,
        @ResolvedClientTier ClientContext context
    ) {
        LocalDate today = LocalDate.now(clock);

        return habitCommandService.bulkComplete(
            context.clientId(),
            request.habitIds(),
            today
        );
    }

    @GetMapping
    public Page<HabitResponse> list(
        @RequestParam(defaultValue = "false")
        boolean includeArchived,
        @RequestParam(required = false)
        String name,
        Pageable pageable,
        @ResolvedClientTier ClientContext context
    ) {
        LocalDate today = LocalDate.now(clock);

        Page<HabitResponse> response =
            habitQueryService
                .list(
                    context.clientId(),
                    includeArchived,
                    name,
                    pageable
                )
                .map(habit ->
                    HabitResponse.from(
                        habit,
                        today,
                        clock.getZone()
                    )
                );

        return habitResponseTransformer.transform(
            response,
            context.tier()
        );
    }

    @GetMapping("/{id}")
    public HabitResponse getById(
        @PathVariable Long id,
        @ResolvedClientTier ClientContext context
    ) {
        LocalDate today = LocalDate.now(clock);
        Habit habit = habitQueryService.getById(context.clientId(), id);

        return habitResponseTransformer.transform(
            HabitResponse.from(
                habit,
                today,
                clock.getZone()
            ),
            context.tier()
        );
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
        @PathVariable Long id,
        @ResolvedClientTier ClientContext context
    ) {
        habitCommandService.delete(context.clientId(), id);
    }

    @PutMapping("/{id}")
    public HabitResponse update(
        @PathVariable Long id,
        @Valid @RequestBody UpdateHabitRequest request,
        @ResolvedClientTier ClientContext context
    ) {
        LocalDate today = LocalDate.now(clock);

        Habit habit = habitCommandService.update(
            context.clientId(),
            id,
            request.version(),
            request.name(),
            request.scheduledDays()
        );

        return habitResponseTransformer.transform(
            HabitResponse.from(
                habit,
                today,
                clock.getZone()
            ),
            context.tier()
        );
    }

    @PostMapping("/{id}/complete")
    public HabitResponse complete(
        @PathVariable Long id,
        @ResolvedClientTier ClientContext context
    ) {
        LocalDate today = LocalDate.now(clock);
        Habit habit =
            habitCommandService.complete(context.clientId(), id, today);

        return habitResponseTransformer.transform(
            HabitResponse.from(
                habit,
                today,
                clock.getZone()
            ),
            context.tier()
        );
    }

    @PostMapping("/{id}/archive")
    public HabitResponse archive(
        @PathVariable Long id,
        @ResolvedClientTier ClientContext context
    ) {
        LocalDate today = LocalDate.now(clock);
        Habit habit = habitCommandService.archive(context.clientId(), id);

        return habitResponseTransformer.transform(
            HabitResponse.from(
                habit,
                today,
                clock.getZone()
            ),
            context.tier()
        );
    }

    @PostMapping("/{id}/unarchive")
    public HabitResponse unarchive(
        @PathVariable Long id,
        @ResolvedClientTier ClientContext context
    ) {
        LocalDate today = LocalDate.now(clock);
        Habit habit = habitCommandService.unarchive(context.clientId(), id);

        return habitResponseTransformer.transform(
            HabitResponse.from(
                habit,
                today,
                clock.getZone()
            ),
            context.tier()
        );
    }

    @GetMapping("/{id}/stats")
    public HabitStatsResponse getStats(
        @PathVariable Long id,
        @ResolvedClientTier ClientContext context
    ) {
        HabitStatsView view = habitQueryService.getStatsProjection(
            context.clientId(),
            id,
            LocalDate.now(clock)
        );
        return HabitStatsResponse.from(view);
    }

    @PostMapping("/{id}/uncomplete")
    public HabitResponse uncomplete(
        @PathVariable Long id,
        @ResolvedClientTier ClientContext context
    ) {
        LocalDate today = LocalDate.now(clock);

        Habit habit =
            habitCommandService.uncomplete(context.clientId(), id, today);

        return habitResponseTransformer.transform(
            HabitResponse.from(
                habit,
                today,
                clock.getZone()
            ),
            context.tier()
        );
    }

    @GetMapping("/{id}/history")
    public Page<HabitCompletionResponse> getHistory(
            @PathVariable Long id,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            Pageable pageable,
            @ResolvedClientTier ClientContext context) {
        return habitQueryService.getHistory(
                context.clientId(),
                id,
                from,
                to,
                pageable
            )
                .map(HabitCompletionResponse::from);
    }

    @GetMapping("/{id}/completion-rate")
    public HabitCompletionRateResponse getCompletionRate(
        @PathVariable Long id,
        @RequestParam LocalDate from,
        @RequestParam LocalDate to,
        @ResolvedClientTier ClientContext context) {
        return habitQueryService.getCompletionRate(
            context.clientId(),
            id,
            from,
            to
        );
    }

    @GetMapping("/due-today")
    public Page<HabitResponse> dueToday(
        Pageable pageable,
        @ResolvedClientTier ClientContext context
    ) {
        LocalDate today = LocalDate.now(clock);

        Page<HabitResponse> response =
            habitQueryService
                .dueToday(context.clientId(), today, pageable)
                .map(habit ->
                    HabitResponse.from(
                        habit,
                        today,
                        clock.getZone()
                    )
                );

        return habitResponseTransformer.transform(
            response,
            context.tier()
        );
    }

    @GetMapping("/due-today/count")
    public DueTodayCountResponse dueTodayCount(
        @ResolvedClientTier ClientContext context
    ) {
        LocalDate today = LocalDate.now(clock);

        long count = habitQueryService.countDueToday(
            context.clientId(),
            today
        );

        return new DueTodayCountResponse(count);
    }

    @GetMapping("/stats")
    public HabitDashboardResponse getDashboardStats(
        @ResolvedClientTier ClientContext context
    ) {
        return habitQueryService.getDashboardStats(
            context.clientId(),
            LocalDate.now(clock)
        );
    }
}

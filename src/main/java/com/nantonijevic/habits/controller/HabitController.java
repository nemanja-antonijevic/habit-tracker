package com.nantonijevic.habits.controller;

import com.nantonijevic.habits.domain.Habit;
import com.nantonijevic.habits.dto.*;
import com.nantonijevic.habits.service.HabitService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.time.LocalDate;

@RestController
@RequestMapping("/habits")
public class HabitController {

    private final HabitService habitService;

    public HabitController(HabitService habitService) {
        this.habitService = habitService;
    }

    @PostMapping
    public ResponseEntity<HabitResponse> create(@Valid @RequestBody CreateHabitRequest request) {
        LocalDate today = LocalDate.now();
        Habit saved = habitService.create(request.name(), request.scheduledDays());
        return ResponseEntity.created(URI.create("/habits/" + saved.getId()))
                .body(HabitResponse.from(saved, today));
    }

    @GetMapping
    public Page<HabitResponse> list(@RequestParam(defaultValue = "false") boolean includeArchived,
                                    @RequestParam(required = false) String name,
                                    Pageable pageable) {
        LocalDate today = LocalDate.now();
        return habitService.list(includeArchived, name, pageable)
                .map(habit -> HabitResponse.from(habit, today));
    }

    @GetMapping("/{id}")
    public HabitResponse getById(@PathVariable Long id) {
        LocalDate today = LocalDate.now();
        Habit habit = habitService.getById(id);
        return HabitResponse.from(habit, today);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        habitService.delete(id);
    }

    @PutMapping("/{id}")
    public HabitResponse update(@PathVariable Long id, @Valid @RequestBody UpdateHabitRequest request) {
        LocalDate today = LocalDate.now();
        Habit habit = habitService.update(id, request.version(), request.name(), request.scheduledDays());
        return HabitResponse.from(habit, today);
    }

    @PostMapping("/{id}/complete")
    public HabitResponse complete(@PathVariable Long id) {
        LocalDate today = LocalDate.now();
        Habit habit = habitService.complete(id, today);
        return HabitResponse.from(habit, today);
    }

    @PostMapping("/{id}/archive")
    public HabitResponse archive(@PathVariable Long id) {
        LocalDate today = LocalDate.now();
        Habit habit = habitService.archive(id);
        return HabitResponse.from(habit, today);
    }

    @PostMapping("/{id}/unarchive")
    public HabitResponse unarchive(@PathVariable Long id) {
        LocalDate today = LocalDate.now();
        Habit habit = habitService.unarchive(id);
        return HabitResponse.from(habit, today);
    }

    @GetMapping("/{id}/stats")
    public HabitStatsResponse getStats(@PathVariable Long id) {
        HabitStatsView view = habitService.getStatsProjection(id, LocalDate.now());
        return HabitStatsResponse.from(view);
    }

    @PostMapping("/{id}/uncomplete")
    public HabitResponse uncomplete(@PathVariable Long id) {
        LocalDate today = LocalDate.now();
        Habit habit = habitService.uncomplete(id, today);
        return HabitResponse.from(habit, today);
    }

    @GetMapping("/{id}/history")
    public Page<HabitCompletionResponse> getHistory(
            @PathVariable Long id,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            Pageable pageable) {
        return habitService.getHistory(id, from, to, pageable)
                .map(HabitCompletionResponse::from);
    }

    @GetMapping("/due-today")
    public Page<HabitResponse> dueToday(Pageable pageable) {
        LocalDate today = LocalDate.now();
        return habitService.dueToday(today, pageable)
                .map(habit -> HabitResponse.from(habit, today));
    }
}

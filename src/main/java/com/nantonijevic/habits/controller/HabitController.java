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
import java.util.List;

@RestController
@RequestMapping("/habits")
public class HabitController {

    private final HabitService habitService;

    public HabitController(HabitService habitService) {
        this.habitService = habitService;
    }

    @PostMapping
    public ResponseEntity<HabitResponse> create(@Valid @RequestBody CreateHabitRequest request) {
        Habit saved = habitService.create(request.name());
        return ResponseEntity.created(URI.create("/habits/" + saved.getId()))
                .body(HabitResponse.from(saved));
    }

    @GetMapping
    public Page<HabitResponse> list(Pageable pageable) {
        return habitService.list(pageable)
                .map(HabitResponse::from);
    }

    @GetMapping("/{id}")
    public HabitResponse getById(@PathVariable Long id) {
        Habit habit = habitService.getById(id);
        return HabitResponse.from(habit);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        habitService.delete(id);
    }

    @PutMapping("/{id}")
    public HabitResponse update(@PathVariable Long id, @Valid @RequestBody UpdateHabitRequest request) {
        Habit habit = habitService.update(id, request.version(), request.name());
        return HabitResponse.from(habit);
    }

    @PostMapping("/{id}/complete")
    public HabitResponse complete(@PathVariable Long id) {
        Habit habit = habitService.complete(id, LocalDate.now());
        return HabitResponse.from(habit);
    }

    @PostMapping("/{id}/archive")
    public HabitResponse archive(@PathVariable Long id) {
        Habit habit = habitService.archive(id);
        return HabitResponse.from(habit);
    }

    @PostMapping("/{id}/unarchive")
    public HabitResponse unarchive(@PathVariable Long id) {
        Habit habit = habitService.unarchive(id);
        return HabitResponse.from(habit);
    }

    @GetMapping("/{id}/stats")
    public HabitStatsResponse getStats(@PathVariable Long id) {
        HabitStatsView view = habitService.getStatsProjection(id, LocalDate.now());
        return HabitStatsResponse.from(view);
    }

    @PostMapping("/{id}/uncomplete")
    public HabitResponse uncomplete(@PathVariable Long id) {
        Habit habit = habitService.uncomplete(id);
        return HabitResponse.from(habit);
    }

    @GetMapping("/{id}/history")
    public List<HabitCompletionResponse> getHistory(@PathVariable Long id) {
        return habitService.getHistory(id)
                .stream()
                .map(HabitCompletionResponse::from)
                .toList();
    }
}

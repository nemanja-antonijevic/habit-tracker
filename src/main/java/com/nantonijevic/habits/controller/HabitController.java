package com.nantonijevic.habits.controller;

import com.nantonijevic.habits.domain.Habit;
import com.nantonijevic.habits.domain.HabitNotFoundException;
import com.nantonijevic.habits.dto.*;
import com.nantonijevic.habits.service.HabitService;
import com.nantonijevic.habits.repository.HabitCompletionRepository;
import com.nantonijevic.habits.repository.HabitRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
@RequestMapping("/habits")
public class HabitController {

    private final HabitRepository repository;
    private final HabitService habitService;
    private final HabitCompletionRepository completionRepository;

    private static final Logger logger = LoggerFactory.getLogger(HabitController.class);


    public HabitController(HabitRepository repository, HabitService habitService, HabitCompletionRepository completionRepository) {
        this.repository = repository;
        this.habitService = habitService;
        this.completionRepository = completionRepository;
    }

    @PostMapping
    public ResponseEntity<HabitResponse> create(@Valid @RequestBody CreateHabitRequest request) {
        var saved = repository.save(new Habit(request.name()));
        logger.info("Habit created, habitId: {}",
                saved.getId());
        var response = HabitResponse.from(saved);
        return ResponseEntity.created(URI.create("/habits/" + saved.getId())).body(response);
    }

    @GetMapping
    public List<HabitResponse> list() {
        return repository.findAll()
                .stream()
                .filter(habit -> !habit.isArchived())
                .map(habit -> HabitResponse.from(habit))
              .toList();
    }

    @GetMapping("/{id}")
    public HabitResponse getById(@PathVariable Long id) {
        var habit = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return HabitResponse.from(habit);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        var habit = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        repository.delete(habit);
        logger.info("Habit deleted, habitId: {}",
                habit.getId());
    }

    @PutMapping("/{id}")
    public Habit update(@PathVariable Long id, @Valid @RequestBody UpdateHabitRequest request) {
        var habit = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        if (!habit.getVersion().equals(request.version())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT);
        }

        habit.setName(request.name());
        logger.info("Habit updated, habitId: {}, version: {}",
                habit.getId(), habit.getVersion());
        return repository.save(habit);
    }

    @PostMapping("/{id}/complete")
    public HabitResponse complete(@PathVariable Long id) {
        try {
            Habit habit = habitService.complete(id,  LocalDate.now());
            return HabitResponse.from(habit);
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        }
    }

    @PostMapping("/{id}/archive")
    public HabitResponse archive(@PathVariable Long id) {
        Habit habit = habitService.archive(id);
        return HabitResponse.from(habit);
    }

    @PostMapping("/{id}/unarchive")
    public HabitResponse unarchive(@PathVariable Long id) {
        var habit = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        habit.unarchive();
        repository.save(habit);
        return HabitResponse.from(habit);
    }

    @GetMapping("/{id}/stats")
    public StatsResponse getStats(@PathVariable Long id) {
        var habit = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return StatsResponse.from(habit);
    }

    @PostMapping("/{id}/uncomplete")
    public HabitResponse uncomplete(@PathVariable Long id) {
        var habit = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        try {
            habit.decrementCompletionCount();
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
        repository.save(habit);
        return HabitResponse.from(habit);
    }

    @GetMapping("/{id}/history")
    public List<HabitCompletionResponse> getHistory(@PathVariable Long id) {
        repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return completionRepository.findByHabitIdOrderByCompletedOnDesc(id)
                .stream()
                .map(HabitCompletionResponse::from)
                .toList();
    }
}

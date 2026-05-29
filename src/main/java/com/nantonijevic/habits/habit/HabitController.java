package com.nantonijevic.habits.habit;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/habits")
public class HabitController {

    private final HabitRepository repository;

    public HabitController(HabitRepository repository) {
        this.repository = repository;
    }

    @PostMapping
    public ResponseEntity<HabitResponse> create(@Valid @RequestBody CreateHabitRequest request) {
        var saved = repository.save(new Habit(request.name()));
        var response = HabitResponse.from(saved);
        return ResponseEntity.created(URI.create("/habits/" + saved.getId())).body(response);
    }

    @GetMapping
    public List<HabitResponse> list() {
        return repository.findAll()
                .stream()
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
    }

    @PutMapping("/{id}")
    public Habit update(@PathVariable Long id, @Valid @RequestBody UpdateHabitRequest request) {
        var habit = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        if (!habit.getVersion().equals(request.version())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT);
        }

        habit.setName(request.name());
        return repository.save(habit);
    }

    @PostMapping("/{id}/complete")
    public HabitResponse complete(@PathVariable Long id) {
        var habit = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        habit.complete(LocalDate.now());
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
}
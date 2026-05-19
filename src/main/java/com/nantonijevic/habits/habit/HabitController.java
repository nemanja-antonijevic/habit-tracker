package com.nantonijevic.habits.habit;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

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
}

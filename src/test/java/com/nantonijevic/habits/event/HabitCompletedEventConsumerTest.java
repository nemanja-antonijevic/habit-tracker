package com.nantonijevic.habits.event;

import com.nantonijevic.habits.domain.HabitCompletionStat;
import com.nantonijevic.habits.repository.HabitCompletionStatRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;

@ExtendWith(MockitoExtension.class)
class HabitCompletedEventConsumerTest {

    @Mock
    private HabitCompletionStatRepository repository;

    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    @InjectMocks
    private HabitCompletedEventConsumer consumer;

    @Test
    void completedEventPublishesDashboardChangeAfterUpdatingReadModel() {
        HabitCompletedEvent event = new HabitCompletedEvent(
            42L,
            LocalDate.of(2024, 1, 5),
            3,
            7
        );

        consumer.on(event);

        InOrder inOrder = inOrder(repository, applicationEventPublisher);
        inOrder.verify(repository).save(any(HabitCompletionStat.class));
        inOrder.verify(applicationEventPublisher)
            .publishEvent(any(DashboardChangedEvent.class));
    }

    @Test
    void uncompletedEventPublishesDashboardChangeAfterUpdatingReadModel() {
        HabitUncompletedEvent event = new HabitUncompletedEvent(
            42L,
            LocalDate.of(2024, 1, 5)
        );

        consumer.on(event);

        InOrder inOrder = inOrder(repository, applicationEventPublisher);
        inOrder.verify(repository)
            .deleteByHabitIdAndCompletedOn(42L, LocalDate.of(2024, 1, 5));
        inOrder.verify(applicationEventPublisher)
            .publishEvent(any(DashboardChangedEvent.class));
    }
}

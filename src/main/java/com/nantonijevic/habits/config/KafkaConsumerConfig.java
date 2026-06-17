package com.nantonijevic.habits.config;

import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.common.serialization.StringDeserializer;
import com.nantonijevic.habits.event.HabitCompletedEvent;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;

import java.util.Map;

@Configuration
public class KafkaConsumerConfig {

    @Bean
    public ConsumerFactory<String, HabitCompletedEvent> consumerFactory(
            ObjectMapper objectMapper,
            KafkaProperties kafkaProperties) {

        Map<String, Object> props = kafkaProperties.buildConsumerProperties();

        JsonDeserializer<HabitCompletedEvent> valueDeserializer =
                new JsonDeserializer<>(HabitCompletedEvent.class, objectMapper);
        valueDeserializer.addTrustedPackages("com.nantonijevic.habits.event");

        return new DefaultKafkaConsumerFactory<>(
                props,
                new StringDeserializer(),
                valueDeserializer);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, HabitCompletedEvent> kafkaListenerContainerFactory(
            ConsumerFactory<String, HabitCompletedEvent> consumerFactory) {
        ConcurrentKafkaListenerContainerFactory<String, HabitCompletedEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        return factory;
    }
}

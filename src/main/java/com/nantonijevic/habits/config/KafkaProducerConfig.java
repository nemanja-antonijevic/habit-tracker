package com.nantonijevic.habits.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nantonijevic.habits.event.HabitEvent;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.boot.ssl.SslBundles;

import java.util.Map;

@Configuration
public class KafkaProducerConfig {

    @Bean
    public ProducerFactory<String, HabitEvent> producerFactory(
            ObjectMapper objectMapper,          // Spring ubrizga SVOJ konfigurisani mapper (ima JavaTimeModule)
            KafkaProperties kafkaProperties,    // nosi bootstrap-servers iz application.yml
            SslBundles sslBundles) {

        Map<String, Object> props = kafkaProperties.buildProducerProperties(sslBundles);

        return new DefaultKafkaProducerFactory<>(
                props,
                new StringSerializer(),                 // key serializer
                new JsonSerializer<>(objectMapper));    // value serializer — KORISTI Spring-ov mapper
    }

    @Bean
    public KafkaTemplate<String, HabitEvent> kafkaTemplate(
            ProducerFactory<String, HabitEvent> producerFactory) {
        return new KafkaTemplate<>(producerFactory);
    }
}

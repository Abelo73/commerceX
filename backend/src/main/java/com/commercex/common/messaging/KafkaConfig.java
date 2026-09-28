package com.commercex.common.messaging;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

@Configuration(proxyBeanMethods = false)
class KafkaConfig {

    @Bean
    NewTopic ordersTopic() {
        return TopicBuilder.name(Topics.ORDERS).partitions(3).build();
    }

    @Bean
    NewTopic usersTopic() {
        return TopicBuilder.name(Topics.USERS).partitions(3).build();
    }

    @Bean
    NewTopic inventoryTopic() {
        return TopicBuilder.name(Topics.INVENTORY).partitions(3).build();
    }

    /**
     * Consumer failures are retried with backoff (1 s, 2 s, 4 s); then the record goes to {@code <topic>.DLT}
     * for inspection and replay, so one poison message never blocks a partition.
     */
    @Bean
    DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> template) {
        ExponentialBackOff backOff = new ExponentialBackOff(1_000, 2.0);
        backOff.setMaxAttempts(3);
        return new DefaultErrorHandler(new DeadLetterPublishingRecoverer(template), backOff);
    }
}

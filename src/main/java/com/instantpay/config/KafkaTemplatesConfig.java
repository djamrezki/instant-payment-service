package com.instantpay.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

//@Configuration
class KafkaTemplatesConfig {
    @Bean
    @Primary
    KafkaTemplate<String, byte[]> modulithKafkaTemplate(ProducerFactory<String, byte[]> pf) {
        return new KafkaTemplate<>(pf);
    }

    // If you also need a String/String template for your own producers:
    @Bean
    KafkaTemplate<String, String> appKafkaTemplate(ProducerFactory<String, String> pf) {
        return new KafkaTemplate<>(pf);
    }
}

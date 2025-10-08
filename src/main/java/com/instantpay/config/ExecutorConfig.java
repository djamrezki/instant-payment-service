package com.instantpay.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

@Configuration
public class ExecutorConfig {

    @Bean(destroyMethod = "close")
    public Executor virtualThreadExecutor() {
        ThreadFactory factory = Thread.ofVirtual().name("vthread-", 0).factory();
        return Executors.newThreadPerTaskExecutor(factory);
    }
}

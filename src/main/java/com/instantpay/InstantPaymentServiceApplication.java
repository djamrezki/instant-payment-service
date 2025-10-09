package com.instantpay;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.modulith.Modulith;

@Modulith
@SpringBootApplication
public class InstantPaymentServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(InstantPaymentServiceApplication.class, args);
    }
}

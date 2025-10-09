package com.instantpay.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.instantpay.adapter.in.web.PaymentController;
import com.instantpay.application.dto.SendPaymentRequest;
import com.instantpay.domain.port.in.SendPaymentUseCase;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PaymentController.class)
@Import(PaymentControllerValidationTest.MockConfig.class)
class PaymentControllerValidationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;

    @TestConfiguration
    static class MockConfig {
        @Bean
        SendPaymentUseCase sendPaymentUseCase() {
            return Mockito.mock(SendPaymentUseCase.class);
        }
    }

    @Test
    void rejectsNegativeAmount() throws Exception {
        var bad = new SendPaymentRequest(
                "CH9300762011623852957",
                "CH470048081",
                "CHF",
                new BigDecimal("-1.00"),
                "ref-123"
        );

        mvc.perform(post("/api/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "idem-123")
                        .content(om.writeValueAsString(bad)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsWrongCurrencyLength() throws Exception {
        var bad = new SendPaymentRequest(
                "CH9300762011623852957",
                "CH470048081",
                "CH",  // too short (must be 3)
                new BigDecimal("10.00"),
                "ref"
        );

        mvc.perform(post("/api/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "idem-abc")
                        .content(om.writeValueAsString(bad)))
                .andExpect(status().isBadRequest());
    }
}

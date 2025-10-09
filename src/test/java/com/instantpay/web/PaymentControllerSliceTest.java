package com.instantpay.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.instantpay.adapter.in.web.PaymentController;
import com.instantpay.application.dto.SendPaymentRequest;
import com.instantpay.domain.model.PaymentStatus;
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
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PaymentController.class)
@Import(PaymentControllerSliceTest.MockConfig.class)   // 👈 import our test configuration
class PaymentControllerSliceTest {

    @TestConfiguration
    static class MockConfig {
        @Bean
        SendPaymentUseCase sendPaymentUseCase() {
            return Mockito.mock(SendPaymentUseCase.class);
        }
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;
    @Autowired SendPaymentUseCase sendPaymentUseCase;

    @Test
    void sendPayment_returnsCompleted() throws Exception {
        var req = new SendPaymentRequest(
                "CH9300762011623852957",
                "CH5604835012345678009",
                "CHF",
                new BigDecimal("25.00"),
                "ref-123"
        );

        var result = new SendPaymentUseCase.Result(
                UUID.randomUUID(),
                PaymentStatus.COMPLETED,
                "OK"
        );

        Mockito.when(sendPaymentUseCase.send(any(SendPaymentUseCase.SendPaymentCommand.class)))
                .thenReturn(result);

        mvc.perform(post("/api/payments") // adjust if needed
                        .header("Idempotency-Key", "idem-123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(result.paymentId().toString()))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.message").value("OK"));
    }
}

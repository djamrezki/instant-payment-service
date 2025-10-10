package com.instantpay.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.instantpay.adapter.in.web.PaymentController;
import com.instantpay.application.dto.SendPaymentRequest;
import com.instantpay.domain.port.in.SendPaymentUseCase;
import org.junit.jupiter.api.BeforeEach;
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

import static org.hamcrest.Matchers.startsWith;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PaymentController.class)
@Import(PaymentControllerValidationTest.MockConfig.class)
class PaymentControllerValidationTest {

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

    private static final String VALID_DEBTOR  = "CH9300762011623852957";
    private static final String VALID_CREDITOR = "CH5604835012345678009";

    @BeforeEach
    void resetMocks() { reset(sendPaymentUseCase); }

    @Test
    void rejectsNegativeAmount_returns400_problemJson() throws Exception {
        var bad = new SendPaymentRequest(
                VALID_DEBTOR,
                VALID_CREDITOR,
                "CHF",
                new BigDecimal("-1.00"),
                "ref-123"
        );

        mvc.perform(post("/api/payments")
                        .header("Idempotency-Key", "idem-amt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(bad)))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Content-Type", startsWith("application/problem+json")))
                .andExpect(jsonPath("$.type").value("about:blank/validation-error"))
                .andExpect(jsonPath("$.status").value(400));
        verifyNoInteractions(sendPaymentUseCase);
    }

    @Test
    void rejectsWrongCurrencyLength_returns400_problemJson() throws Exception {
        var bad = new SendPaymentRequest(
                VALID_DEBTOR,
                VALID_CREDITOR,
                "CH", // too short (must be 3)
                new BigDecimal("10.00"),
                "ref"
        );

        mvc.perform(post("/api/payments")
                        .header("Idempotency-Key", "idem-cur")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(bad)))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Content-Type", startsWith("application/problem+json")))
                .andExpect(jsonPath("$.type").value("about:blank/validation-error"))
                .andExpect(jsonPath("$.status").value(400));
        verifyNoInteractions(sendPaymentUseCase);
    }
}

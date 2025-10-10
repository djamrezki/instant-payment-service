package com.instantpay.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.instantpay.adapter.in.web.PaymentController;
import com.instantpay.application.dto.SendPaymentRequest;
import com.instantpay.domain.error.AccountNotFoundException;
import com.instantpay.domain.error.InsufficientFundsException;
import com.instantpay.domain.model.PaymentStatus;
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
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith; // Hamcrest
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PaymentController.class)
@Import(PaymentControllerSliceTest.MockConfig.class)
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

    private static final String DEBTOR_IBAN   = "CH9300762011623852957";
    private static final String CREDITOR_IBAN = "CH5604835012345678009";

    @BeforeEach
    void resetMocks() {
        // Clear any stubs from previous tests in the cached context
        reset(sendPaymentUseCase);
    }

    @Test
    void sendPayment_returnsCompleted() throws Exception {
        var req = new SendPaymentRequest(
                DEBTOR_IBAN,
                CREDITOR_IBAN,
                "CHF",
                new BigDecimal("25.00"),
                "ref-123"
        );

        var result = new SendPaymentUseCase.Result(
                UUID.randomUUID(),
                PaymentStatus.COMPLETED,
                "OK"
        );

        when(sendPaymentUseCase.send(any(SendPaymentUseCase.SendPaymentCommand.class)))
                .thenReturn(result);

        mvc.perform(post("/api/payments")
                        .header("Idempotency-Key", "idem-123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(result.paymentId().toString()))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.message").value("OK"));
    }

    @Test
    void insufficientFunds_mapsTo422_problemJson() throws Exception {
        when(sendPaymentUseCase.send(any()))
                .thenThrow(new InsufficientFundsException("Insufficient balance on source account."));

        var req = new SendPaymentRequest(
                DEBTOR_IBAN,
                CREDITOR_IBAN,
                "CHF",
                new BigDecimal("9999.00"),
                "big transfer"
        );

        mvc.perform(post("/api/payments")
                        .header("Idempotency-Key", "idem-422")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(req)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(header().string("Content-Type", startsWith("application/problem+json")))
                .andExpect(jsonPath("$.type", containsString("business-rule")))
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.detail", containsString("Insufficient")));
    }

    @Test
    void accountNotFound_mapsTo404_problemJson() throws Exception {
        when(sendPaymentUseCase.send(any()))
                .thenThrow(new AccountNotFoundException("Account not found: " + CREDITOR_IBAN));

        var req = new SendPaymentRequest(
                DEBTOR_IBAN,
                CREDITOR_IBAN,
                "CHF",
                new BigDecimal("10.00"),
                null
        );

        mvc.perform(post("/api/payments")
                        .header("Idempotency-Key", "idem-404")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(req)))
                .andExpect(status().isNotFound())
                .andExpect(header().string("Content-Type", startsWith("application/problem+json")))
                .andExpect(jsonPath("$.type", containsString("not-found")))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail", containsString(CREDITOR_IBAN)));
    }
}

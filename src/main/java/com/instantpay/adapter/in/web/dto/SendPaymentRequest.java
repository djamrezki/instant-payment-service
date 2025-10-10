package com.instantpay.adapter.in.web.dto;

import com.instantpay.validation.ValidIban;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record SendPaymentRequest(
        @NotBlank @ValidIban String debtorIban,
        @NotBlank @ValidIban String creditorIban,
        @NotBlank @Size(min=3, max=3) String currency,
        @NotNull @DecimalMin(value = "0.01") BigDecimal amount,
        @Size(max = 140) String remittanceInfo
) {}
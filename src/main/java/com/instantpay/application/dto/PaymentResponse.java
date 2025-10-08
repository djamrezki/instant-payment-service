package com.instantpay.application.dto;

import java.util.UUID;

public record PaymentResponse(UUID id, String status, String message) {}

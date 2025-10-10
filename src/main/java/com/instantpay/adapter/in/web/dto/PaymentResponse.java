package com.instantpay.adapter.in.web.dto;

import java.util.UUID;

public record PaymentResponse(UUID id, String status, String message) {}

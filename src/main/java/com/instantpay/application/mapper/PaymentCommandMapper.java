package com.instantpay.application.mapper;

import com.instantpay.application.dto.SendPaymentRequest;
import com.instantpay.domain.port.in.SendPaymentUseCase.SendPaymentCommand;

public final class PaymentCommandMapper {
    private PaymentCommandMapper() {}
    public static SendPaymentCommand toCommand(String idemKey, String requestId, SendPaymentRequest request) {
        return  new SendPaymentCommand(
                idemKey,
                request.debtorIban(),
                request.creditorIban(),
                request.currency(),
                request.amount(),
                request.remittanceInfo(),
                requestId
        );
    }
}

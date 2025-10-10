package com.instantpay.adapter.in.web.mapper;

import com.instantpay.adapter.in.web.dto.SendPaymentRequest;
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

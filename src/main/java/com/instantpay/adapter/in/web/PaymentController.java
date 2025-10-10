package com.instantpay.adapter.in.web;

import com.instantpay.adapter.in.web.dto.PaymentResponse;
import com.instantpay.adapter.in.web.dto.SendPaymentRequest;
import com.instantpay.adapter.in.web.mapper.PaymentCommandMapper;
import com.instantpay.domain.port.in.SendPaymentUseCase;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final SendPaymentUseCase sendPayment;

    public PaymentController(SendPaymentUseCase sendPayment) {
        this.sendPayment = sendPayment;
    }

    @PostMapping
    public ResponseEntity<PaymentResponse> send(
            @RequestHeader("Idempotency-Key") String idemKey,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            @RequestBody @Valid SendPaymentRequest request) {



        var result = sendPayment.send(PaymentCommandMapper.toCommand(idemKey, requestId,request));
        var body = new PaymentResponse(result.paymentId(), result.status().name(), result.message());
        // Pick one depending on your flow:
        return switch (result.status()) {
            case COMPLETED -> ResponseEntity.status(201).body(body);
            case CREATED   -> ResponseEntity.accepted().body(body); // 202
            case FAILED    -> ResponseEntity.status(422).body(body); // or 409/400 depending on error mapping
        };
    }

    @GetMapping("/health")
    public String health() { return "OK"; }
}

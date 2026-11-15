package com.ecom.dto;

import java.time.LocalDateTime;

public record PaymentResult(
        boolean successful,
        String transactionId,
        String gatewayReference,
        String paymentDetails,
        String message,
        LocalDateTime timestamp
) {
    public static PaymentResult success(String transactionId, String gatewayReference, String paymentDetails) {
        return new PaymentResult(true, transactionId, gatewayReference, paymentDetails, "Payment Authorized Successfully", LocalDateTime.now());
    }

    public static PaymentResult failed(String transactionId, String message) {
        return new PaymentResult(false, transactionId, null, null, message, LocalDateTime.now());
    }
}

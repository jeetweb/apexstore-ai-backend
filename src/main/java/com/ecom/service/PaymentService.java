package com.ecom.service;

import com.ecom.dto.CheckoutRequest;
import com.ecom.dto.PaymentResult;
import com.ecom.exception.PaymentFailedException;
import com.ecom.model.Order;
import com.ecom.model.Payment;
import com.ecom.model.PaymentMethod;
import com.ecom.model.PaymentStatus;
import com.ecom.repository.PaymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;

    public PaymentService(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    @Transactional
    public Payment processDigitalPayment(Order order, CheckoutRequest request) {
        PaymentMethod method = request.getPaymentMethod();
        String txnPrefix = "TXN-" + method.name() + "-";
        String uniqueTxnId = txnPrefix + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String gatewayRef = "GW-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase();

        Payment payment = new Payment();
        payment.setOrder(order);
        payment.setTransactionId(uniqueTxnId);
        payment.setPaymentMethod(method);
        payment.setAmount(order.getTotalAmount());
        payment.setGatewayReference(gatewayRef);

        try {
            switch (method) {
                case CREDIT_CARD, DEBIT_CARD -> handleCardPayment(payment, request);
                case UPI -> handleUpiPayment(payment, request);
                case DIGITAL_WALLET -> handleWalletPayment(payment, request);
                default -> throw new PaymentFailedException("Unsupported digital payment method: " + method);
            }

            payment.setPaymentStatus(PaymentStatus.SUCCESS);
            payment.setPaidAt(LocalDateTime.now());
            return paymentRepository.save(payment);

        } catch (PaymentFailedException ex) {
            payment.setPaymentStatus(PaymentStatus.FAILED);
            payment.setFailureReason(ex.getMessage());
            paymentRepository.save(payment);
            throw ex;
        }
    }

    private void handleCardPayment(Payment payment, CheckoutRequest request) {
        String cardNum = request.getCardNumber() != null ? request.getCardNumber().replaceAll("\\s+", "").replaceAll("-", "") : "";
        String cvv = request.getCardCvv() != null ? request.getCardCvv().trim() : "";
        String expiry = request.getCardExpiry() != null ? request.getCardExpiry().trim() : "";

        if (cardNum.length() < 13 || cardNum.length() > 19) {
            throw new PaymentFailedException("Invalid card number format. Must be 13-19 digits.");
        }
        if (cvv.length() < 3 || cvv.length() > 4) {
            throw new PaymentFailedException("Invalid CVV security code.");
        }
        if (!expiry.matches("^(0[1-9]|1[0-2])/?([0-9]{2})$")) {
            throw new PaymentFailedException("Invalid expiration date format. Use MM/YY (e.g. 12/28).");
        }

        // Test failure trigger for demonstration/testing
        if (cardNum.endsWith("0000") || "999".equals(cvv)) {
            throw new PaymentFailedException("Card transaction declined by issuing bank (Insufficient funds / Test trigger).");
        }

        String last4 = cardNum.substring(cardNum.length() - 4);
        String cardType = cardNum.startsWith("4") ? "Visa" : (cardNum.startsWith("5") ? "Mastercard" : "Digital Card");
        payment.setPaymentDetails(cardType + " ending in " + last4 + " (Exp: " + expiry + ")");
    }

    private void handleUpiPayment(Payment payment, CheckoutRequest request) {
        String upiId = request.getUpiId();
        if (upiId == null || !upiId.contains("@") || upiId.trim().length() < 4) {
            throw new PaymentFailedException("Invalid UPI ID. Expected format: username@bank (e.g., user@okhdfcbank or merchant@upi).");
        }

        if (upiId.equalsIgnoreCase("fail@upi")) {
            throw new PaymentFailedException("UPI Payment rejected: UPI PIN validation failed or timed out.");
        }

        payment.setPaymentDetails("UPI ID: " + upiId.trim() + " (Instant Settlement Approved)");
    }

    private void handleWalletPayment(Payment payment, CheckoutRequest request) {
        String wallet = request.getWalletProvider();
        if (wallet == null || wallet.isBlank()) {
            wallet = "Apple Pay";
        }

        if ("FAIL_WALLET".equalsIgnoreCase(wallet)) {
            throw new PaymentFailedException("Digital Wallet transaction failed: User cancelled authorization.");
        }

        payment.setPaymentDetails("Digital Wallet: " + wallet + " (Biometric One-Touch Authentication)");
    }
}

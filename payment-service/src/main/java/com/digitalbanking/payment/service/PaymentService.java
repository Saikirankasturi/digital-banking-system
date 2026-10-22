package com.digitalbanking.payment.service;

import com.digitalbanking.common.enums.Currency;
import com.digitalbanking.payment.client.AccountClient;
import com.digitalbanking.payment.domain.PaymentOrder;
import com.digitalbanking.payment.repository.PaymentOrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentOrderRepository paymentOrderRepository;
    private final AccountClient accountClient;

    public PaymentOrder createOrder(String accountId, BigDecimal amount, Currency currency) {
        String orderId = UUID.randomUUID().toString();
        String gatewayOrderId = "order_pg_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 8);

        PaymentOrder order = PaymentOrder.builder()
                .id(orderId)
                .gatewayOrderId(gatewayOrderId)
                .accountId(accountId)
                .amount(amount)
                .currency(currency != null ? currency : Currency.USD)
                .status("CREATED")
                .build();

        PaymentOrder saved = paymentOrderRepository.save(order);
        log.info("Created payment order {} for account {} with amount {} {}",
                saved.getGatewayOrderId(), accountId, amount, currency);
        return saved;
    }

    @Transactional
    public PaymentOrder processWebhook(String gatewayOrderId, String paymentId, boolean isSuccessful, String failureReason) {
        PaymentOrder order = paymentOrderRepository.findByGatewayOrderId(gatewayOrderId)
                .orElseThrow(() -> new RuntimeException("Payment order not found: " + gatewayOrderId));

        if ("PAID".equals(order.getStatus())) {
            log.info("Order {} is already marked as PAID", gatewayOrderId);
            return order;
        }

        if (isSuccessful) {
            order.setStatus("PAID");
            order.setGatewayPaymentId(paymentId);
            paymentOrderRepository.save(order);

            // Credit the customer's bank account
            log.info("Payment success confirmed for order {}. Crediting account {}...", gatewayOrderId, order.getAccountId());
            try {
                accountClient.credit(
                        order.getAccountId(),
                        order.getAmount(),
                        "PG_PAYMENT_" + paymentId,
                        "Payment Gateway Deposit: " + paymentId
                );
            } catch (Exception e) {
                log.error("Failed to credit account after successful payment webhook: {}", e.getMessage());
            }
        } else {
            order.setStatus("FAILED");
            order.setFailureReason(failureReason != null ? failureReason : "Payment failed at gateway");
            paymentOrderRepository.save(order);
            log.warn("Payment failed for order {}: {}", gatewayOrderId, failureReason);
        }

        return order;
    }

    public PaymentOrder getOrder(String id) {
        return paymentOrderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Payment order not found: " + id));
    }
}

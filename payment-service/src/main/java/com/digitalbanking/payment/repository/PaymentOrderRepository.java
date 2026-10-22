package com.digitalbanking.payment.repository;

import com.digitalbanking.payment.domain.PaymentOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentOrderRepository extends JpaRepository<PaymentOrder, String> {

    Optional<PaymentOrder> findByGatewayOrderId(String gatewayOrderId);

    List<PaymentOrder> findByAccountIdOrderByCreatedAtDesc(String accountId);
}

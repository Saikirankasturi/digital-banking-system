package com.digitalbanking.transaction.repository;

import com.digitalbanking.transaction.domain.TransferTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TransferTransactionRepository extends JpaRepository<TransferTransaction, String> {

    Optional<TransferTransaction> findByIdempotencyKey(String idempotencyKey);

    Optional<TransferTransaction> findBySagaId(String sagaId);

    List<TransferTransaction> findBySourceAccountIdOrderByCreatedAtDesc(String sourceAccountId);
}

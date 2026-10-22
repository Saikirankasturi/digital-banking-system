package com.digitalbanking.transaction.repository;

import com.digitalbanking.transaction.domain.SagaInstance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SagaInstanceRepository extends JpaRepository<SagaInstance, String> {

    Optional<SagaInstance> findByTransactionId(String transactionId);
}

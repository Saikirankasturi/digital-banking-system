package com.digitalbanking.transaction.repository;

import com.digitalbanking.transaction.domain.SagaStepLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SagaStepLogRepository extends JpaRepository<SagaStepLog, String> {

    List<SagaStepLog> findBySagaIdOrderByTimestampAsc(String sagaId);
}

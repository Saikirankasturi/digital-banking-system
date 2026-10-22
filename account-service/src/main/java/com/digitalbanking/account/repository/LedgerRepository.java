package com.digitalbanking.account.repository;

import com.digitalbanking.account.domain.LedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LedgerRepository extends JpaRepository<LedgerEntry, String> {

    List<LedgerEntry> findByAccountIdOrderByCreatedAtDesc(String accountId);

    List<LedgerEntry> findByTransactionIdOrderByCreatedAtAsc(String transactionId);
}

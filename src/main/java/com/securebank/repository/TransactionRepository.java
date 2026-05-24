package com.securebank.repository;

import com.securebank.domain.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Optional;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    /** All transactions touching any of the given accounts, newest first. */
    @Query("select t from Transaction t "
            + "where t.fromAccount.id in :accountIds or t.toAccount.id in :accountIds "
            + "order by t.createdAt desc")
    Page<Transaction> findForAccounts(@Param("accountIds") Collection<Long> accountIds, Pageable pageable);

    Optional<Transaction> findByReference(String reference);
}

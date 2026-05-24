package com.securebank.repository;

import com.securebank.domain.Account;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, Long> {

    List<Account> findByOwnerId(Long ownerId);

    boolean existsByAccountNumber(String accountNumber);

    /**
     * Loads an account with a {@code SELECT ... FOR UPDATE} row lock. Any concurrent transfer
     * touching the same account blocks here until this transaction commits or rolls back,
     * which is what makes balance updates safe under concurrency.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Account a where a.id = :id")
    Optional<Account> findByIdForUpdate(@Param("id") Long id);
}

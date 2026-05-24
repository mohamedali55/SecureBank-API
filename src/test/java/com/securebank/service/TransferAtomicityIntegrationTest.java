package com.securebank.service;

import com.securebank.domain.Account;
import com.securebank.domain.Role;
import com.securebank.domain.User;
import com.securebank.dto.TransferRequest;
import com.securebank.repository.AccountRepository;
import com.securebank.repository.AuditLogRepository;
import com.securebank.repository.TransactionRepository;
import com.securebank.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;

/**
 * THE test banking interviewers ask about: prove that money is never lost mid-transfer.
 *
 * <p>We force a real crash <em>after the source account has been debited but before the
 * destination credit commits</em>, then assert that the surrounding {@code @Transactional}
 * boundary rolled everything back: both balances are unchanged, total money is conserved,
 * and no ledger row was written.
 *
 * <p>Runs against an in-memory H2 database in PostgreSQL mode, so it executes on any machine
 * with just a JDK — no Docker required.
 */
@SpringBootTest
@ActiveProfiles("test")
class TransferAtomicityIntegrationTest {

    @Autowired
    private TransferService transferService;

    // @SpyBean wraps the real repository so we can make ONE call (the destination credit) fail.
    @SpyBean
    private AccountRepository accountRepository;

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private TransactionRepository transactionRepository;
    @Autowired
    private AuditLogRepository auditLogRepository;

    private User owner;

    @BeforeEach
    void cleanDatabase() {
        transactionRepository.deleteAll();
        auditLogRepository.deleteAll();
        accountRepository.deleteAll();
        userRepository.deleteAll();
        owner = userRepository.save(new User("alice", "alice@example.com", "hash", Role.USER));
    }

    @Test
    @DisplayName("a crash mid-transfer rolls back completely — no money is lost or created")
    void failedTransferIsFullyRolledBack() {
        Account source = newAccount("100.00");
        Account destination = newAccount("50.00");
        BigDecimal totalBefore = source.getBalance().add(destination.getBalance());
        Long destinationId = destination.getId();

        // Simulate a failure that strikes after the debit, while crediting the destination.
        doThrow(new RuntimeException("Simulated database crash mid-transfer"))
                .when(accountRepository)
                .save(argThat(account -> account != null && destinationId.equals(account.getId())));

        assertThatThrownBy(() -> transferService.transfer(owner.getId(), owner.getUsername(),
                new TransferRequest(source.getId(), destination.getId(), new BigDecimal("30.00"), "rent")))
                .isInstanceOf(RuntimeException.class);

        // Re-read straight from the database (fresh transaction): nothing should have changed.
        BigDecimal sourceAfter = accountRepository.findById(source.getId()).orElseThrow().getBalance();
        BigDecimal destinationAfter = accountRepository.findById(destinationId).orElseThrow().getBalance();

        assertThat(sourceAfter).as("source untouched").isEqualByComparingTo("100.00");
        assertThat(destinationAfter).as("destination untouched").isEqualByComparingTo("50.00");
        assertThat(sourceAfter.add(destinationAfter)).as("total money conserved").isEqualByComparingTo(totalBefore);
        assertThat(transactionRepository.count()).as("no ledger row written").isZero();
    }

    @Test
    @DisplayName("a successful transfer moves the money and conserves the total")
    void successfulTransferMovesMoney() {
        Account source = newAccount("100.00");
        Account destination = newAccount("50.00");
        BigDecimal totalBefore = source.getBalance().add(destination.getBalance());

        transferService.transfer(owner.getId(), owner.getUsername(),
                new TransferRequest(source.getId(), destination.getId(), new BigDecimal("30.00"), "rent"));

        BigDecimal sourceAfter = accountRepository.findById(source.getId()).orElseThrow().getBalance();
        BigDecimal destinationAfter = accountRepository.findById(destination.getId()).orElseThrow().getBalance();

        assertThat(sourceAfter).isEqualByComparingTo("70.00");
        assertThat(destinationAfter).isEqualByComparingTo("80.00");
        assertThat(sourceAfter.add(destinationAfter)).isEqualByComparingTo(totalBefore);
        assertThat(transactionRepository.count()).isEqualTo(1);
    }

    private Account newAccount(String balance) {
        Account account = new Account("SB" + System.nanoTime(), owner, "USD");
        account.setBalance(new BigDecimal(balance));
        return accountRepository.save(account);
    }
}

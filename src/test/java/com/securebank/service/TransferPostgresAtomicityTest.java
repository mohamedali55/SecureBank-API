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
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;

/**
 * The same atomicity proof as {@link TransferAtomicityIntegrationTest}, but against a real
 * PostgreSQL instance with the actual Flyway migrations applied. This is the production-fidelity
 * version: it exercises {@code SELECT ... FOR UPDATE} locking and Postgres transaction semantics.
 *
 * <p>{@code disabledWithoutDocker = true} makes the whole class skip cleanly on machines without
 * a Docker daemon, so the build never fails just because Docker is unavailable.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class TransferPostgresAtomicityTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private TransferService transferService;

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
    @DisplayName("[PostgreSQL] crash mid-transfer rolls back; money is conserved")
    void failedTransferIsFullyRolledBack() {
        Account source = newAccount("100.00");
        Account destination = newAccount("50.00");
        Long destinationId = destination.getId();

        doThrow(new RuntimeException("Simulated database crash mid-transfer"))
                .when(accountRepository)
                .save(argThat(account -> account != null && destinationId.equals(account.getId())));

        assertThatThrownBy(() -> transferService.transfer(owner.getId(), owner.getUsername(),
                new TransferRequest(source.getId(), destination.getId(), new BigDecimal("30.00"), "rent")))
                .isInstanceOf(RuntimeException.class);

        assertThat(accountRepository.findById(source.getId()).orElseThrow().getBalance())
                .isEqualByComparingTo("100.00");
        assertThat(accountRepository.findById(destinationId).orElseThrow().getBalance())
                .isEqualByComparingTo("50.00");
        assertThat(transactionRepository.count()).isZero();
    }

    @Test
    @DisplayName("[PostgreSQL] successful transfer moves the money")
    void successfulTransferMovesMoney() {
        Account source = newAccount("100.00");
        Account destination = newAccount("50.00");

        transferService.transfer(owner.getId(), owner.getUsername(),
                new TransferRequest(source.getId(), destination.getId(), new BigDecimal("30.00"), "rent"));

        assertThat(accountRepository.findById(source.getId()).orElseThrow().getBalance())
                .isEqualByComparingTo("70.00");
        assertThat(accountRepository.findById(destination.getId()).orElseThrow().getBalance())
                .isEqualByComparingTo("80.00");
    }

    private Account newAccount(String balance) {
        Account account = new Account("SB" + System.nanoTime(), owner, "USD");
        account.setBalance(new BigDecimal(balance));
        return accountRepository.save(account);
    }
}

package com.securebank.service;

import com.securebank.domain.Account;
import com.securebank.domain.Role;
import com.securebank.domain.Transaction;
import com.securebank.domain.User;
import com.securebank.dto.TransferRequest;
import com.securebank.exception.BusinessRuleException;
import com.securebank.exception.InsufficientFundsException;
import com.securebank.exception.UnauthorizedOperationException;
import com.securebank.repository.AccountRepository;
import com.securebank.repository.TransactionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Fast, dependency-free unit tests for the transfer business rules.
 * The full ACID rollback proof lives in {@link TransferAtomicityIntegrationTest}.
 */
@ExtendWith(MockitoExtension.class)
class TransferServiceUnitTest {

    @Mock
    private AccountRepository accountRepository;
    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private AuditService auditService;

    @InjectMocks
    private TransferService transferService;

    @Test
    @DisplayName("a valid transfer debits the source and credits the destination by the same amount")
    void transferMovesMoney() {
        User alice = user(1L, "alice");
        Account from = account(10L, alice, "100.00");
        Account to = account(20L, alice, "50.00");
        when(accountRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(from));
        when(accountRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(to));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));

        transferService.transfer(1L, "alice",
                new TransferRequest(10L, 20L, new BigDecimal("30.00"), "rent"));

        assertThat(from.getBalance()).isEqualByComparingTo("70.00");
        assertThat(to.getBalance()).isEqualByComparingTo("80.00");
        verify(transactionRepository).save(any(Transaction.class));
    }

    @Test
    @DisplayName("transferring more than the balance is rejected and nothing is written")
    void rejectsInsufficientFunds() {
        User alice = user(1L, "alice");
        Account from = account(10L, alice, "10.00");
        Account to = account(20L, alice, "50.00");
        when(accountRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(from));
        when(accountRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(to));

        assertThatThrownBy(() -> transferService.transfer(1L, "alice",
                new TransferRequest(10L, 20L, new BigDecimal("30.00"), "rent")))
                .isInstanceOf(InsufficientFundsException.class);

        assertThat(from.getBalance()).isEqualByComparingTo("10.00");
        assertThat(to.getBalance()).isEqualByComparingTo("50.00");
        verify(transactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("transferring to the same account is rejected before any lock is taken")
    void rejectsSameAccount() {
        assertThatThrownBy(() -> transferService.transfer(1L, "alice",
                new TransferRequest(10L, 10L, new BigDecimal("5.00"), "self")))
                .isInstanceOf(BusinessRuleException.class);

        verify(transactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("a user cannot transfer from an account they do not own")
    void rejectsTransferFromAccountNotOwned() {
        User bob = user(2L, "bob");
        User alice = user(1L, "alice");
        Account from = account(10L, bob, "100.00");   // owned by bob
        Account to = account(20L, alice, "50.00");
        when(accountRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(from));
        when(accountRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(to));

        // alice (id 1) tries to move bob's money
        assertThatThrownBy(() -> transferService.transfer(1L, "alice",
                new TransferRequest(10L, 20L, new BigDecimal("30.00"), "theft")))
                .isInstanceOf(UnauthorizedOperationException.class);

        assertThat(from.getBalance()).isEqualByComparingTo("100.00");
        verify(transactionRepository, never()).save(any());
    }

    // ---- helpers ----

    private User user(long id, String username) {
        User u = new User(username, username + "@example.com", "hash", Role.USER);
        ReflectionTestUtils.setField(u, "id", id);
        return u;
    }

    private Account account(long id, User owner, String balance) {
        Account a = new Account("SB" + id, owner, "USD");
        a.credit(new BigDecimal(balance));
        ReflectionTestUtils.setField(a, "id", id);
        return a;
    }
}

package com.securebank.service;

import com.securebank.domain.Account;
import com.securebank.domain.Transaction;
import com.securebank.domain.TransactionStatus;
import com.securebank.domain.TransactionType;
import com.securebank.dto.TransactionResponse;
import com.securebank.dto.TransferRequest;
import com.securebank.exception.BusinessRuleException;
import com.securebank.exception.InsufficientFundsException;
import com.securebank.exception.ResourceNotFoundException;
import com.securebank.exception.UnauthorizedOperationException;
import com.securebank.repository.AccountRepository;
import com.securebank.repository.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Owns the one operation that must never go wrong: moving money between two accounts.
 *
 * <p>The whole method runs in a single database transaction. Both accounts are loaded with a
 * pessimistic {@code SELECT ... FOR UPDATE} lock, acquired in a fixed global order (lowest id
 * first) so two opposing transfers can never deadlock. The debit, the credit, the ledger entry
 * and the audit row all commit together — or, if anything throws, all roll back together. That
 * is the ACID guarantee: money is never created, destroyed, or stranded between accounts.
 *
 * <p>Isolation is READ_COMMITTED, which together with the row locks is enough to prevent lost
 * updates without paying the retry cost of SERIALIZABLE.
 */
@Service
public class TransferService {

    private static final Logger log = LoggerFactory.getLogger(TransferService.class);

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final AuditService auditService;

    public TransferService(AccountRepository accountRepository,
                           TransactionRepository transactionRepository,
                           AuditService auditService) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.auditService = auditService;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED, timeout = 10)
    public TransactionResponse transfer(Long actorUserId, String actorUsername, TransferRequest request) {
        BigDecimal amount = request.amount();

        // --- Pre-lock guards ---
        if (request.fromAccountId().equals(request.toAccountId())) {
            throw new BusinessRuleException("Cannot transfer money to the same account");
        }
        if (amount == null || amount.signum() <= 0) {
            throw new BusinessRuleException("Transfer amount must be positive");
        }

        // --- Lock both accounts in a deadlock-free order (lowest id first) ---
        long lowId = Math.min(request.fromAccountId(), request.toAccountId());
        long highId = Math.max(request.fromAccountId(), request.toAccountId());
        Account low = lockAccount(lowId);
        Account high = lockAccount(highId);

        Account from = request.fromAccountId().equals(low.getId()) ? low : high;
        Account to = request.toAccountId().equals(low.getId()) ? low : high;

        // --- Authorization: only the owner of the source account may move its money ---
        if (!from.getOwner().getId().equals(actorUserId)) {
            throw new UnauthorizedOperationException("You can only transfer from an account you own");
        }

        // --- Business rules ---
        if (!from.isActive()) {
            throw new BusinessRuleException("Source account is not active");
        }
        if (!to.isActive()) {
            throw new BusinessRuleException("Destination account is not active");
        }
        if (!from.getCurrency().equals(to.getCurrency())) {
            throw new BusinessRuleException("Cannot transfer between accounts of different currencies");
        }
        if (from.getBalance().compareTo(amount) < 0) {
            throw new InsufficientFundsException(
                    "Insufficient funds in account " + from.getAccountNumber());
        }

        // --- Move the money. Both writes live inside this single transaction. ---
        from.debit(amount);
        accountRepository.save(from);

        to.credit(amount);
        accountRepository.save(to);

        Transaction tx = transactionRepository.save(new Transaction(
                UUID.randomUUID().toString(), from, to, amount,
                TransactionType.TRANSFER, TransactionStatus.COMPLETED, request.description()));

        auditService.record(actorUsername, "TRANSFER",
                String.format("Transferred %s %s from %s to %s",
                        amount.toPlainString(), from.getCurrency(),
                        from.getAccountNumber(), to.getAccountNumber()));

        log.info("Transfer {} completed: {} {} from {} to {}",
                tx.getReference(), amount.toPlainString(), from.getCurrency(),
                from.getAccountNumber(), to.getAccountNumber());

        return TransactionResponse.from(tx);
    }

    private Account lockAccount(long accountId) {
        return accountRepository.findByIdForUpdate(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found: " + accountId));
    }
}

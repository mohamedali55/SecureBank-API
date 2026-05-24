package com.securebank.service;

import com.securebank.domain.Account;
import com.securebank.domain.Transaction;
import com.securebank.domain.TransactionStatus;
import com.securebank.domain.TransactionType;
import com.securebank.domain.User;
import com.securebank.dto.AccountResponse;
import com.securebank.dto.CreateAccountRequest;
import com.securebank.exception.ResourceNotFoundException;
import com.securebank.exception.UnauthorizedOperationException;
import com.securebank.repository.AccountRepository;
import com.securebank.repository.TransactionRepository;
import com.securebank.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.util.List;
import java.util.UUID;

@Service
public class AccountService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final AccountRepository accountRepository;
    private final UserRepository userRepository;
    private final TransactionRepository transactionRepository;
    private final AuditService auditService;

    public AccountService(AccountRepository accountRepository,
                          UserRepository userRepository,
                          TransactionRepository transactionRepository,
                          AuditService auditService) {
        this.accountRepository = accountRepository;
        this.userRepository = userRepository;
        this.transactionRepository = transactionRepository;
        this.auditService = auditService;
    }

    @Transactional
    public AccountResponse createAccount(Long userId, CreateAccountRequest request) {
        User owner = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Account account = new Account(generateAccountNumber(), owner, request.currencyOrDefault());

        BigDecimal initialDeposit = request.initialDepositOrZero();
        if (initialDeposit.signum() > 0) {
            account.credit(initialDeposit);
        }
        account = accountRepository.save(account);

        // Record the opening deposit as a ledger entry so history is complete.
        if (initialDeposit.signum() > 0) {
            transactionRepository.save(new Transaction(
                    UUID.randomUUID().toString(), null, account, initialDeposit,
                    TransactionType.DEPOSIT, TransactionStatus.COMPLETED, "Opening deposit"));
        }

        auditService.record(owner.getUsername(), "ACCOUNT_CREATED",
                "Opened account " + account.getAccountNumber());

        return AccountResponse.from(account);
    }

    @Transactional(readOnly = true)
    public AccountResponse getAccount(Long userId, Long accountId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found"));
        if (!account.getOwner().getId().equals(userId)) {
            throw new UnauthorizedOperationException("You do not own this account");
        }
        return AccountResponse.from(account);
    }

    @Transactional(readOnly = true)
    public List<AccountResponse> listAccounts(Long userId) {
        return accountRepository.findByOwnerId(userId).stream()
                .map(AccountResponse::from)
                .toList();
    }

    /** Generates a unique 18-character account number, e.g. {@code SB0042196837451209}. */
    private String generateAccountNumber() {
        String number;
        do {
            StringBuilder sb = new StringBuilder("SB");
            for (int i = 0; i < 16; i++) {
                sb.append(RANDOM.nextInt(10));
            }
            number = sb.toString();
        } while (accountRepository.existsByAccountNumber(number));
        return number;
    }
}

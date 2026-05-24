package com.securebank.service;

import com.securebank.domain.Account;
import com.securebank.domain.Transaction;
import com.securebank.dto.TransactionResponse;
import com.securebank.exception.ResourceNotFoundException;
import com.securebank.exception.UnauthorizedOperationException;
import com.securebank.repository.AccountRepository;
import com.securebank.repository.TransactionRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class TransactionService {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;

    public TransactionService(AccountRepository accountRepository,
                              TransactionRepository transactionRepository) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
    }

    /** All transactions touching any account the user owns, newest first. */
    @Transactional(readOnly = true)
    public Page<TransactionResponse> listForUser(Long userId, Pageable pageable) {
        List<Long> accountIds = accountRepository.findByOwnerId(userId).stream()
                .map(Account::getId)
                .toList();
        if (accountIds.isEmpty()) {
            return Page.empty(pageable);
        }
        return transactionRepository.findForAccounts(accountIds, pageable)
                .map(TransactionResponse::from);
    }

    @Transactional(readOnly = true)
    public TransactionResponse getForUser(Long userId, Long transactionId) {
        Transaction tx = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found"));
        if (!touchesUser(tx, userId)) {
            throw new UnauthorizedOperationException("You cannot view this transaction");
        }
        return TransactionResponse.from(tx);
    }

    private boolean touchesUser(Transaction tx, Long userId) {
        return (tx.getFromAccount() != null && tx.getFromAccount().getOwner().getId().equals(userId))
                || (tx.getToAccount() != null && tx.getToAccount().getOwner().getId().equals(userId));
    }
}

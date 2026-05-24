package com.securebank.controller;

import com.securebank.dto.TransactionResponse;
import com.securebank.security.UserPrincipal;
import com.securebank.service.TransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/transactions")
@Tag(name = "Transactions", description = "View transaction history")
@SecurityRequirement(name = "bearerAuth")
public class TransactionController {

    private static final int MAX_PAGE_SIZE = 100;

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @GetMapping
    @Operation(summary = "List transactions across all your accounts, newest first")
    public Page<TransactionResponse> list(@AuthenticationPrincipal UserPrincipal principal,
                                          @RequestParam(defaultValue = "0") int page,
                                          @RequestParam(defaultValue = "20") int size) {
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        int safePage = Math.max(page, 0);
        return transactionService.listForUser(principal.getId(), PageRequest.of(safePage, safeSize));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get one transaction by id (must involve one of your accounts)")
    public TransactionResponse get(@AuthenticationPrincipal UserPrincipal principal,
                                   @PathVariable Long id) {
        return transactionService.getForUser(principal.getId(), id);
    }
}

package com.securebank.controller;

import com.securebank.dto.TransactionResponse;
import com.securebank.dto.TransferRequest;
import com.securebank.security.UserPrincipal;
import com.securebank.service.TransferService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/transfers")
@Tag(name = "Transfers", description = "Move money atomically between accounts")
@SecurityRequirement(name = "bearerAuth")
public class TransferController {

    private final TransferService transferService;

    public TransferController(TransferService transferService) {
        this.transferService = transferService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Transfer money from one of your accounts to another account",
            description = "Runs in a single ACID transaction with row-level locking: the debit and "
                    + "credit either both commit or both roll back.")
    public TransactionResponse transfer(@AuthenticationPrincipal UserPrincipal principal,
                                        @Valid @RequestBody TransferRequest request) {
        return transferService.transfer(principal.getId(), principal.getUsername(), request);
    }
}

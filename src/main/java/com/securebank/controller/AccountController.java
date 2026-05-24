package com.securebank.controller;

import com.securebank.dto.AccountResponse;
import com.securebank.dto.CreateAccountRequest;
import com.securebank.security.UserPrincipal;
import com.securebank.service.AccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/accounts")
@Tag(name = "Accounts", description = "Open and view bank accounts")
@SecurityRequirement(name = "bearerAuth")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Open a new account for the authenticated user")
    public AccountResponse create(@AuthenticationPrincipal UserPrincipal principal,
                                  @Valid @RequestBody CreateAccountRequest request) {
        return accountService.createAccount(principal.getId(), request);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get one of your accounts by id")
    public AccountResponse get(@AuthenticationPrincipal UserPrincipal principal,
                               @PathVariable Long id) {
        return accountService.getAccount(principal.getId(), id);
    }

    @GetMapping
    @Operation(summary = "List all of your accounts")
    public List<AccountResponse> list(@AuthenticationPrincipal UserPrincipal principal) {
        return accountService.listAccounts(principal.getId());
    }
}

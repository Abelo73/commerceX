package com.commercex.auth.api;

import com.commercex.auth.api.AccountDtos.AddressRequest;
import com.commercex.auth.api.AccountDtos.AddressResponse;
import com.commercex.auth.api.AccountDtos.ChangePasswordRequest;
import com.commercex.auth.api.AccountDtos.UpdateProfileRequest;
import com.commercex.auth.application.AccountService;
import com.commercex.auth.application.AddressService;
import com.commercex.auth.application.UserView;
import com.commercex.common.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** The authenticated user's own account. The user ID always comes from the token, never the request. */
@RestController
@RequestMapping("/api/v1/me")
@Tag(name = "Account")
@SecurityRequirement(name = "bearer")
class AccountController {

    private final AccountService accountService;
    private final AddressService addressService;

    AccountController(AccountService accountService, AddressService addressService) {
        this.accountService = accountService;
        this.addressService = addressService;
    }

    @GetMapping
    UserView me(@AuthenticationPrincipal Jwt jwt) {
        return accountService.profile(CurrentUser.id(jwt));
    }

    @PatchMapping
    UserView updateProfile(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody UpdateProfileRequest body) {
        return accountService.updateProfile(CurrentUser.id(jwt), body.firstName(), body.lastName());
    }

    @PutMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Change password; signs out all other sessions")
    void changePassword(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ChangePasswordRequest body) {
        accountService.changePassword(CurrentUser.id(jwt), body.currentPassword(), body.newPassword());
    }

    @GetMapping("/addresses")
    List<AddressResponse> addresses(@AuthenticationPrincipal Jwt jwt) {
        return addressService.list(CurrentUser.id(jwt)).stream().map(AddressResponse::from).toList();
    }

    @GetMapping("/addresses/{id}")
    AddressResponse address(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
        return AddressResponse.from(addressService.get(CurrentUser.id(jwt), id));
    }

    @PostMapping("/addresses")
    @ResponseStatus(HttpStatus.CREATED)
    AddressResponse createAddress(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody AddressRequest body) {
        return AddressResponse.from(addressService.create(CurrentUser.id(jwt), body.details(), body.isDefault()));
    }

    @PutMapping("/addresses/{id}")
    AddressResponse updateAddress(@AuthenticationPrincipal Jwt jwt, @PathVariable long id,
            @Valid @RequestBody AddressRequest body) {
        return AddressResponse.from(
                addressService.update(CurrentUser.id(jwt), id, body.details(), body.isDefault()));
    }

    @DeleteMapping("/addresses/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteAddress(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
        addressService.delete(CurrentUser.id(jwt), id);
    }
}

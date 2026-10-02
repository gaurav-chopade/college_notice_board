package com.college.noticeboard.exception;

import com.college.noticeboard.dto.RegistrationDtos.AccountStatusView;
import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Thrown at sign-in, only after the password has been verified, when the account is not APPROVED.
 * Carries the applicant's status so the login page can show where their request stands.
 */
@Getter
public class AccountStatusException extends ApiException {

    private final AccountStatusView account;

    public AccountStatusException(String message, AccountStatusView account) {
        super(HttpStatus.FORBIDDEN, message);
        this.account = account;
    }
}

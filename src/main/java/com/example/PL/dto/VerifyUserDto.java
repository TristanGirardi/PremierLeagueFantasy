package com.example.PL.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class VerifyUserDto {
    @NotNull(message = "Email is required")
    private String email;
    @NotNull(message = "Verification code is required")
    private String verificationCode;
}

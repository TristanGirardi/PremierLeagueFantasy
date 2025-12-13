package com.example.PL.dto;


import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RegisterUserDto {
    @NotNull(message = "Full name is required")
    private String fullName;
    @NotNull(message = "Address is required")
    private String address;
    @NotNull(message = "Username is required")
    private String username;
    @NotNull(message = "Email is required")
    private String email;
    @NotNull(message = "Password is required")
    private String password;
}

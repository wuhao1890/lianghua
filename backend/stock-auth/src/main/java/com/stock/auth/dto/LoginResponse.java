package com.stock.auth.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class LoginResponse {

    private String token;

    private Long userId;

    private String username;

    private String nickname;

    private BigDecimal availableCash;

    private BigDecimal initialCapital;

    private String role;

    public LoginResponse() {
    }

    public LoginResponse(String token, Long userId, String username, String nickname,
                         BigDecimal availableCash, BigDecimal initialCapital, String role) {
        this.token = token;
        this.userId = userId;
        this.username = username;
        this.nickname = nickname;
        this.availableCash = availableCash;
        this.initialCapital = initialCapital;
        this.role = role;
    }
}

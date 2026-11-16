package com.team262.moviematch.service.account;

public interface PasswordResetMailService {
    void send(String email, String signInName, String token);
}

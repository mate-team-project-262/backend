package com.team262.moviematch.service.account;

public interface PasswordResetService {
    void request(String email);
    void check(String token);
    void reset(String token, String newPassword);
    void cancel(Long userId);
}

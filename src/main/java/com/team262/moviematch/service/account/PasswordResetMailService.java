package com.team262.moviematch.service.account;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class PasswordResetMailService {
    private final JavaMailSender sender;
    private final String from;
    private final String frontendUrl;

    public PasswordResetMailService(JavaMailSender sender,
            @Value("${app.mail.from}") String from,
            @Value("${app.frontend-url}") String frontendUrl) {
        this.sender = sender;
        this.from = from;
        this.frontendUrl = frontendUrl.replaceAll("/+$", "");
    }

    public void send(String email, String signInName, String token) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(email);
        message.setSubject("Reset your What to Watch password");
        message.setText("Use this link to set a new password:\n" + frontendUrl
                + "/reset-password?token=" + token
                + "\n\nYour sign-in name: " + signInName
                + "\nThis link expires in 20 minutes. If you did not request it, ignore this email.");
        sender.send(message);
    }
}

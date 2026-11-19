package com.team262.moviematch.service.account;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.event.TransactionPhase;

@Service
public class PasswordResetMailService {
    private static final Logger LOGGER = LoggerFactory.getLogger(PasswordResetMailService.class);
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

    public record ResetMailRequested(String email, String signInName, String token) {}

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void send(ResetMailRequested request) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(request.email());
        message.setSubject("Reset your What to Watch password");
        message.setText("Use this link to set a new password:\n" + frontendUrl
                + "/reset-password?token=" + request.token()
                + "\n\nYour sign-in name: " + request.signInName()
                + "\nThis link expires in 20 minutes. If you did not request it, ignore this email.");
        try {
            sender.send(message);
        } catch (Exception exception) {
            LOGGER.error("Password reset delivery failed ({})", exception.getClass().getSimpleName());
        }
    }
}

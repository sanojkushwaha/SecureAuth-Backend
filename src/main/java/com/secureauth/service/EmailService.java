package com.secureauth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;


    @Value("${app.frontend.url}")
    private String frontendUrl;

    public void sendVerificationEmail(
            String toEmail,
            String fullName,
            String token
    ) {

        String link = frontendUrl + "/verify-email?token=" + token;

        String body =
                "Hi " + fullName + ",\n\n"
                + "Thanks for registering with SecureAuth.\n\n"
                + "Please verify your email by clicking the link below:\n\n"
                + link + "\n\n"
                + "This verification link expires in 24 hours.\n\n"
                + "If you did not create this account, please ignore this email.\n\n"
                + "Regards,\n"
                + "SecureAuth Team";

        send(
                toEmail,
                "Verify your email - SecureAuth",
                body
        );
    }

    public void sendPasswordResetEmail(
            String toEmail,
            String fullName,
            String token
    ) {

        String link = frontendUrl + "/reset-password?token=" + token;

        String body =
                "Hi " + fullName + ",\n\n"
                + "We received a request to reset your password.\n\n"
                + "Click the link below to set a new password:\n\n"
                + link + "\n\n"
                + "This link expires in 1 hour.\n\n"
                + "If you did not request this, you can safely ignore this email.\n\n"
                + "Regards,\n"
                + "SecureAuth Team";

        send(
                toEmail,
                "Reset your password - SecureAuth",
                body
        );
    }

    private void send(
            String to,
            String subject,
            String body
    ) {

        SimpleMailMessage message = new SimpleMailMessage();

        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);

        mailSender.send(message);
    }
}

package com.shortlyai.auth.email;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class EmailService {

    private final JavaMailSender javaMailSender;

    private final String fromEmail;

    public EmailService(
            JavaMailSender javaMailSender,
            @Value("${spring.mail.username}") String fromEmail
    ) {
        this.javaMailSender = javaMailSender;
        this.fromEmail = fromEmail;
    }

    @Async("emailExecutor")
    public void sendVerificationEmail(String toEmail, String token, String userName) {

        SimpleMailMessage mailMessage = new SimpleMailMessage();

        mailMessage.setSubject("Verify your ShortlyAI account");

        mailMessage.setText("Hi, " + userName + "\nPlease click this link to verify your ShortlyAI account\n" + "http://localhost:8081/api/v1/auth/verify?token=" + token);

        mailMessage.setFrom(fromEmail);

        mailMessage.setTo(toEmail);

        javaMailSender.send(mailMessage);

        log.info("Account verification mail sent to user: {}", userName);

    }
}

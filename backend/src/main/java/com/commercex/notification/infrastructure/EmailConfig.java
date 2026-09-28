package com.commercex.notification.infrastructure;

import com.commercex.notification.application.EmailSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

@Configuration(proxyBeanMethods = false)
class EmailConfig {

    private static final Logger log = LoggerFactory.getLogger(EmailConfig.class);

    /** SMTP when {@code spring.mail.host} is configured; otherwise a no-op sender that logs metadata only. */
    @Bean
    EmailSender emailSender(ObjectProvider<JavaMailSender> mailSender,
            @Value("${commercex.notification.from:no-reply@commercex.local}") String from) {
        JavaMailSender smtp = mailSender.getIfAvailable();
        if (smtp == null) {
            log.warn("spring.mail.host not configured: emails will be dropped (logged without content)");
            return message -> log.info("Email not sent (no SMTP configured): {}", message);
        }
        return message -> {
            SimpleMailMessage mail = new SimpleMailMessage();
            mail.setFrom(from);
            mail.setTo(message.to());
            mail.setSubject(message.subject());
            mail.setText(message.textBody());
            smtp.send(mail);
        };
    }
}

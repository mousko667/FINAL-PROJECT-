package com.oct.invoicesystem.domain.notification.service;

import java.util.List;
import java.util.Map;

public interface EmailService {
    void sendEmail(String to, String subject, String templateName, Map<String, Object> variables);
    void sendEmailToUsers(List<String> emails, String subject, String templateName, Map<String, Object> variables);
}

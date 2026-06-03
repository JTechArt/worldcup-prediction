package com.worldcup.prediction.service;

import com.worldcup.prediction.domain.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Email service stub — all methods log intent only.
 * Part 8 replaces these with real SMTP calls.
 */
@Service
@Slf4j
public class EmailService {

    public void sendApprovalEmail(User user) {
        log.info("[EMAIL STUB] Approval email would be sent to: {}", user.getEmail());
    }

    public void sendRejectionEmail(User user) {
        log.info("[EMAIL STUB] Rejection email would be sent to: {}", user.getEmail());
    }

    public void sendPredictionReminder(User user, String matchInfo) {
        log.info("[EMAIL STUB] Reminder would be sent to {} for match: {}", user.getEmail(), matchInfo);
    }
}

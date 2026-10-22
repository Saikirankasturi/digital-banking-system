package com.digitalbanking.notification.service;

import com.digitalbanking.notification.model.NotificationRecord;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
@Service
public class NotificationService {

    private final List<NotificationRecord> notificationHistory = new CopyOnWriteArrayList<>();
    private static final int MAX_HISTORY = 100;

    public void sendNotification(String recipient, String channel, String priority, String title, String message) {
        NotificationRecord record = NotificationRecord.builder()
                .id(UUID.randomUUID().toString())
                .recipient(recipient)
                .channel(channel)
                .priority(priority)
                .title(title)
                .message(message)
                .timestamp(Instant.now())
                .build();

        if (notificationHistory.size() >= MAX_HISTORY) {
            notificationHistory.remove(0);
        }
        notificationHistory.add(record);

        // Simulated Dispatch
        log.info("""
                ======================= [NOTIFICATION DISPATCHED] =======================
                To:        {} (Channel: {})
                Priority:  {}
                Subject:   {}
                Body:      {}
                ========================================================================
                """, recipient, channel, priority, title, message);
    }

    public List<NotificationRecord> getRecentNotifications() {
        return new ArrayList<>(notificationHistory);
    }
}

package com.digitalbanking.notification.controller;

import com.digitalbanking.common.dto.ApiResponse;
import com.digitalbanking.notification.model.NotificationRecord;
import com.digitalbanking.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<NotificationRecord>>> getRecentNotifications() {
        List<NotificationRecord> notifications = notificationService.getRecentNotifications();
        return ResponseEntity.ok(ApiResponse.success(notifications, "Retrieved recent notifications"));
    }
}

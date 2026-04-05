package com.oct.invoicesystem.domain.notification.service;

import com.oct.invoicesystem.domain.notification.dto.NotificationDTO;
import com.oct.invoicesystem.domain.notification.model.Notification;
import com.oct.invoicesystem.domain.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;

    /**
     * Get paginated notifications for a user.
     */
    @Transactional(readOnly = true)
    public Page<NotificationDTO> getMyNotifications(UUID userId, Pageable pageable) {
        return notificationRepository.findByUserId(userId, pageable)
                .map(this::toDTO);
    }

    /**
     * Count unread notifications for a user.
     */
    @Transactional(readOnly = true)
    public long countUnread(UUID userId) {
        return notificationRepository.countUnreadByUserId(userId);
    }

    /**
     * Mark a specific notification as read.
     */
    @Transactional
    public void markAsRead(UUID id, UUID userId) {
        notificationRepository.findById(id).ifPresent(notification -> {
            if (notification.getUser().getId().equals(userId) && !notification.isRead()) {
                notification.setRead(true);
                notification.setReadAt(Instant.now());
                notificationRepository.save(notification);
            }
        });
    }

    /**
     * Mark all notifications as read for a user.
     */
    @Transactional
    public void markAllAsRead(UUID userId) {
        notificationRepository.markAllAsReadByUserId(userId, Instant.now());
    }

    private NotificationDTO toDTO(Notification n) {
        return new NotificationDTO(
                n.getId(),
                n.getInvoice() != null ? n.getInvoice().getId() : null,
                n.getTitleFr(),
                n.getTitleEn(),
                n.getMessageFr(),
                n.getMessageEn(),
                n.getType().name(),
                n.isRead(),
                n.getReadAt(),
                n.getCreatedAt()
        );
    }
}

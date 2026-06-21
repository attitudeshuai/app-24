package com.electricitysplit.controller;

import com.electricitysplit.dto.ApiResponse;
import com.electricitysplit.dto.NotificationDto;
import com.electricitysplit.dto.PageResponse;
import com.electricitysplit.entity.Notification;
import com.electricitysplit.entity.User;
import com.electricitysplit.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@Tag(name = "通知管理", description = "用户通知的查询和管理接口")
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    @Operation(summary = "分页获取用户通知列表")
    public ResponseEntity<ApiResponse<PageResponse<NotificationDto.Response>>> list(
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        User user = getCurrentUser();
        Page<Notification> page = notificationService.getUserNotifications(user, pageable);
        List<NotificationDto.Response> content = page.getContent().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
        PageResponse<NotificationDto.Response> pageResponse = PageResponse.<NotificationDto.Response>builder()
                .content(content)
                .pageNumber(page.getNumber())
                .pageSize(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .first(page.isFirst())
                .last(page.isLast())
                .build();
        return ResponseEntity.ok(ApiResponse.success(pageResponse));
    }

    @GetMapping("/unread")
    @Operation(summary = "获取用户未读通知列表")
    public ResponseEntity<ApiResponse<List<NotificationDto.Response>>> getUnread() {
        User user = getCurrentUser();
        List<Notification> notifications = notificationService.getUnreadNotifications(user);
        List<NotificationDto.Response> responses = notifications.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success(responses));
    }

    @GetMapping("/unread/count")
    @Operation(summary = "获取用户未读通知数量")
    public ResponseEntity<ApiResponse<NotificationDto.UnreadCountResponse>> getUnreadCount() {
        User user = getCurrentUser();
        long count = notificationService.getUnreadCount(user);
        NotificationDto.UnreadCountResponse response = NotificationDto.UnreadCountResponse.builder()
                .count(count)
                .build();
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PutMapping("/{id}/read")
    @Operation(summary = "标记单条通知为已读")
    public ResponseEntity<ApiResponse<Void>> markAsRead(
            @PathVariable Long id) {
        User user = getCurrentUser();
        notificationService.markAsRead(user, id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PutMapping("/read-all")
    @Operation(summary = "标记所有通知为已读")
    public ResponseEntity<ApiResponse<Void>> markAllAsRead() {
        User user = getCurrentUser();
        notificationService.markAllAsRead(user);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    private User getCurrentUser() {
        return (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }

    private NotificationDto.Response toResponse(Notification notification) {
        return NotificationDto.Response.builder()
                .id(notification.getId())
                .userId(notification.getUser().getId())
                .billId(notification.getBill() != null ? notification.getBill().getId() : null)
                .billItemId(notification.getBillItem() != null ? notification.getBillItem().getId() : null)
                .type(notification.getType())
                .title(notification.getTitle())
                .content(notification.getContent())
                .isRead(notification.getIsRead())
                .createdAt(notification.getCreatedAt())
                .build();
    }
}

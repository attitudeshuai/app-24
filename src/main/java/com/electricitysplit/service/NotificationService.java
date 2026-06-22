package com.electricitysplit.service;

import com.electricitysplit.entity.Bill;
import com.electricitysplit.entity.BillItem;
import com.electricitysplit.entity.Notification;
import com.electricitysplit.entity.User;
import com.electricitysplit.exception.BusinessException;
import com.electricitysplit.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy年MM月dd日");

    @Transactional
    public void notifyBillCreated(Bill bill, List<BillItem> items) {
        for (BillItem item : items) {
            User occupant = item.getRoom().getOccupant();
            if (occupant != null) {
                String title = buildBillTitle(bill);
                String content = buildBillContent(bill, item);
                
                Notification notification = Notification.builder()
                        .user(occupant)
                        .bill(bill)
                        .billItem(item)
                        .type(Notification.NotificationType.BILL_CREATED)
                        .title(title)
                        .content(content)
                        .isRead(false)
                        .build();
                
                notificationRepository.save(notification);
            }
        }
    }

    @Transactional
    public void notifyBillReminder(Bill bill, List<BillItem> items) {
        for (BillItem item : items) {
            if (Boolean.FALSE.equals(item.getIsPaid())) {
                User occupant = item.getRoom().getOccupant();
                if (occupant != null) {
                    String title = "缴费提醒：" + buildBillTitle(bill);
                    String content = "您有一笔待缴电费账单，请及时缴纳。\n" + buildBillContent(bill, item);
                    
                    Notification notification = Notification.builder()
                            .user(occupant)
                            .bill(bill)
                            .billItem(item)
                            .type(Notification.NotificationType.BILL_REMINDER)
                            .title(title)
                            .content(content)
                            .isRead(false)
                            .build();
                    
                    notificationRepository.save(notification);
                }
            }
        }
    }

    @Transactional
    public void notifyBillPaid(BillItem item) {
        User creator = item.getBill().getHousehold().getCreatedBy();
        if (creator != null) {
            String title = String.format("【%s】已缴纳电费", item.getRoom().getName());
            String content = String.format("%s 已缴纳电费 %.2f 元。", 
                    item.getRoom().getName(), item.getTotalDue());
            
            Notification notification = Notification.builder()
                    .user(creator)
                    .bill(item.getBill())
                    .billItem(item)
                    .type(Notification.NotificationType.BILL_PAID)
                    .title(title)
                    .content(content)
                    .isRead(false)
                    .build();
            
            notificationRepository.save(notification);
        }
    }

    @Transactional
    public void notifyBillOverdue(Bill bill, List<BillItem> items) {
        for (BillItem item : items) {
            if (Boolean.FALSE.equals(item.getIsPaid())) {
                User occupant = item.getRoom().getOccupant();
                if (occupant != null) {
                    String title = "账单已逾期：" + buildBillTitle(bill);
                    String content = "您有一笔电费账单已逾期，请尽快缴纳，避免产生额外影响。\n" + buildBillContent(bill, item);

                    Notification notification = Notification.builder()
                            .user(occupant)
                            .bill(bill)
                            .billItem(item)
                            .type(Notification.NotificationType.BILL_OVERDUE)
                            .title(title)
                            .content(content)
                            .isRead(false)
                            .build();

                    notificationRepository.save(notification);
                }
            }
        }
    }

    @Transactional
    public void notifyAdmin(Bill bill, String title, String content) {
        User admin = bill.getHousehold().getCreatedBy();
        if (admin != null) {
            Notification notification = Notification.builder()
                    .user(admin)
                    .bill(bill)
                    .type(Notification.NotificationType.SYSTEM)
                    .title(title)
                    .content(content)
                    .isRead(false)
                    .build();
            notificationRepository.save(notification);
        }
    }

    @Transactional
    public void notifyReconciliationDiscrepancy(User user, String title, String content) {
        if (user != null) {
            Notification notification = Notification.builder()
                    .user(user)
                    .type(Notification.NotificationType.RECONCILIATION_DISCREPANCY)
                    .title(title)
                    .content(content)
                    .isRead(false)
                    .build();
            notificationRepository.save(notification);
        }
    }

    public Page<Notification> getUserNotifications(User user, Pageable pageable) {
        return notificationRepository.findByUserId(user.getId(), pageable);
    }

    public List<Notification> getUnreadNotifications(User user) {
        return notificationRepository.findByUserIdAndIsReadFalse(user.getId());
    }

    public long getUnreadCount(User user) {
        return notificationRepository.countByUserIdAndIsReadFalse(user.getId());
    }

    @Transactional
    public void markAsRead(User user, Long notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new BusinessException("通知不存在"));
        
        if (!notification.getUser().getId().equals(user.getId())) {
            throw new BusinessException("无权限操作此通知");
        }
        
        notification.setIsRead(true);
        notificationRepository.save(notification);
    }

    @Transactional
    public void markAllAsRead(User user) {
        List<Notification> unread = notificationRepository.findByUserIdAndIsReadFalse(user.getId());
        for (Notification notification : unread) {
            notification.setIsRead(true);
        }
        notificationRepository.saveAll(unread);
    }

    private String buildBillTitle(Bill bill) {
        return String.format("%s 电费账单", 
                bill.getPeriodStart().format(DATE_FORMATTER));
    }

    private String buildBillContent(Bill bill, BillItem item) {
        StringBuilder sb = new StringBuilder();
        sb.append("账单期间：").append(bill.getPeriodStart().format(DATE_FORMATTER))
          .append(" 至 ").append(bill.getPeriodEnd().format(DATE_FORMATTER)).append("\n");
        sb.append("房间：").append(item.getRoom().getName()).append("\n");
        sb.append("基础电费：").append(formatAmount(item.getBaseShare())).append("元\n");
        sb.append("空调电费：").append(formatAmount(item.getAcShare())).append("元\n");
        sb.append("公共分摊：").append(formatAmount(item.getPublicShare())).append("元\n");
        sb.append("应付金额：").append(formatAmount(item.getTotalDue())).append("元\n");
        
        if (Boolean.TRUE.equals(item.getHasRoundingAdjustment())) {
            sb.append("备注：含尾差调整 ").append(formatAmount(item.getRoundingAdjustmentAmount())).append("元\n");
        }
        
        return sb.toString();
    }

    private String formatAmount(BigDecimal amount) {
        return String.format("%.2f", amount);
    }
}

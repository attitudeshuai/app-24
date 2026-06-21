package com.electricitysplit.service;

import com.electricitysplit.dto.BillItemDto;
import com.electricitysplit.entity.Bill;
import com.electricitysplit.entity.BillItem;
import com.electricitysplit.entity.Room;
import com.electricitysplit.entity.User;
import com.electricitysplit.exception.BusinessException;
import com.electricitysplit.repository.BillItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BillItemService {

    private final BillItemRepository billItemRepository;
    private final BillService billService;
    private final RoomService roomService;

    @Transactional
    public BillItemDto.Response create(User user, BillItemDto.CreateRequest request) {
        Bill bill = billService.getEntityByIdAndCheckPermission(user, request.getBillId());
        Room room = roomService.getEntityByIdAndCheckPermission(user, request.getRoomId());

        BigDecimal totalDue = request.getBaseShare()
                .add(request.getAcShare())
                .add(request.getPublicShare());

        Boolean isPaid = request.getIsPaid() != null ? request.getIsPaid() : false;
        LocalDateTime paidAt = Boolean.TRUE.equals(isPaid) ? LocalDateTime.now() : null;

        BillItem item = BillItem.builder()
                .bill(bill)
                .room(room)
                .baseShare(request.getBaseShare())
                .acShare(request.getAcShare())
                .publicShare(request.getPublicShare())
                .totalDue(totalDue)
                .isPaid(isPaid)
                .paidAt(paidAt)
                .build();

        BillItem saved = billItemRepository.save(item);
        return toResponse(saved);
    }

    public BillItemDto.Response getById(User user, Long id) {
        BillItem item = billItemRepository.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new BusinessException("账单明细不存在或无权限访问"));
        return toResponse(item);
    }

    public BillItem getEntityByIdAndCheckPermission(User user, Long id) {
        return billItemRepository.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new BusinessException("账单明细不存在或无权限访问"));
    }

    public Page<BillItemDto.Response> list(User user, Long billId, Long roomId, Pageable pageable) {
        if (billId == null && roomId == null) {
            throw new BusinessException("必须指定账单ID或房间ID");
        }

        Page<BillItem> page;

        if (billId != null) {
            billService.getEntityByIdAndCheckPermission(user, billId);
            page = billItemRepository.findByBillId(billId, pageable);
        } else {
            roomService.getEntityByIdAndCheckPermission(user, roomId);
            List<BillItem> items = billItemRepository.findByRoomId(roomId);
            page = new org.springframework.data.domain.PageImpl<>(items, pageable, items.size());
        }

        return page.map(this::toResponse);
    }

    @Transactional
    public BillItemDto.Response update(User user, Long id, BillItemDto.UpdateRequest request) {
        BillItem item = getEntityByIdAndCheckPermission(user, id);

        if (request.getBaseShare() != null) {
            item.setBaseShare(request.getBaseShare());
        }
        if (request.getAcShare() != null) {
            item.setAcShare(request.getAcShare());
        }
        if (request.getPublicShare() != null) {
            item.setPublicShare(request.getPublicShare());
        }
        if (request.getBaseShare() != null || request.getAcShare() != null || request.getPublicShare() != null) {
            BigDecimal totalDue = item.getBaseShare()
                    .add(item.getAcShare())
                    .add(item.getPublicShare());
            item.setTotalDue(totalDue);
        }
        if (request.getIsPaid() != null) {
            item.setIsPaid(request.getIsPaid());
            if (Boolean.TRUE.equals(request.getIsPaid()) && item.getPaidAt() == null) {
                item.setPaidAt(LocalDateTime.now());
            } else if (!Boolean.TRUE.equals(request.getIsPaid())) {
                item.setPaidAt(null);
            }
        }

        BillItem saved = billItemRepository.save(item);
        return toResponse(saved);
    }

    @Transactional
    public void delete(User user, Long id) {
        BillItem item = getEntityByIdAndCheckPermission(user, id);
        billItemRepository.delete(item);
    }

    private BillItemDto.Response toResponse(BillItem item) {
        return BillItemDto.Response.builder()
                .id(item.getId())
                .billId(item.getBill().getId())
                .roomId(item.getRoom().getId())
                .roomName(item.getRoom().getName())
                .baseShare(item.getBaseShare())
                .acShare(item.getAcShare())
                .publicShare(item.getPublicShare())
                .totalDue(item.getTotalDue())
                .isPaid(item.getIsPaid())
                .paidAt(item.getPaidAt())
                .build();
    }
}

package com.electricitysplit.service;

import com.electricitysplit.dto.BillDto;
import com.electricitysplit.dto.BillItemDto;
import com.electricitysplit.entity.Bill;
import com.electricitysplit.entity.BillItem;
import com.electricitysplit.entity.Household;
import com.electricitysplit.entity.User;
import com.electricitysplit.exception.BusinessException;
import com.electricitysplit.repository.BillItemRepository;
import com.electricitysplit.repository.BillRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BillService {

    private final BillRepository billRepository;
    private final BillItemRepository billItemRepository;
    private final HouseholdService householdService;

    @Transactional
    public BillDto.Response create(User user, BillDto.CreateRequest request) {
        Household household = householdService.getEntityByIdAndCheckPermission(user, request.getHouseholdId());

        Bill.BillStatus status = request.getStatus() != null ? request.getStatus() : Bill.BillStatus.Draft;

        Bill bill = Bill.builder()
                .household(household)
                .periodStart(request.getPeriodStart())
                .periodEnd(request.getPeriodEnd())
                .totalAmount(request.getTotalAmount())
                .status(status)
                .build();

        Bill saved = billRepository.save(bill);
        return toResponse(saved);
    }

    public BillDto.Response getById(User user, Long id) {
        Bill bill = billRepository.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new BusinessException("账单不存在或无权限访问"));
        return toResponse(bill);
    }

    public Bill getEntityByIdAndCheckPermission(User user, Long id) {
        return billRepository.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new BusinessException("账单不存在或无权限访问"));
    }

    public Page<BillDto.Response> list(User user, Long householdId, Bill.BillStatus status, LocalDate startDate, LocalDate endDate, Pageable pageable) {
        if (householdId == null) {
            throw new BusinessException("必须指定住户ID");
        }
        householdService.getEntityByIdAndCheckPermission(user, householdId);

        Page<Bill> page;

        if (status != null) {
            page = billRepository.findByHouseholdIdAndStatus(householdId, status, pageable);
        } else if (startDate != null && endDate != null) {
            page = billRepository.findByHouseholdIdAndDateRange(householdId, startDate, endDate, pageable);
        } else {
            page = billRepository.findByHouseholdId(householdId, pageable);
        }

        return page.map(this::toResponse);
    }

    @Transactional
    public BillDto.Response update(User user, Long id, BillDto.UpdateRequest request) {
        Bill bill = getEntityByIdAndCheckPermission(user, id);

        if (request.getPeriodStart() != null) {
            bill.setPeriodStart(request.getPeriodStart());
        }
        if (request.getPeriodEnd() != null) {
            bill.setPeriodEnd(request.getPeriodEnd());
        }
        if (request.getTotalAmount() != null) {
            bill.setTotalAmount(request.getTotalAmount());
        }
        if (request.getStatus() != null) {
            bill.setStatus(request.getStatus());
        }

        Bill saved = billRepository.save(bill);
        return toResponse(saved);
    }

    @Transactional
    public BillDto.Response updateStatus(User user, Long id, Bill.BillStatus status) {
        Bill bill = getEntityByIdAndCheckPermission(user, id);
        bill.setStatus(status);
        Bill saved = billRepository.save(bill);
        return toResponse(saved);
    }

    @Transactional
    public void delete(User user, Long id) {
        Bill bill = getEntityByIdAndCheckPermission(user, id);
        List<BillItem> items = billItemRepository.findByBillId(id);
        billItemRepository.deleteAll(items);
        billRepository.delete(bill);
    }

    private BillDto.Response toResponse(Bill bill) {
        List<BillItem> items = billItemRepository.findByBillId(bill.getId());
        List<BillItemDto.Response> itemResponses = items.stream()
                .map(this::toBillItemResponse)
                .collect(Collectors.toList());

        return BillDto.Response.builder()
                .id(bill.getId())
                .householdId(bill.getHousehold().getId())
                .householdName(bill.getHousehold().getName())
                .periodStart(bill.getPeriodStart())
                .periodEnd(bill.getPeriodEnd())
                .totalAmount(bill.getTotalAmount())
                .status(bill.getStatus())
                .createdAt(bill.getCreatedAt())
                .items(itemResponses)
                .build();
    }

    private BillItemDto.Response toBillItemResponse(BillItem item) {
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

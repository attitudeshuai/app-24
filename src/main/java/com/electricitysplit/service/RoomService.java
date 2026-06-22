package com.electricitysplit.service;

import com.electricitysplit.dto.RoomDto;
import com.electricitysplit.entity.*;
import com.electricitysplit.exception.BusinessException;
import com.electricitysplit.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RoomService {

    private final RoomRepository roomRepository;
    private final HouseholdService householdService;
    private final UserRepository userRepository;
    private final BillRepository billRepository;
    private final BillItemRepository billItemRepository;
    private final PaymentRecordRepository paymentRecordRepository;
    private final RoomMeterReadingRepository roomMeterReadingRepository;
    private final MeterReadingRepository meterReadingRepository;

    @Transactional
    public RoomDto.Response create(User user, RoomDto.CreateRequest request) {
        Household household = householdService.getEntityByIdAndCheckPermission(user, request.getHouseholdId());

        User occupant = null;
        if (request.getOccupantId() != null) {
            occupant = userRepository.findById(request.getOccupantId())
                    .orElseThrow(() -> new BusinessException("住户用户不存在"));
        }

        Room room = Room.builder()
                .household(household)
                .name(request.getName())
                .area(request.getArea())
                .occupant(occupant)
                .hasAirConditioner(request.getHasAirConditioner())
                .headCount(request.getHeadCount())
                .isDeleted(false)
                .build();

        Room saved = roomRepository.save(room);
        return toResponse(saved);
    }

    public RoomDto.Response getById(User user, Long id) {
        Room room = roomRepository.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new BusinessException("房间不存在或无权限访问"));
        return toResponse(room);
    }

    public Room getEntityByIdAndCheckPermission(User user, Long id) {
        return roomRepository.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new BusinessException("房间不存在或无权限访问"));
    }

    public Page<RoomDto.Response> list(User user, Long householdId, String keyword, Pageable pageable) {
        if (householdId != null) {
            householdService.getEntityByIdAndCheckPermission(user, householdId);
        } else {
            throw new BusinessException("必须指定住户ID");
        }

        Page<Room> page;

        if (keyword != null && !keyword.isBlank()) {
            page = roomRepository.findByHouseholdIdAndKeyword(householdId, keyword, pageable);
        } else {
            page = roomRepository.findByHouseholdId(householdId, pageable);
        }

        return page.map(this::toResponse);
    }

    @Transactional
    public RoomDto.Response update(User user, Long id, RoomDto.UpdateRequest request) {
        Room room = getEntityByIdAndCheckPermission(user, id);

        if (request.getName() != null) {
            room.setName(request.getName());
        }
        if (request.getArea() != null) {
            room.setArea(request.getArea());
        }
        if (request.getOccupantId() != null) {
            if (request.getOccupantId() == 0) {
                room.setOccupant(null);
            } else {
                User occupant = userRepository.findById(request.getOccupantId())
                        .orElseThrow(() -> new BusinessException("住户用户不存在"));
                room.setOccupant(occupant);
            }
        }
        if (request.getHasAirConditioner() != null) {
            room.setHasAirConditioner(request.getHasAirConditioner());
        }
        if (request.getHeadCount() != null) {
            room.setHeadCount(request.getHeadCount());
        }

        Room saved = roomRepository.save(room);
        return toResponse(saved);
    }

    public RoomDto.DeleteCheckResult checkDeleteConditions(User user, Long id) {
        Room room = getEntityByIdAndCheckPermission(user, id);

        List<RoomDto.ConflictItem> conflicts = new ArrayList<>();

        long unpaidBillCount = billRepository.countUnpaidBillsByRoomId(room.getId(), BillStatus.PAID, user.getId());
        if (unpaidBillCount > 0) {
            conflicts.add(RoomDto.ConflictItem.builder()
                    .type("UNPAID_BILLS")
                    .description("存在未结清账单")
                    .count(unpaidBillCount)
                    .build());
        }

        List<RoomMeterReading> roomMeterReadings = roomMeterReadingRepository.findByRoomId(room.getId());
        if (!roomMeterReadings.isEmpty()) {
            conflicts.add(RoomDto.ConflictItem.builder()
                    .type("METER_READINGS")
                    .description("存在电表读数记录")
                    .count((long) roomMeterReadings.size())
                    .build());
        }

        boolean canDelete = conflicts.isEmpty();
        String message = canDelete ? "可以安全删除" : "存在删除冲突，请先处理相关数据";

        return RoomDto.DeleteCheckResult.builder()
                .canDelete(canDelete)
                .conflicts(conflicts)
                .message(message)
                .build();
    }

    @Transactional
    public void delete(User user, Long id, String reason) {
        RoomDto.DeleteCheckResult checkResult = checkDeleteConditions(user, id);
        if (!checkResult.isCanDelete()) {
            StringBuilder message = new StringBuilder("无法删除房间：");
            for (RoomDto.ConflictItem conflict : checkResult.getConflicts()) {
                message.append(conflict.getDescription()).append("（").append(conflict.getCount()).append("条）；");
            }
            message.append("请先处理相关数据或导出历史数据后再删除。");
            throw new BusinessException(409, message.toString());
        }

        Room room = getEntityByIdAndCheckPermission(user, id);
        room.setIsDeleted(true);
        room.setDeletedAt(LocalDateTime.now());
        room.setDeletedBy(user);
        room.setDeleteReason(reason);
        roomRepository.save(room);
    }

    @Transactional
    public void forceDelete(User user, Long id, String reason) {
        Room room = getEntityByIdAndCheckPermission(user, id);
        room.setIsDeleted(true);
        room.setDeletedAt(LocalDateTime.now());
        room.setDeletedBy(user);
        room.setDeleteReason(reason);
        roomRepository.save(room);
    }

    public RoomDto.RoomDataExport exportRoomData(User user, Long id) {
        Room room = roomRepository.findByIdAndUserIdIncludeDeleted(id, user.getId())
                .orElseThrow(() -> new BusinessException("房间不存在或无权限访问"));

        RoomDto.RoomBasicInfo roomBasicInfo = RoomDto.RoomBasicInfo.builder()
                .id(room.getId())
                .name(room.getName())
                .area(room.getArea())
                .hasAirConditioner(room.getHasAirConditioner())
                .headCount(room.getHeadCount())
                .createdAt(room.getCreatedAt())
                .householdName(room.getHousehold().getName())
                .build();

        List<RoomMeterReading> roomMeterReadings = roomMeterReadingRepository.findByRoomId(room.getId());
        List<RoomDto.MeterReadingExport> meterReadingExports = new ArrayList<>();
        for (RoomMeterReading rmr : roomMeterReadings) {
            MeterReading mr = rmr.getMeterReading();
            meterReadingExports.add(RoomDto.MeterReadingExport.builder()
                    .id(rmr.getId())
                    .readingDate(mr.getReadingDate())
                    .previousReading(rmr.getPreviousReading())
                    .currentReading(rmr.getCurrentReading())
                    .usageKwh(rmr.getUsageKwh())
                    .isAcUsage(rmr.getIsAcUsage())
                    .totalKwh(mr.getTotalKwh())
                    .amount(mr.getAmount())
                    .createdAt(rmr.getCreatedAt())
                    .build());
        }

        List<Bill> bills = billRepository.findAllBillsByRoomId(room.getId(), user.getId());
        List<RoomDto.BillExport> billExports = bills.stream()
                .map(bill -> RoomDto.BillExport.builder()
                        .id(bill.getId())
                        .periodStart(bill.getPeriodStart())
                        .periodEnd(bill.getPeriodEnd())
                        .totalAmount(bill.getTotalAmount())
                        .status(bill.getStatus().name())
                        .dueDate(bill.getDueDate())
                        .createdAt(bill.getCreatedAt())
                        .build())
                .collect(Collectors.toList());

        List<BillItem> billItems = billItemRepository.findByRoomId(room.getId());
        List<RoomDto.BillItemExport> billItemExports = billItems.stream()
                .map(bi -> RoomDto.BillItemExport.builder()
                        .id(bi.getId())
                        .billId(bi.getBill().getId())
                        .baseShare(bi.getBaseShare())
                        .acShare(bi.getAcShare())
                        .publicShare(bi.getPublicShare())
                        .totalDue(bi.getTotalDue())
                        .isPaid(bi.getIsPaid())
                        .paidAt(bi.getPaidAt())
                        .isPublicArea(bi.getIsPublicArea())
                        .allocationType(bi.getAllocationType())
                        .calculationDetails(bi.getCalculationDetails())
                        .build())
                .collect(Collectors.toList());

        List<PaymentRecord> paymentRecords = paymentRecordRepository.findByRoomId(room.getId(), user.getId());
        List<RoomDto.PaymentRecordExport> paymentRecordExports = paymentRecords.stream()
                .map(pr -> RoomDto.PaymentRecordExport.builder()
                        .id(pr.getId())
                        .billId(pr.getBill().getId())
                        .billItemId(pr.getBillItem() != null ? pr.getBillItem().getId() : null)
                        .paymentAmount(pr.getPaymentAmount())
                        .paymentMethod(pr.getPaymentMethod().name())
                        .transactionNo(pr.getTransactionNo())
                        .paymentTime(pr.getPaymentTime())
                        .operatorName(pr.getOperatorName())
                        .paymentType(pr.getPaymentType().name())
                        .status(pr.getStatus().name())
                        .remark(pr.getRemark())
                        .createdAt(pr.getCreatedAt())
                        .build())
                .collect(Collectors.toList());

        BigDecimal totalBillAmount = billItems.stream()
                .map(BillItem::getTotalDue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalPaymentAmount = paymentRecords.stream()
                .map(PaymentRecord::getPaymentAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        RoomDto.ExportSummary summary = RoomDto.ExportSummary.builder()
                .meterReadingCount(meterReadingExports.size())
                .billCount(billExports.size())
                .billItemCount(billItemExports.size())
                .paymentRecordCount(paymentRecordExports.size())
                .totalBillAmount(totalBillAmount)
                .totalPaymentAmount(totalPaymentAmount)
                .exportTime(LocalDateTime.now())
                .build();

        return RoomDto.RoomDataExport.builder()
                .room(roomBasicInfo)
                .meterReadings(meterReadingExports)
                .bills(billExports)
                .billItems(billItemExports)
                .paymentRecords(paymentRecordExports)
                .summary(summary)
                .build();
    }

    private RoomDto.Response toResponse(Room room) {
        return RoomDto.Response.builder()
                .id(room.getId())
                .householdId(room.getHousehold().getId())
                .householdName(room.getHousehold().getName())
                .name(room.getName())
                .area(room.getArea())
                .occupantId(room.getOccupant() != null ? room.getOccupant().getId() : null)
                .occupantUsername(room.getOccupant() != null ? room.getOccupant().getUsername() : null)
                .hasAirConditioner(room.getHasAirConditioner())
                .headCount(room.getHeadCount())
                .createdAt(room.getCreatedAt())
                .isDeleted(room.getIsDeleted())
                .deletedAt(room.getDeletedAt())
                .deletedBy(room.getDeletedBy() != null ? room.getDeletedBy().getId() : null)
                .deletedByUsername(room.getDeletedBy() != null ? room.getDeletedBy().getUsername() : null)
                .deleteReason(room.getDeleteReason())
                .build();
    }
}

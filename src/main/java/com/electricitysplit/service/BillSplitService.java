package com.electricitysplit.service;

import com.electricitysplit.entity.*;
import com.electricitysplit.exception.BusinessException;
import com.electricitysplit.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BillSplitService {

    private final AllocationRuleRepository allocationRuleRepository;
    private final RoomRepository roomRepository;
    private final RoomCustomRatioRepository roomCustomRatioRepository;
    private final RoomMeterReadingRepository roomMeterReadingRepository;
    private final MeterReadingRepository meterReadingRepository;
    private final BillItemRepository billItemRepository;

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    @Transactional
    public List<BillItem> splitBill(Bill bill, Long meterReadingId) {
        Household household = bill.getHousehold();

        AllocationRule rule = allocationRuleRepository.findActiveByHouseholdId(household.getId())
                .orElseThrow(() -> new BusinessException("未找到有效的分摊规则，请先设置分摊规则"));

        List<Room> rooms = roomRepository.findByHouseholdId(household.getId());
        if (rooms.isEmpty()) {
            throw new BusinessException("该住户下没有房间，无法进行分摊");
        }

        MeterReading meterReading = null;
        Map<Long, RoomMeterReading> roomMeterReadings = new HashMap<>();

        if (meterReadingId != null) {
            meterReading = meterReadingRepository.findById(meterReadingId)
                    .orElseThrow(() -> new BusinessException("电表读数记录不存在"));
            if (!meterReading.getHousehold().getId().equals(household.getId())) {
                throw new BusinessException("电表读数不属于该住户");
            }
            List<RoomMeterReading> readings = roomMeterReadingRepository.findByMeterReadingId(meterReadingId);
            roomMeterReadings = readings.stream()
                    .collect(Collectors.toMap(r -> r.getRoom().getId(), r -> r));
        }

        BigDecimal totalAmount = bill.getTotalAmount();
        BigDecimal publicAmount = BigDecimal.ZERO;
        BigDecimal remainingAmount = totalAmount;

        if (rule.getPublicRatio() != null && rule.getPublicRatio().compareTo(BigDecimal.ZERO) > 0) {
            publicAmount = totalAmount.multiply(rule.getPublicRatio())
                    .setScale(2, RoundingMode.HALF_UP);
            remainingAmount = totalAmount.subtract(publicAmount);
        }

        Map<Long, BigDecimal> baseShares = calculateShares(rooms, rule.getBaseAllocationType(), 
                remainingAmount, rule, AllocationCategory.BASE, roomMeterReadings);
        Map<Long, BigDecimal> acShares = calculateShares(rooms, rule.getAcAllocationType(), 
                BigDecimal.ZERO, rule, AllocationCategory.AC, roomMeterReadings);
        Map<Long, BigDecimal> publicShares = calculatePublicShares(rooms, rule.getPublicAllocationType(), 
                publicAmount, rule);

        List<BillItem> items = new ArrayList<>();
        BigDecimal totalCalculated = BigDecimal.ZERO;

        for (Room room : rooms) {
            BigDecimal baseShare = baseShares.getOrDefault(room.getId(), BigDecimal.ZERO);
            BigDecimal acShare = acShares.getOrDefault(room.getId(), BigDecimal.ZERO);
            BigDecimal publicShare = publicShares.getOrDefault(room.getId(), BigDecimal.ZERO);
            BigDecimal totalDue = baseShare.add(acShare).add(publicShare);

            String calculationDetails = buildCalculationDetails(room, rule, baseShare, acShare, 
                    publicShare, totalDue, roomMeterReadings.get(room.getId()));

            BillItem item = BillItem.builder()
                    .bill(bill)
                    .room(room)
                    .baseShare(baseShare)
                    .acShare(acShare)
                    .publicShare(publicShare)
                    .totalDue(totalDue)
                    .isPaid(false)
                    .isPublicArea(false)
                    .allocationType(rule.getBaseAllocationType().name())
                    .calculationDetails(calculationDetails)
                    .hasRoundingAdjustment(false)
                    .roundingAdjustmentAmount(BigDecimal.ZERO)
                    .build();

            items.add(item);
            totalCalculated = totalCalculated.add(totalDue);
        }

        handleRoundingDifference(items, totalAmount, totalCalculated, rooms);

        validateSplit(items, totalAmount);

        return billItemRepository.saveAll(items);
    }

    private Map<Long, BigDecimal> calculateShares(List<Room> rooms, AllocationType type, 
            BigDecimal totalAmount, AllocationRule rule, AllocationCategory category,
            Map<Long, RoomMeterReading> roomMeterReadings) {
        Map<Long, BigDecimal> shares = new HashMap<>();

        if (totalAmount.compareTo(BigDecimal.ZERO) == 0 && type != AllocationType.CUSTOM_RATIO) {
            for (Room room : rooms) {
                shares.put(room.getId(), BigDecimal.ZERO);
            }
            return shares;
        }

        switch (type) {
            case PER_PERSON:
                shares = calculateByPerson(rooms, totalAmount, category);
                break;
            case PER_AREA:
                shares = calculateByArea(rooms, totalAmount, category);
                break;
            case EQUAL:
                shares = calculateEqual(rooms, totalAmount);
                break;
            case CUSTOM_RATIO:
                shares = calculateByCustomRatio(rooms, totalAmount, rule, category);
                break;
            default:
                throw new BusinessException("不支持的分摊类型: " + type);
        }

        return shares;
    }

    private Map<Long, BigDecimal> calculateByPerson(List<Room> rooms, BigDecimal totalAmount, 
            AllocationCategory category) {
        Map<Long, BigDecimal> shares = new HashMap<>();
        int totalHeadCount = rooms.stream()
                .filter(r -> shouldAllocate(r, category))
                .mapToInt(r -> r.getHeadCount() != null ? r.getHeadCount() : 1)
                .sum();

        if (totalHeadCount == 0) {
            totalHeadCount = rooms.size();
        }

        for (Room room : rooms) {
            if (!shouldAllocate(room, category)) {
                shares.put(room.getId(), BigDecimal.ZERO);
                continue;
            }
            int headCount = room.getHeadCount() != null ? room.getHeadCount() : 1;
            BigDecimal ratio = BigDecimal.valueOf(headCount)
                    .divide(BigDecimal.valueOf(totalHeadCount), 6, RoundingMode.HALF_UP);
            shares.put(room.getId(), totalAmount.multiply(ratio).setScale(2, RoundingMode.HALF_UP));
        }

        return shares;
    }

    private Map<Long, BigDecimal> calculateByArea(List<Room> rooms, BigDecimal totalAmount,
            AllocationCategory category) {
        Map<Long, BigDecimal> shares = new HashMap<>();
        BigDecimal totalArea = rooms.stream()
                .filter(r -> shouldAllocate(r, category))
                .map(r -> r.getArea() != null ? r.getArea() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (totalArea.compareTo(BigDecimal.ZERO) == 0) {
            return calculateEqual(rooms, totalAmount);
        }

        for (Room room : rooms) {
            if (!shouldAllocate(room, category)) {
                shares.put(room.getId(), BigDecimal.ZERO);
                continue;
            }
            BigDecimal area = room.getArea() != null ? room.getArea() : BigDecimal.ZERO;
            BigDecimal ratio = area.divide(totalArea, 6, RoundingMode.HALF_UP);
            shares.put(room.getId(), totalAmount.multiply(ratio).setScale(2, RoundingMode.HALF_UP));
        }

        return shares;
    }

    private Map<Long, BigDecimal> calculateEqual(List<Room> rooms, BigDecimal totalAmount) {
        Map<Long, BigDecimal> shares = new HashMap<>();
        int count = rooms.size();
        if (count == 0) {
            return shares;
        }
        BigDecimal perRoom = totalAmount.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
        for (Room room : rooms) {
            shares.put(room.getId(), perRoom);
        }
        return shares;
    }

    private Map<Long, BigDecimal> calculateByCustomRatio(List<Room> rooms, BigDecimal totalAmount,
            AllocationRule rule, AllocationCategory category) {
        Map<Long, BigDecimal> shares = new HashMap<>();
        List<RoomCustomRatio> customRatios = roomCustomRatioRepository.findByAllocationRuleId(rule.getId());
        Map<Long, RoomCustomRatio> ratioMap = customRatios.stream()
                .collect(Collectors.toMap(r -> r.getRoom().getId(), r -> r));

        BigDecimal totalRatio = BigDecimal.ZERO;
        for (Room room : rooms) {
            RoomCustomRatio ratio = ratioMap.get(room.getId());
            if (ratio != null) {
                BigDecimal r = getRatioByCategory(ratio, category);
                if (r != null) {
                    totalRatio = totalRatio.add(r);
                }
            }
        }

        if (totalRatio.compareTo(BigDecimal.ZERO) == 0) {
            return calculateEqual(rooms, totalAmount);
        }

        for (Room room : rooms) {
            RoomCustomRatio ratio = ratioMap.get(room.getId());
            BigDecimal roomRatio = BigDecimal.ZERO;
            if (ratio != null) {
                roomRatio = getRatioByCategory(ratio, category);
                if (roomRatio == null) {
                    roomRatio = BigDecimal.ZERO;
                }
            }
            BigDecimal allocRatio = roomRatio.divide(totalRatio, 6, RoundingMode.HALF_UP);
            shares.put(room.getId(), totalAmount.multiply(allocRatio).setScale(2, RoundingMode.HALF_UP));
        }

        return shares;
    }

    private BigDecimal getRatioByCategory(RoomCustomRatio ratio, AllocationCategory category) {
        return switch (category) {
            case BASE -> ratio.getBaseRatio();
            case AC -> ratio.getAcRatio();
            case PUBLIC -> ratio.getPublicRatio();
        };
    }

    private Map<Long, BigDecimal> calculatePublicShares(List<Room> rooms, AllocationType type,
            BigDecimal publicAmount, AllocationRule rule) {
        return calculateShares(rooms, type, publicAmount, rule, AllocationCategory.PUBLIC, null);
    }

    private boolean shouldAllocate(Room room, AllocationCategory category) {
        if (category == AllocationCategory.AC) {
            return room.getHasAirConditioner() != null && room.getHasAirConditioner();
        }
        return true;
    }

    private void handleRoundingDifference(List<BillItem> items, BigDecimal totalAmount, 
            BigDecimal totalCalculated, List<Room> rooms) {
        BigDecimal difference = totalAmount.subtract(totalCalculated);
        
        if (difference.compareTo(BigDecimal.ZERO) == 0) {
            return;
        }

        Room targetRoom = rooms.stream()
                .filter(r -> r.getOccupant() != null)
                .findFirst()
                .orElse(rooms.get(0));

        for (BillItem item : items) {
            if (item.getRoom().getId().equals(targetRoom.getId())) {
                item.setTotalDue(item.getTotalDue().add(difference));
                item.setHasRoundingAdjustment(true);
                item.setRoundingAdjustmentAmount(difference);
                item.setCalculationDetails(item.getCalculationDetails() + 
                        String.format("尾差调整: %+.2f元", difference));
                break;
            }
        }
    }

    private void validateSplit(List<BillItem> items, BigDecimal totalAmount) {
        BigDecimal sum = items.stream()
                .map(BillItem::getTotalDue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        if (sum.compareTo(totalAmount) != 0) {
            throw new BusinessException(
                String.format("分摊校验失败：明细总额%.2f元不等于账单总额%.2f元，差额%.2f元", 
                    sum, totalAmount, totalAmount.subtract(sum)));
        }
    }

    private String buildCalculationDetails(Room room, AllocationRule rule, 
            BigDecimal baseShare, BigDecimal acShare, BigDecimal publicShare, 
            BigDecimal totalDue, RoomMeterReading reading) {
        StringBuilder sb = new StringBuilder();
        
        sb.append("【").append(room.getName()).append("】");
        
        if (reading != null) {
            sb.append(String.format("电表读数：上期%.2f度，本期%.2f度，用电%.2f度；",
                    reading.getPreviousReading(), reading.getCurrentReading(), reading.getUsageKwh()));
        }
        
        if (baseShare.compareTo(BigDecimal.ZERO) > 0) {
            sb.append(String.format("基础电费(%.2f)按%s分摊%.2f元；", 
                    baseShare, getAllocationTypeName(rule.getBaseAllocationType()), baseShare));
        }
        
        if (acShare.compareTo(BigDecimal.ZERO) > 0) {
            sb.append(String.format("空调电费按%s分摊%.2f元；",
                    getAllocationTypeName(rule.getAcAllocationType()), acShare));
        }
        
        if (publicShare.compareTo(BigDecimal.ZERO) > 0) {
            sb.append(String.format("公共区域电费按%s分摊%.2f元；",
                    getAllocationTypeName(rule.getPublicAllocationType()), publicShare));
        }
        
        sb.append(String.format("合计应付%.2f元。", totalDue));
        
        return sb.toString();
    }

    private String getAllocationTypeName(AllocationType type) {
        return switch (type) {
            case PER_PERSON -> "人头";
            case PER_AREA -> "面积";
            case EQUAL -> "均摊";
            case CUSTOM_RATIO -> "自定义比例";
        };
    }

    public enum AllocationCategory {
        BASE, AC, PUBLIC
    }
}

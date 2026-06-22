package com.electricitysplit.service;

import com.electricitysplit.config.SchedulerConfig;
import com.electricitysplit.entity.Household;
import com.electricitysplit.entity.MeterReading;
import com.electricitysplit.entity.Room;
import com.electricitysplit.entity.RoomMeterReading;
import com.electricitysplit.repository.MeterReadingRepository;
import com.electricitysplit.repository.RoomMeterReadingRepository;
import com.electricitysplit.repository.RoomRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MeterReadingEstimationService {

    private final MeterReadingRepository meterReadingRepository;
    private final RoomMeterReadingRepository roomMeterReadingRepository;
    private final RoomRepository roomRepository;
    private final SchedulerConfig schedulerConfig;

    public static class EstimationResult {
        private final boolean estimated;
        private final MeterReading meterReading;
        private final Map<Long, RoomMeterReading> roomReadings;
        private final List<String> estimationLogs;
        private final boolean skipped;
        private final String skipReason;

        public EstimationResult(boolean estimated, MeterReading meterReading,
                                Map<Long, RoomMeterReading> roomReadings,
                                List<String> estimationLogs,
                                boolean skipped, String skipReason) {
            this.estimated = estimated;
            this.meterReading = meterReading;
            this.roomReadings = roomReadings;
            this.estimationLogs = estimationLogs;
            this.skipped = skipped;
            this.skipReason = skipReason;
        }

        public boolean isEstimated() { return estimated; }
        public MeterReading getMeterReading() { return meterReading; }
        public Map<Long, RoomMeterReading> getRoomReadings() { return roomReadings; }
        public List<String> getEstimationLogs() { return estimationLogs; }
        public boolean isSkipped() { return skipped; }
        public String getSkipReason() { return skipReason; }
    }

    public EstimationResult getOrEstimateMeterReading(Household household,
                                                       YearMonth billingPeriod,
                                                       LocalDate periodStart,
                                                       LocalDate periodEnd) {
        List<String> logs = new ArrayList<>();

        Optional<MeterReading> existingReading = findMeterReadingForPeriod(
                household.getId(), periodStart, periodEnd);

        if (existingReading.isPresent()) {
            MeterReading reading = existingReading.get();
            Map<Long, RoomMeterReading> roomReadings = getRoomReadings(reading);
            List<Room> activeRooms = roomRepository.findByHouseholdId(household.getId());

            Set<Long> roomsWithReadings = roomReadings.keySet();
            List<Room> roomsWithoutReadings = activeRooms.stream()
                    .filter(r -> !roomsWithReadings.contains(r.getId()))
                    .collect(Collectors.toList());

            if (!roomsWithoutReadings.isEmpty()) {
                if (schedulerConfig.isEstimateMissingReadings()) {
                    logs.add(String.format("电表读数记录已存在（ID: %d），但 %d 个房间缺少读数，开始估算",
                            reading.getId(), roomsWithoutReadings.size()));

                    Map<Long, RoomMeterReading> estimatedRoomReadings = estimateRoomReadings(
                            household, reading, roomsWithoutReadings, billingPeriod, logs);

                    roomReadings.putAll(estimatedRoomReadings);
                    return new EstimationResult(true, reading, roomReadings, logs, false, null);
                } else {
                    String skipReason = String.format("%d 个房间缺少电表读数且未开启估算功能: %s",
                            roomsWithoutReadings.size(),
                            roomsWithoutReadings.stream().map(Room::getName).collect(Collectors.joining(", ")));
                    logs.add(skipReason);
                    return new EstimationResult(false, reading, roomReadings, logs, true, skipReason);
                }
            }

            logs.add(String.format("电表读数记录已存在（ID: %d），所有房间均有读数，无需估算", reading.getId()));
            return new EstimationResult(false, reading, roomReadings, logs, false, null);
        }

        if (!schedulerConfig.isEstimateMissingReadings()) {
            String skipReason = "未找到电表读数记录且未开启估算功能";
            logs.add(skipReason);
            return new EstimationResult(false, null, Collections.emptyMap(), logs, true, skipReason);
        }

        logs.add("未找到电表读数记录，开始估算总用电量和房间读数");

        MeterReading estimatedReading = estimateTotalMeterReading(household, billingPeriod, periodStart, periodEnd, logs);
        if (estimatedReading == null) {
            if (schedulerConfig.isSkipOnEstimationFailure()) {
                String skipReason = "无法估算总用电量，历史数据不足";
                logs.add(skipReason);
                return new EstimationResult(false, null, Collections.emptyMap(), logs, true, skipReason);
            } else {
                throw new IllegalStateException("无法估算总用电量，历史数据不足且未配置跳过");
            }
        }

        List<Room> activeRooms = roomRepository.findByHouseholdId(household.getId());
        Map<Long, RoomMeterReading> estimatedRoomReadings = estimateRoomReadings(
                household, estimatedReading, activeRooms, billingPeriod, logs);

        return new EstimationResult(true, estimatedReading, estimatedRoomReadings, logs, false, null);
    }

    private Optional<MeterReading> findMeterReadingForPeriod(Long householdId,
                                                              LocalDate periodStart,
                                                              LocalDate periodEnd) {
        List<MeterReading> readings = meterReadingRepository.findByHouseholdIdAndDateRange(
                householdId, periodStart, periodEnd);

        if (readings.isEmpty()) {
            return Optional.empty();
        }

        LocalDate midPeriod = periodStart.plusDays(periodEnd.getDayOfMonth() / 2);
        return readings.stream()
                .min(Comparator.comparing(r -> Math.abs(r.getReadingDate().toEpochDay() - midPeriod.toEpochDay())));
    }

    private Map<Long, RoomMeterReading> getRoomReadings(MeterReading meterReading) {
        List<RoomMeterReading> readings = roomMeterReadingRepository.findByMeterReadingId(meterReading.getId());
        return readings.stream()
                .collect(Collectors.toMap(r -> r.getRoom().getId(), r -> r));
    }

    private MeterReading estimateTotalMeterReading(Household household,
                                                    YearMonth billingPeriod,
                                                    LocalDate periodStart,
                                                    LocalDate periodEnd,
                                                    List<String> logs) {
        int estimationMonths = schedulerConfig.getEstimationMonths();
        LocalDate searchEndDate = periodStart.minusDays(1);

        List<MeterReading> historicalReadings = meterReadingRepository.findLatestByHouseholdIdAndDate(
                household.getId(), searchEndDate, PageRequest.of(0, estimationMonths + 1));

        if (historicalReadings.size() < 2) {
            logs.add("历史电表读数不足（需要至少2条记录），无法估算");
            return null;
        }

        BigDecimal totalUsage = BigDecimal.ZERO;
        int validPeriods = 0;

        for (int i = 0; i < historicalReadings.size() - 1 && i < estimationMonths; i++) {
            MeterReading current = historicalReadings.get(i);
            MeterReading previous = historicalReadings.get(i + 1);

            BigDecimal usage = current.getTotalKwh().subtract(previous.getTotalKwh());
            if (usage.compareTo(BigDecimal.ZERO) >= 0) {
                totalUsage = totalUsage.add(usage);
                validPeriods++;
                logs.add(String.format("历史周期 %s 至 %s: 用电量 %.2f 度",
                        previous.getReadingDate(), current.getReadingDate(), usage));
            }
        }

        if (validPeriods == 0) {
            logs.add("无有效的历史用电量数据，无法估算");
            return null;
        }

        BigDecimal avgUsagePerPeriod = totalUsage.divide(BigDecimal.valueOf(validPeriods), 2, RoundingMode.HALF_UP);

        int daysInPeriod = periodEnd.getDayOfMonth();
        int avgDaysInHistorical = 30;
        BigDecimal estimatedUsage = avgUsagePerPeriod
                .multiply(BigDecimal.valueOf(daysInPeriod))
                .divide(BigDecimal.valueOf(avgDaysInHistorical), 2, RoundingMode.HALF_UP);

        MeterReading latestReading = historicalReadings.get(0);
        BigDecimal estimatedTotalKwh = latestReading.getTotalKwh().add(estimatedUsage);

        BigDecimal avgUnitPrice = calculateAverageUnitPrice(historicalReadings.subList(0, Math.min(validPeriods, estimationMonths)));
        BigDecimal estimatedAmount = estimatedUsage.multiply(avgUnitPrice).setScale(2, RoundingMode.HALF_UP);

        MeterReading estimatedReading = MeterReading.builder()
                .household(household)
                .readingDate(periodEnd)
                .totalKwh(estimatedTotalKwh)
                .amount(estimatedAmount)
                .build();

        logs.add(String.format("估算结果: 总用电量 %.2f 度，电费 %.2f 元（基于 %d 个历史周期的平均值）",
                estimatedUsage, estimatedAmount, validPeriods));

        return estimatedReading;
    }

    private Map<Long, RoomMeterReading> estimateRoomReadings(Household household,
                                                              MeterReading meterReading,
                                                              List<Room> rooms,
                                                              YearMonth billingPeriod,
                                                              List<String> logs) {
        Map<Long, RoomMeterReading> result = new HashMap<>();
        int estimationMonths = schedulerConfig.getEstimationMonths();
        LocalDate searchEndDate = billingPeriod.atDay(1).minusDays(1);

        for (Room room : rooms) {
            List<RoomMeterReading> historicalRoomReadings = roomMeterReadingRepository.findLatestByRoomIdAndDate(
                    room.getId(), searchEndDate);

            if (historicalRoomReadings.isEmpty()) {
                logs.add(String.format("房间 %s 无历史读数，按均摊方式估算", room.getName()));
                continue;
            }

            BigDecimal totalUsage = BigDecimal.ZERO;
            int validPeriods = 0;
            BigDecimal lastReading = null;

            for (int i = 0; i < historicalRoomReadings.size() && i < estimationMonths; i++) {
                RoomMeterReading reading = historicalRoomReadings.get(i);
                if (lastReading == null) {
                    lastReading = reading.getCurrentReading();
                }
                if (reading.getUsageKwh().compareTo(BigDecimal.ZERO) >= 0) {
                    totalUsage = totalUsage.add(reading.getUsageKwh());
                    validPeriods++;
                }
            }

            if (validPeriods == 0 || lastReading == null) {
                logs.add(String.format("房间 %s 无有效的历史用电量数据，按均摊方式估算", room.getName()));
                continue;
            }

            BigDecimal avgUsage = totalUsage.divide(BigDecimal.valueOf(validPeriods), 2, RoundingMode.HALF_UP);
            BigDecimal estimatedCurrentReading = lastReading.add(avgUsage);

            RoomMeterReading estimatedReading = RoomMeterReading.builder()
                    .meterReading(meterReading)
                    .room(room)
                    .previousReading(lastReading)
                    .currentReading(estimatedCurrentReading)
                    .usageKwh(avgUsage)
                    .isAcUsage(room.getHasAirConditioner() != null && room.getHasAirConditioner())
                    .build();

            result.put(room.getId(), estimatedReading);
            logs.add(String.format("房间 %s 估算: 上期 %.2f 度，本期 %.2f 度，用电 %.2f 度（基于 %d 个历史周期的平均值）",
                    room.getName(), lastReading, estimatedCurrentReading, avgUsage, validPeriods));
        }

        int estimatedCount = result.size();
        int roomsWithoutEstimate = rooms.size() - estimatedCount;
        if (roomsWithoutEstimate > 0) {
            BigDecimal totalEstimatedUsage = result.values().stream()
                    .map(RoomMeterReading::getUsageKwh)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal totalHistoricalUsage = getHistoricalTotalUsage(household, searchEndDate, estimationMonths);
            BigDecimal remainingUsage = totalHistoricalUsage.compareTo(BigDecimal.ZERO) > 0
                    ? totalHistoricalUsage.subtract(totalEstimatedUsage)
                    : BigDecimal.ZERO;

            if (remainingUsage.compareTo(BigDecimal.ZERO) > 0 && roomsWithoutEstimate > 0) {
                BigDecimal perRoomShare = remainingUsage.divide(
                        BigDecimal.valueOf(roomsWithoutEstimate), 2, RoundingMode.HALF_UP);

                for (Room room : rooms) {
                    if (!result.containsKey(room.getId())) {
                        BigDecimal lastKnownReading = getLastKnownReading(room, searchEndDate);
                        RoomMeterReading estimatedReading = RoomMeterReading.builder()
                                .meterReading(meterReading)
                                .room(room)
                                .previousReading(lastKnownReading)
                                .currentReading(lastKnownReading.add(perRoomShare))
                                .usageKwh(perRoomShare)
                                .isAcUsage(room.getHasAirConditioner() != null && room.getHasAirConditioner())
                                .build();
                        result.put(room.getId(), estimatedReading);
                        logs.add(String.format("房间 %s 均摊估算: 用电 %.2f 度", room.getName(), perRoomShare));
                    }
                }
            }
        }

        return result;
    }

    private BigDecimal calculateAverageUnitPrice(List<MeterReading> readings) {
        if (readings.size() < 2) {
            return new BigDecimal("0.6");
        }

        BigDecimal totalAmount = BigDecimal.ZERO;
        BigDecimal totalUsage = BigDecimal.ZERO;

        for (int i = 0; i < readings.size() - 1; i++) {
            MeterReading current = readings.get(i);
            MeterReading previous = readings.get(i + 1);
            BigDecimal usage = current.getTotalKwh().subtract(previous.getTotalKwh());
            if (usage.compareTo(BigDecimal.ZERO) > 0) {
                totalAmount = totalAmount.add(current.getAmount());
                totalUsage = totalUsage.add(usage);
            }
        }

        if (totalUsage.compareTo(BigDecimal.ZERO) == 0) {
            return new BigDecimal("0.6");
        }

        return totalAmount.divide(totalUsage, 4, RoundingMode.HALF_UP);
    }

    private BigDecimal getHistoricalTotalUsage(Household household, LocalDate endDate, int months) {
        List<MeterReading> readings = meterReadingRepository.findLatestByHouseholdIdAndDate(
                household.getId(), endDate, PageRequest.of(0, months + 1));

        if (readings.size() < 2) {
            return BigDecimal.ZERO;
        }

        BigDecimal totalUsage = BigDecimal.ZERO;
        for (int i = 0; i < readings.size() - 1 && i < months; i++) {
            BigDecimal usage = readings.get(i).getTotalKwh().subtract(readings.get(i + 1).getTotalKwh());
            if (usage.compareTo(BigDecimal.ZERO) > 0) {
                totalUsage = totalUsage.add(usage);
            }
        }

        return readings.size() > 1
                ? totalUsage.divide(BigDecimal.valueOf(Math.min(readings.size() - 1, months)), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
    }

    private BigDecimal getLastKnownReading(Room room, LocalDate endDate) {
        List<RoomMeterReading> readings = roomMeterReadingRepository.findLatestByRoomIdAndDate(
                room.getId(), endDate);

        if (readings.isEmpty()) {
            return BigDecimal.ZERO;
        }

        return readings.get(0).getCurrentReading();
    }
}

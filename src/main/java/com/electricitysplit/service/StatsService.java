package com.electricitysplit.service;

import com.electricitysplit.dto.StatsDto;
import com.electricitysplit.entity.Bill;
import com.electricitysplit.entity.BillStatus;
import com.electricitysplit.entity.Household;
import com.electricitysplit.entity.Room;
import com.electricitysplit.entity.User;
import com.electricitysplit.repository.BillRepository;
import com.electricitysplit.repository.HouseholdRepository;
import com.electricitysplit.repository.RoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StatsService {

    private final HouseholdRepository householdRepository;
    private final RoomRepository roomRepository;
    private final BillRepository billRepository;

    public StatsDto.OverviewResponse getOverview(User user) {
        Long userId = user.getId();

        List<Household> households = householdRepository.findByCreatedById(userId, org.springframework.data.domain.Pageable.unpaged()).getContent();
        long totalHouseholds = households.size();

        long totalRooms = 0;
        List<Bill> allBills = new ArrayList<>();

        for (Household h : households) {
            List<Room> rooms = roomRepository.findByHouseholdId(h.getId());
            totalRooms += rooms.size();
            List<Bill> bills = billRepository.findByHouseholdIdOrderByPeriodEndDesc(h.getId());
            allBills.addAll(bills);
        }

        long totalBills = allBills.size();
        long paidBills = allBills.stream().filter(b -> b.getStatus() == BillStatus.PAID).count();
        long pendingBills = totalBills - paidBills;

        BigDecimal totalAmount = allBills.stream()
                .map(Bill::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return StatsDto.OverviewResponse.builder()
                .totalHouseholds(totalHouseholds)
                .totalRooms(totalRooms)
                .totalBills(totalBills)
                .paidBills(paidBills)
                .pendingBills(pendingBills)
                .totalAmount(totalAmount)
                .build();
    }

    public StatsDto.TrendResponse getTrend(User user, LocalDate startDate, LocalDate endDate) {
        Long userId = user.getId();

        List<Household> households = householdRepository.findByCreatedById(userId, org.springframework.data.domain.Pageable.unpaged()).getContent();
        List<Bill> allBills = new ArrayList<>();

        for (Household h : households) {
            List<Bill> bills = billRepository.findByHouseholdIdOrderByPeriodEndDesc(h.getId());
            allBills.addAll(bills);
        }

        List<Bill> filteredBills = allBills;
        if (startDate != null && endDate != null) {
            filteredBills = allBills.stream()
                    .filter(b -> !b.getPeriodEnd().isBefore(startDate) && !b.getPeriodEnd().isAfter(endDate))
                    .collect(Collectors.toList());
        }

        Map<String, StatsDto.TrendItem> trendMap = new LinkedHashMap<>();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM");

        for (Bill bill : filteredBills) {
            String period = bill.getPeriodEnd().format(formatter);
            StatsDto.TrendItem item = trendMap.get(period);
            if (item == null) {
                item = StatsDto.TrendItem.builder()
                        .period(period)
                        .amount(BigDecimal.ZERO)
                        .billCount(0)
                        .build();
                trendMap.put(period, item);
            }
            item.setAmount(item.getAmount().add(bill.getTotalAmount()));
            item.setBillCount(item.getBillCount() + 1);
        }

        List<StatsDto.TrendItem> items = new ArrayList<>(trendMap.values());
        items.sort((a, b) -> b.getPeriod().compareTo(a.getPeriod()));

        return StatsDto.TrendResponse.builder()
                .items(items)
                .build();
    }
}

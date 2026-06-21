package com.electricitysplit.service;

import com.electricitysplit.entity.*;
import com.electricitysplit.exception.BusinessException;
import com.electricitysplit.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BillSplitServiceTest {

    @Mock
    private AllocationRuleRepository allocationRuleRepository;

    @Mock
    private RoomRepository roomRepository;

    @Mock
    private RoomCustomRatioRepository roomCustomRatioRepository;

    @Mock
    private RoomMeterReadingRepository roomMeterReadingRepository;

    @Mock
    private MeterReadingRepository meterReadingRepository;

    @Mock
    private BillItemRepository billItemRepository;

    @InjectMocks
    private BillSplitService billSplitService;

    private Household household;
    private User user;
    private Bill bill;
    private List<Room> rooms;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .id(1L)
                .username("testuser")
                .email("test@example.com")
                .passwordHash("password")
                .build();

        household = Household.builder()
                .id(1L)
                .name("测试家庭")
                .address("测试地址")
                .createdBy(user)
                .build();

        bill = Bill.builder()
                .id(1L)
                .household(household)
                .periodStart(LocalDate.of(2024, 1, 1))
                .periodEnd(LocalDate.of(2024, 1, 31))
                .totalAmount(new BigDecimal("300.00"))
                .status(BillStatus.PENDING_CONFIRMATION)
                .build();

        Room room1 = Room.builder()
                .id(1L)
                .household(household)
                .name("主卧")
                .area(new BigDecimal("20.00"))
                .occupant(user)
                .hasAirConditioner(true)
                .headCount(2)
                .build();

        Room room2 = Room.builder()
                .id(2L)
                .household(household)
                .name("次卧")
                .area(new BigDecimal("15.00"))
                .hasAirConditioner(true)
                .headCount(1)
                .build();

        Room room3 = Room.builder()
                .id(3L)
                .household(household)
                .name("书房")
                .area(new BigDecimal("10.00"))
                .hasAirConditioner(false)
                .headCount(1)
                .build();

        rooms = Arrays.asList(room1, room2, room3);
    }

    @Test
    void testSplitBill_PerPersonAllocation() {
        AllocationRule rule = AllocationRule.builder()
                .id(1L)
                .household(household)
                .baseAllocationType(AllocationType.PER_PERSON)
                .acAllocationType(AllocationType.PER_PERSON)
                .publicAllocationType(AllocationType.PER_PERSON)
                .publicRatio(BigDecimal.ZERO)
                .isActive(true)
                .build();

        when(allocationRuleRepository.findActiveByHouseholdId(1L)).thenReturn(Optional.of(rule));
        when(roomRepository.findByHouseholdId(1L)).thenReturn(rooms);
        when(billItemRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        List<BillItem> items = billSplitService.splitBill(bill, null);

        assertEquals(3, items.size());
        
        BigDecimal totalHeadCount = new BigDecimal("4");
        BigDecimal expectedRoom1 = new BigDecimal("300.00").multiply(new BigDecimal("2"))
                .divide(totalHeadCount, 2, RoundingMode.HALF_UP);
        BigDecimal expectedRoom2 = new BigDecimal("300.00").multiply(new BigDecimal("1"))
                .divide(totalHeadCount, 2, RoundingMode.HALF_UP);
        BigDecimal expectedRoom3 = new BigDecimal("300.00").multiply(new BigDecimal("1"))
                .divide(totalHeadCount, 2, RoundingMode.HALF_UP);

        assertEquals(expectedRoom1, items.get(0).getTotalDue());
        assertEquals(expectedRoom2, items.get(1).getTotalDue());
        assertEquals(expectedRoom3, items.get(2).getTotalDue());

        BigDecimal total = items.stream()
                .map(BillItem::getTotalDue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(new BigDecimal("300.00"), total);

        verify(billItemRepository).saveAll(anyList());
    }

    @Test
    void testSplitBill_PerAreaAllocation() {
        AllocationRule rule = AllocationRule.builder()
                .id(1L)
                .household(household)
                .baseAllocationType(AllocationType.PER_AREA)
                .acAllocationType(AllocationType.PER_AREA)
                .publicAllocationType(AllocationType.PER_AREA)
                .publicRatio(BigDecimal.ZERO)
                .isActive(true)
                .build();

        when(allocationRuleRepository.findActiveByHouseholdId(1L)).thenReturn(Optional.of(rule));
        when(roomRepository.findByHouseholdId(1L)).thenReturn(rooms);
        when(billItemRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        List<BillItem> items = billSplitService.splitBill(bill, null);

        assertEquals(3, items.size());

        BigDecimal totalArea = new BigDecimal("45.00");
        BigDecimal expectedRoom1 = new BigDecimal("300.00").multiply(new BigDecimal("20.00"))
                .divide(totalArea, 2, RoundingMode.HALF_UP);
        BigDecimal expectedRoom2 = new BigDecimal("300.00").multiply(new BigDecimal("15.00"))
                .divide(totalArea, 2, RoundingMode.HALF_UP);
        BigDecimal expectedRoom3 = new BigDecimal("300.00").multiply(new BigDecimal("10.00"))
                .divide(totalArea, 2, RoundingMode.HALF_UP);

        assertEquals(expectedRoom1, items.get(0).getTotalDue());
        assertEquals(expectedRoom2, items.get(1).getTotalDue());
        assertEquals(expectedRoom3, items.get(2).getTotalDue());

        BigDecimal total = items.stream()
                .map(BillItem::getTotalDue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(new BigDecimal("300.00"), total);
    }

    @Test
    void testSplitBill_EqualAllocation() {
        AllocationRule rule = AllocationRule.builder()
                .id(1L)
                .household(household)
                .baseAllocationType(AllocationType.EQUAL)
                .acAllocationType(AllocationType.EQUAL)
                .publicAllocationType(AllocationType.EQUAL)
                .publicRatio(BigDecimal.ZERO)
                .isActive(true)
                .build();

        when(allocationRuleRepository.findActiveByHouseholdId(1L)).thenReturn(Optional.of(rule));
        when(roomRepository.findByHouseholdId(1L)).thenReturn(rooms);
        when(billItemRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        List<BillItem> items = billSplitService.splitBill(bill, null);

        assertEquals(3, items.size());

        BigDecimal perRoom = new BigDecimal("300.00").divide(new BigDecimal("3"), 2, RoundingMode.HALF_UP);

        assertEquals(perRoom, items.get(0).getTotalDue());
        assertEquals(perRoom, items.get(1).getTotalDue());
        assertEquals(perRoom, items.get(2).getTotalDue());

        BigDecimal total = items.stream()
                .map(BillItem::getTotalDue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(new BigDecimal("300.00"), total);
    }

    @Test
    void testSplitBill_PublicAreaAllocation() {
        AllocationRule rule = AllocationRule.builder()
                .id(1L)
                .household(household)
                .baseAllocationType(AllocationType.EQUAL)
                .acAllocationType(AllocationType.EQUAL)
                .publicAllocationType(AllocationType.EQUAL)
                .publicRatio(new BigDecimal("0.20"))
                .isActive(true)
                .build();

        when(allocationRuleRepository.findActiveByHouseholdId(1L)).thenReturn(Optional.of(rule));
        when(roomRepository.findByHouseholdId(1L)).thenReturn(rooms);
        when(billItemRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        List<BillItem> items = billSplitService.splitBill(bill, null);

        assertEquals(3, items.size());

        BigDecimal publicAmount = new BigDecimal("300.00").multiply(new BigDecimal("0.20"))
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal remaining = new BigDecimal("300.00").subtract(publicAmount);
        BigDecimal perRoomRemaining = remaining.divide(new BigDecimal("3"), 2, RoundingMode.HALF_UP);
        BigDecimal perRoomPublic = publicAmount.divide(new BigDecimal("3"), 2, RoundingMode.HALF_UP);

        for (BillItem item : items) {
            assertTrue(item.getPublicShare().compareTo(BigDecimal.ZERO) > 0);
            assertTrue(item.getCalculationDetails().contains("公共区域电费"));
        }

        BigDecimal total = items.stream()
                .map(BillItem::getTotalDue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(new BigDecimal("300.00"), total);
    }

    @Test
    void testSplitBill_RoundingDifference() {
        bill.setTotalAmount(new BigDecimal("100.00"));

        AllocationRule rule = AllocationRule.builder()
                .id(1L)
                .household(household)
                .baseAllocationType(AllocationType.PER_PERSON)
                .acAllocationType(AllocationType.PER_PERSON)
                .publicAllocationType(AllocationType.PER_PERSON)
                .publicRatio(BigDecimal.ZERO)
                .isActive(true)
                .build();

        when(allocationRuleRepository.findActiveByHouseholdId(1L)).thenReturn(Optional.of(rule));
        when(roomRepository.findByHouseholdId(1L)).thenReturn(rooms);
        when(billItemRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        List<BillItem> items = billSplitService.splitBill(bill, null);

        BigDecimal total = items.stream()
                .map(BillItem::getTotalDue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(new BigDecimal("100.00"), total);

        long adjustmentCount = items.stream()
                .filter(item -> Boolean.TRUE.equals(item.getHasRoundingAdjustment()))
                .count();
        assertTrue(adjustmentCount <= 1);

        Optional<BillItem> adjustedItem = items.stream()
                .filter(item -> Boolean.TRUE.equals(item.getHasRoundingAdjustment()))
                .findFirst();
        if (adjustedItem.isPresent()) {
            assertTrue(adjustedItem.get().getCalculationDetails().contains("尾差调整"));
        }
    }

    @Test
    void testSplitBill_CustomRatioAllocation() {
        AllocationRule rule = AllocationRule.builder()
                .id(1L)
                .household(household)
                .baseAllocationType(AllocationType.CUSTOM_RATIO)
                .acAllocationType(AllocationType.CUSTOM_RATIO)
                .publicAllocationType(AllocationType.CUSTOM_RATIO)
                .publicRatio(BigDecimal.ZERO)
                .isActive(true)
                .build();

        RoomCustomRatio ratio1 = RoomCustomRatio.builder()
                .id(1L)
                .allocationRule(rule)
                .room(rooms.get(0))
                .baseRatio(new BigDecimal("0.50"))
                .acRatio(new BigDecimal("0.50"))
                .publicRatio(new BigDecimal("0.50"))
                .build();

        RoomCustomRatio ratio2 = RoomCustomRatio.builder()
                .id(2L)
                .allocationRule(rule)
                .room(rooms.get(1))
                .baseRatio(new BigDecimal("0.30"))
                .acRatio(new BigDecimal("0.30"))
                .publicRatio(new BigDecimal("0.30"))
                .build();

        RoomCustomRatio ratio3 = RoomCustomRatio.builder()
                .id(3L)
                .allocationRule(rule)
                .room(rooms.get(2))
                .baseRatio(new BigDecimal("0.20"))
                .acRatio(new BigDecimal("0.20"))
                .publicRatio(new BigDecimal("0.20"))
                .build();

        when(allocationRuleRepository.findActiveByHouseholdId(1L)).thenReturn(Optional.of(rule));
        when(roomRepository.findByHouseholdId(1L)).thenReturn(rooms);
        when(roomCustomRatioRepository.findByAllocationRuleId(1L))
                .thenReturn(Arrays.asList(ratio1, ratio2, ratio3));
        when(billItemRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        List<BillItem> items = billSplitService.splitBill(bill, null);

        assertEquals(3, items.size());
        assertEquals(new BigDecimal("150.00"), items.get(0).getTotalDue());
        assertEquals(new BigDecimal("90.00"), items.get(1).getTotalDue());
        assertEquals(new BigDecimal("60.00"), items.get(2).getTotalDue());

        BigDecimal total = items.stream()
                .map(BillItem::getTotalDue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(new BigDecimal("300.00"), total);
    }

    @Test
    void testSplitBill_NoAllocationRule() {
        when(allocationRuleRepository.findActiveByHouseholdId(1L)).thenReturn(Optional.empty());

        assertThrows(BusinessException.class, () -> {
            billSplitService.splitBill(bill, null);
        });
    }

    @Test
    void testSplitBill_NoRooms() {
        AllocationRule rule = AllocationRule.builder()
                .id(1L)
                .household(household)
                .baseAllocationType(AllocationType.EQUAL)
                .acAllocationType(AllocationType.EQUAL)
                .publicAllocationType(AllocationType.EQUAL)
                .publicRatio(BigDecimal.ZERO)
                .isActive(true)
                .build();

        when(allocationRuleRepository.findActiveByHouseholdId(1L)).thenReturn(Optional.of(rule));
        when(roomRepository.findByHouseholdId(1L)).thenReturn(Collections.emptyList());

        assertThrows(BusinessException.class, () -> {
            billSplitService.splitBill(bill, null);
        });
    }

    @Test
    void testSplitBill_WithMeterReadings() {
        AllocationRule rule = AllocationRule.builder()
                .id(1L)
                .household(household)
                .baseAllocationType(AllocationType.EQUAL)
                .acAllocationType(AllocationType.EQUAL)
                .publicAllocationType(AllocationType.EQUAL)
                .publicRatio(BigDecimal.ZERO)
                .isActive(true)
                .build();

        MeterReading meterReading = MeterReading.builder()
                .id(1L)
                .household(household)
                .readingDate(LocalDate.of(2024, 1, 31))
                .totalKwh(new BigDecimal("500.00"))
                .amount(new BigDecimal("300.00"))
                .build();

        RoomMeterReading reading1 = RoomMeterReading.builder()
                .id(1L)
                .meterReading(meterReading)
                .room(rooms.get(0))
                .previousReading(new BigDecimal("100.00"))
                .currentReading(new BigDecimal("300.00"))
                .usageKwh(new BigDecimal("200.00"))
                .isAcUsage(false)
                .build();

        RoomMeterReading reading2 = RoomMeterReading.builder()
                .id(2L)
                .meterReading(meterReading)
                .room(rooms.get(1))
                .previousReading(new BigDecimal("50.00"))
                .currentReading(new BigDecimal("150.00"))
                .usageKwh(new BigDecimal("100.00"))
                .isAcUsage(false)
                .build();

        RoomMeterReading reading3 = RoomMeterReading.builder()
                .id(3L)
                .meterReading(meterReading)
                .room(rooms.get(2))
                .previousReading(new BigDecimal("30.00"))
                .currentReading(new BigDecimal("80.00"))
                .usageKwh(new BigDecimal("50.00"))
                .isAcUsage(false)
                .build();

        when(allocationRuleRepository.findActiveByHouseholdId(1L)).thenReturn(Optional.of(rule));
        when(roomRepository.findByHouseholdId(1L)).thenReturn(rooms);
        when(meterReadingRepository.findById(1L)).thenReturn(Optional.of(meterReading));
        when(roomMeterReadingRepository.findByMeterReadingId(1L))
                .thenReturn(Arrays.asList(reading1, reading2, reading3));
        when(billItemRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        List<BillItem> items = billSplitService.splitBill(bill, 1L);

        for (BillItem item : items) {
            assertTrue(item.getCalculationDetails().contains("电表读数"));
            assertTrue(item.getCalculationDetails().contains("用电"));
        }

        BigDecimal total = items.stream()
                .map(BillItem::getTotalDue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(new BigDecimal("300.00"), total);
    }

    @Test
    void testSplitBill_ACAllocationOnlyForACRooms() {
        AllocationRule rule = AllocationRule.builder()
                .id(1L)
                .household(household)
                .baseAllocationType(AllocationType.EQUAL)
                .acAllocationType(AllocationType.EQUAL)
                .publicAllocationType(AllocationType.EQUAL)
                .publicRatio(BigDecimal.ZERO)
                .isActive(true)
                .build();

        bill.setTotalAmount(new BigDecimal("100.00"));

        when(allocationRuleRepository.findActiveByHouseholdId(1L)).thenReturn(Optional.of(rule));
        when(roomRepository.findByHouseholdId(1L)).thenReturn(rooms);
        when(billItemRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        List<BillItem> items = billSplitService.splitBill(bill, null);

        assertEquals(3, items.size());

        assertEquals(BigDecimal.ZERO, items.get(2).getAcShare());

        BigDecimal total = items.stream()
                .map(BillItem::getTotalDue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(new BigDecimal("100.00"), total);
    }

    @Test
    void testSplitBill_CalculationDetails() {
        AllocationRule rule = AllocationRule.builder()
                .id(1L)
                .household(household)
                .baseAllocationType(AllocationType.PER_PERSON)
                .acAllocationType(AllocationType.PER_AREA)
                .publicAllocationType(AllocationType.EQUAL)
                .publicRatio(new BigDecimal("0.10"))
                .isActive(true)
                .build();

        when(allocationRuleRepository.findActiveByHouseholdId(1L)).thenReturn(Optional.of(rule));
        when(roomRepository.findByHouseholdId(1L)).thenReturn(rooms);
        when(billItemRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        List<BillItem> items = billSplitService.splitBill(bill, null);

        for (BillItem item : items) {
            assertNotNull(item.getCalculationDetails());
            assertTrue(item.getCalculationDetails().contains(item.getRoom().getName()));
            assertTrue(item.getCalculationDetails().contains("基础电费"));
            assertTrue(item.getCalculationDetails().contains("公共区域电费"));
            assertTrue(item.getCalculationDetails().contains("合计应付"));
        }
    }
}

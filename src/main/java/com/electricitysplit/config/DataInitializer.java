package com.electricitysplit.config;

import com.electricitysplit.entity.*;
import com.electricitysplit.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final HouseholdRepository householdRepository;
    private final RoomRepository roomRepository;
    private final MeterReadingRepository meterReadingRepository;
    private final BillRepository billRepository;
    private final BillItemRepository billItemRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(String... args) {
        if (userRepository.count() > 0) {
            log.info("Seed data already exists, skipping initialization.");
            return;
        }

        log.info("Starting seed data initialization...");

        User user1 = createUser("zhangsan", "zhangsan@example.com", "张三头像", Role.ROLE_USER);
        User user2 = createUser("lisi", "lisi@example.com", "李四头像", Role.ROLE_USER);
        User user3 = createUser("wangwu", "wangwu@example.com", "王五头像", Role.ROLE_USER);
        User user4 = createUser("zhaoliu", "zhaoliu@example.com", "赵六头像", Role.ROLE_USER);
        User admin = createUser("admin", "admin@example.com", "管理员头像", Role.ROLE_ADMIN);

        Household h1 = createHousehold("阳光花园3栋2单元", "北京市朝阳区阳光花园3栋2单元501", user1);
        Household h2 = createHousehold("幸福里小区", "上海市浦东新区幸福里小区8号楼302", user2);

        Room r1 = createRoom(h1, "主卧", new BigDecimal("18.5"), user1, true);
        Room r2 = createRoom(h1, "次卧", new BigDecimal("12.0"), user3, false);
        Room r3 = createRoom(h1, "小书房", new BigDecimal("8.0"), null, true);
        Room r4 = createRoom(h2, "大房间", new BigDecimal("20.0"), user2, true);
        Room r5 = createRoom(h2, "小房间", new BigDecimal("10.0"), user4, false);

        createMeterReading(h1, LocalDate.of(2026, 1, 1), new BigDecimal("1250.0"), new BigDecimal("750.00"));
        createMeterReading(h1, LocalDate.of(2026, 2, 1), new BigDecimal("1580.0"), new BigDecimal("948.00"));
        createMeterReading(h1, LocalDate.of(2026, 3, 1), new BigDecimal("1820.0"), new BigDecimal("1092.00"));
        createMeterReading(h1, LocalDate.of(2026, 4, 1), new BigDecimal("2050.0"), new BigDecimal("1230.00"));
        createMeterReading(h1, LocalDate.of(2026, 5, 1), new BigDecimal("2380.0"), new BigDecimal("1428.00"));

        createMeterReading(h2, LocalDate.of(2026, 1, 1), new BigDecimal("980.0"), new BigDecimal("588.00"));
        createMeterReading(h2, LocalDate.of(2026, 2, 1), new BigDecimal("1260.0"), new BigDecimal("756.00"));
        createMeterReading(h2, LocalDate.of(2026, 3, 1), new BigDecimal("1520.0"), new BigDecimal("912.00"));
        createMeterReading(h2, LocalDate.of(2026, 4, 1), new BigDecimal("1750.0"), new BigDecimal("1050.00"));

        Bill b1 = createBill(h1, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31),
                new BigDecimal("948.00"), BillStatus.PAID);
        createBillItem(b1, r1, new BigDecimal("250.00"), new BigDecimal("120.00"), new BigDecimal("80.00"), true);
        createBillItem(b1, r2, new BigDecimal("180.00"), new BigDecimal("30.00"), new BigDecimal("80.00"), true);
        createBillItem(b1, r3, new BigDecimal("120.00"), new BigDecimal("50.00"), new BigDecimal("38.00"), false);

        Bill b2 = createBill(h1, LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28),
                new BigDecimal("1092.00"), BillStatus.PENDING_PAYMENT);
        createBillItem(b2, r1, new BigDecimal("280.00"), new BigDecimal("150.00"), new BigDecimal("90.00"), true);
        createBillItem(b2, r2, new BigDecimal("200.00"), new BigDecimal("40.00"), new BigDecimal("90.00"), false);
        createBillItem(b2, r3, new BigDecimal("140.00"), new BigDecimal("60.00"), new BigDecimal("42.00"), false);

        Bill b3 = createBill(h1, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31),
                new BigDecimal("1230.00"), BillStatus.PENDING_CONFIRMATION);
        createBillItem(b3, r1, new BigDecimal("300.00"), new BigDecimal("180.00"), new BigDecimal("100.00"), false);
        createBillItem(b3, r2, new BigDecimal("220.00"), new BigDecimal("50.00"), new BigDecimal("100.00"), false);
        createBillItem(b3, r3, new BigDecimal("150.00"), new BigDecimal("80.00"), new BigDecimal("50.00"), false);

        Bill b4 = createBill(h2, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31),
                new BigDecimal("756.00"), BillStatus.PAID);
        createBillItem(b4, r4, new BigDecimal("300.00"), new BigDecimal("100.00"), new BigDecimal("60.00"), true);
        createBillItem(b4, r5, new BigDecimal("180.00"), new BigDecimal("40.00"), new BigDecimal("76.00"), true);

        Bill b5 = createBill(h2, LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28),
                new BigDecimal("912.00"), BillStatus.PENDING_PAYMENT);
        createBillItem(b5, r4, new BigDecimal("340.00"), new BigDecimal("130.00"), new BigDecimal("70.00"), false);
        createBillItem(b5, r5, new BigDecimal("200.00"), new BigDecimal("50.00"), new BigDecimal("122.00"), false);

        log.info("Seed data initialization completed successfully!");
        log.info("Created 5 users (4 regular + 1 admin), 2 households, 5 rooms, 9 meter readings, 5 bills, and 13 bill items.");
        log.info("Default test accounts: zhangsan/lisi/wangwu/zhaoliu, password: 123456");
        log.info("Default admin account: admin, password: 123456");
    }

    private User createUser(String username, String email, String avatar, Role role) {
        User user = User.builder()
                .username(username)
                .email(email)
                .passwordHash(passwordEncoder.encode("123456"))
                .avatar(avatar)
                .role(role)
                .build();
        return userRepository.save(user);
    }

    private Household createHousehold(String name, String address, User createdBy) {
        Household household = Household.builder()
                .name(name)
                .address(address)
                .createdBy(createdBy)
                .build();
        return householdRepository.save(household);
    }

    private Room createRoom(Household household, String name, BigDecimal area, User occupant, Boolean hasAc) {
        Room room = Room.builder()
                .household(household)
                .name(name)
                .area(area)
                .occupant(occupant)
                .hasAirConditioner(hasAc)
                .build();
        return roomRepository.save(room);
    }

    private MeterReading createMeterReading(Household household, LocalDate readingDate, BigDecimal totalKwh, BigDecimal amount) {
        MeterReading reading = MeterReading.builder()
                .household(household)
                .readingDate(readingDate)
                .totalKwh(totalKwh)
                .amount(amount)
                .build();
        return meterReadingRepository.save(reading);
    }

    private Bill createBill(Household household, LocalDate periodStart, LocalDate periodEnd,
                            BigDecimal totalAmount, BillStatus status) {
        Bill bill = Bill.builder()
                .household(household)
                .periodStart(periodStart)
                .periodEnd(periodEnd)
                .totalAmount(totalAmount)
                .status(status)
                .build();
        return billRepository.save(bill);
    }

    private void createBillItem(Bill bill, Room room, BigDecimal baseShare, BigDecimal acShare,
                                BigDecimal publicShare, Boolean isPaid) {
        BigDecimal totalDue = baseShare.add(acShare).add(publicShare);
        BillItem item = BillItem.builder()
                .bill(bill)
                .room(room)
                .baseShare(baseShare)
                .acShare(acShare)
                .publicShare(publicShare)
                .totalDue(totalDue)
                .isPaid(isPaid)
                .paidAt(isPaid ? LocalDateTime.now() : null)
                .build();
        billItemRepository.save(item);
    }
}

package com.electricitysplit.service;

import com.electricitysplit.dto.HouseholdDto;
import com.electricitysplit.entity.Bill;
import com.electricitysplit.entity.Household;
import com.electricitysplit.entity.User;
import com.electricitysplit.exception.BusinessException;
import com.electricitysplit.repository.BillItemRepository;
import com.electricitysplit.repository.BillRepository;
import com.electricitysplit.repository.HouseholdRepository;
import com.electricitysplit.repository.MeterReadingRepository;
import com.electricitysplit.repository.RoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class HouseholdService {

    private final HouseholdRepository householdRepository;
    private final RoomRepository roomRepository;
    private final MeterReadingRepository meterReadingRepository;
    private final BillRepository billRepository;
    private final BillItemRepository billItemRepository;

    @Transactional
    public HouseholdDto.Response create(User user, HouseholdDto.CreateRequest request) {
        Household household = Household.builder()
                .name(request.getName())
                .address(request.getAddress())
                .createdBy(user)
                .build();

        Household saved = householdRepository.save(household);
        return toResponse(saved);
    }

    public HouseholdDto.Response getById(User user, Long id) {
        Household household = householdRepository.findByIdAndCreatedById(id, user.getId())
                .orElseThrow(() -> new BusinessException("住户不存在或无权限访问"));
        return toResponse(household);
    }

    public Household getEntityByIdAndCheckPermission(User user, Long id) {
        return householdRepository.findByIdAndCreatedById(id, user.getId())
                .orElseThrow(() -> new BusinessException("住户不存在或无权限访问"));
    }

    public Page<HouseholdDto.Response> list(User user, String keyword, Pageable pageable) {
        Page<Household> page;

        if (keyword != null && !keyword.isBlank()) {
            page = householdRepository.findByCreatedByIdAndKeyword(user.getId(), keyword, pageable);
        } else {
            page = householdRepository.findByCreatedById(user.getId(), pageable);
        }

        return page.map(this::toResponse);
    }

    @Transactional
    public HouseholdDto.Response update(User user, Long id, HouseholdDto.UpdateRequest request) {
        Household household = getEntityByIdAndCheckPermission(user, id);

        if (request.getName() != null) {
            household.setName(request.getName());
        }
        if (request.getAddress() != null) {
            household.setAddress(request.getAddress());
        }

        Household saved = householdRepository.save(household);
        return toResponse(saved);
    }

    @Transactional
    public void delete(User user, Long id) {
        Household household = getEntityByIdAndCheckPermission(user, id);

        billRepository.findByHouseholdIdOrderByPeriodEndDesc(household.getId()).forEach(bill -> {
            billItemRepository.deleteAll(billItemRepository.findByBillId(bill.getId()));
        });
        billRepository.deleteAll(billRepository.findByHouseholdIdOrderByPeriodEndDesc(household.getId()));

        meterReadingRepository.deleteAll(meterReadingRepository.findByHouseholdIdOrderByReadingDateDesc(household.getId()));

        roomRepository.deleteAll(roomRepository.findByHouseholdId(household.getId()));

        householdRepository.delete(household);
    }

    private HouseholdDto.Response toResponse(Household household) {
        return HouseholdDto.Response.builder()
                .id(household.getId())
                .name(household.getName())
                .address(household.getAddress())
                .createdById(household.getCreatedBy().getId())
                .createdByUsername(household.getCreatedBy().getUsername())
                .createdAt(household.getCreatedAt())
                .build();
    }
}

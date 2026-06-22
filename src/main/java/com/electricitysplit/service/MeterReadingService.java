package com.electricitysplit.service;

import com.electricitysplit.dto.MeterReadingDto;
import com.electricitysplit.entity.Household;
import com.electricitysplit.entity.MeterReading;
import com.electricitysplit.entity.User;
import com.electricitysplit.exception.BusinessException;
import com.electricitysplit.repository.MeterReadingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class MeterReadingService {

    private final MeterReadingRepository meterReadingRepository;
    private final HouseholdService householdService;

    @Transactional
    public MeterReadingDto.Response create(User user, MeterReadingDto.CreateRequest request) {
        Household household = householdService.getEntityByIdAndCheckPermission(user, request.getHouseholdId());

        validateReadingNotReversed(household.getId(), null, request.getTotalKwh(), request.getReadingDate());

        MeterReading reading = MeterReading.builder()
                .household(household)
                .readingDate(request.getReadingDate())
                .totalKwh(request.getTotalKwh())
                .amount(request.getAmount())
                .build();

        MeterReading saved = meterReadingRepository.save(reading);
        return toResponse(saved);
    }

    private void validateReadingNotReversed(Long householdId, Long excludeReadingId,
                                             BigDecimal newTotalKwh, LocalDate readingDate) {
        if (newTotalKwh == null) {
            return;
        }

        Optional<MeterReading> latestOpt;

        if (excludeReadingId != null && readingDate != null) {
            latestOpt = meterReadingRepository.findLatestByHouseholdIdAndDateBefore(
                    householdId, readingDate, excludeReadingId);
        } else if (excludeReadingId != null) {
            latestOpt = meterReadingRepository.findLatestByHouseholdIdExcludeId(householdId, excludeReadingId);
        } else {
            latestOpt = meterReadingRepository.findLatestByHouseholdId(householdId);
        }

        if (latestOpt.isPresent()) {
            MeterReading latest = latestOpt.get();
            if (newTotalKwh.compareTo(latest.getTotalKwh()) < 0) {
                throw new BusinessException(
                        String.format("电表读数禁止反向录入：新读数(%.2f度)必须大于等于最近一次有效读数(%.2f度，日期：%s)。" +
                                        "如需回退读数，请通过专门的校正流程由管理员审批。",
                                newTotalKwh, latest.getTotalKwh(), latest.getReadingDate()));
            }
        }
    }

    public MeterReadingDto.Response getById(User user, Long id) {
        MeterReading reading = meterReadingRepository.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new BusinessException("电表读数不存在或无权限访问"));
        return toResponse(reading);
    }

    public MeterReading getEntityByIdAndCheckPermission(User user, Long id) {
        return meterReadingRepository.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new BusinessException("电表读数不存在或无权限访问"));
    }

    public Page<MeterReadingDto.Response> list(User user, Long householdId, LocalDate startDate, LocalDate endDate, Pageable pageable) {
        if (householdId == null) {
            throw new BusinessException("必须指定住户ID");
        }
        householdService.getEntityByIdAndCheckPermission(user, householdId);

        Page<MeterReading> page;

        if (startDate != null && endDate != null) {
            page = meterReadingRepository.findByHouseholdIdAndDateRange(householdId, startDate, endDate, pageable);
        } else {
            page = meterReadingRepository.findByHouseholdId(householdId, pageable);
        }

        return page.map(this::toResponse);
    }

    @Transactional
    public MeterReadingDto.Response update(User user, Long id, MeterReadingDto.UpdateRequest request) {
        MeterReading reading = getEntityByIdAndCheckPermission(user, id);

        LocalDate newReadingDate = request.getReadingDate() != null ? request.getReadingDate() : reading.getReadingDate();
        BigDecimal newTotalKwh = request.getTotalKwh() != null ? request.getTotalKwh() : reading.getTotalKwh();

        validateReadingNotReversed(reading.getHousehold().getId(), id, newTotalKwh, newReadingDate);

        if (request.getReadingDate() != null) {
            reading.setReadingDate(request.getReadingDate());
        }
        if (request.getTotalKwh() != null) {
            reading.setTotalKwh(request.getTotalKwh());
        }
        if (request.getAmount() != null) {
            reading.setAmount(request.getAmount());
        }

        MeterReading saved = meterReadingRepository.save(reading);
        return toResponse(saved);
    }

    @Transactional
    public void delete(User user, Long id) {
        MeterReading reading = getEntityByIdAndCheckPermission(user, id);
        meterReadingRepository.delete(reading);
    }

    private MeterReadingDto.Response toResponse(MeterReading reading) {
        return MeterReadingDto.Response.builder()
                .id(reading.getId())
                .householdId(reading.getHousehold().getId())
                .householdName(reading.getHousehold().getName())
                .readingDate(reading.getReadingDate())
                .totalKwh(reading.getTotalKwh())
                .amount(reading.getAmount())
                .createdAt(reading.getCreatedAt())
                .build();
    }
}

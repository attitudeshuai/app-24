package com.electricitysplit.service;

import com.electricitysplit.dto.AllocationRuleDto;
import com.electricitysplit.entity.AllocationRule;
import com.electricitysplit.entity.Household;
import com.electricitysplit.entity.Room;
import com.electricitysplit.entity.RoomCustomRatio;
import com.electricitysplit.entity.User;
import com.electricitysplit.exception.BusinessException;
import com.electricitysplit.repository.AllocationRuleRepository;
import com.electricitysplit.repository.RoomCustomRatioRepository;
import com.electricitysplit.repository.RoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AllocationRuleService {

    private final AllocationRuleRepository allocationRuleRepository;
    private final RoomCustomRatioRepository roomCustomRatioRepository;
    private final RoomRepository roomRepository;
    private final HouseholdService householdService;

    @Transactional
    public AllocationRuleDto.Response create(User user, AllocationRuleDto.CreateRequest request) {
        Household household = householdService.getEntityByIdAndCheckPermission(user, request.getHouseholdId());

        if (Boolean.TRUE.equals(request.getIsActive())) {
            allocationRuleRepository.findActiveByHouseholdId(household.getId())
                    .ifPresent(rule -> {
                        rule.setIsActive(false);
                        allocationRuleRepository.save(rule);
                    });
        }

        AllocationRule rule = AllocationRule.builder()
                .household(household)
                .baseAllocationType(request.getBaseAllocationType())
                .acAllocationType(request.getAcAllocationType())
                .publicAllocationType(request.getPublicAllocationType())
                .publicRatio(request.getPublicRatio())
                .isActive(request.getIsActive() != null ? request.getIsActive() : true)
                .build();

        AllocationRule savedRule = allocationRuleRepository.save(rule);

        List<RoomCustomRatio> customRatios = new ArrayList<>();
        if (request.getRoomCustomRatios() != null && !request.getRoomCustomRatios().isEmpty()) {
            for (AllocationRuleDto.RoomCustomRatioDto ratioDto : request.getRoomCustomRatios()) {
                Room room = roomRepository.findById(ratioDto.getRoomId())
                        .orElseThrow(() -> new BusinessException("房间不存在: " + ratioDto.getRoomId()));
                if (!room.getHousehold().getId().equals(household.getId())) {
                    throw new BusinessException("房间不属于该住户: " + room.getName());
                }

                RoomCustomRatio ratio = RoomCustomRatio.builder()
                        .allocationRule(savedRule)
                        .room(room)
                        .baseRatio(ratioDto.getBaseRatio())
                        .acRatio(ratioDto.getAcRatio())
                        .publicRatio(ratioDto.getPublicRatio())
                        .build();
                customRatios.add(ratio);
            }
            roomCustomRatioRepository.saveAll(customRatios);
        }

        return toResponse(savedRule, customRatios);
    }

    public AllocationRuleDto.Response getById(User user, Long id) {
        AllocationRule rule = allocationRuleRepository.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new BusinessException("分摊规则不存在或无权限访问"));
        List<RoomCustomRatio> ratios = roomCustomRatioRepository.findByAllocationRuleId(id);
        return toResponse(rule, ratios);
    }

    public AllocationRuleDto.Response getActiveByHouseholdId(User user, Long householdId) {
        householdService.getEntityByIdAndCheckPermission(user, householdId);
        AllocationRule rule = allocationRuleRepository.findActiveByHouseholdId(householdId)
                .orElseThrow(() -> new BusinessException("该住户没有有效的分摊规则"));
        List<RoomCustomRatio> ratios = roomCustomRatioRepository.findByAllocationRuleId(rule.getId());
        return toResponse(rule, ratios);
    }

    public List<AllocationRuleDto.Response> listByHouseholdId(User user, Long householdId) {
        householdService.getEntityByIdAndCheckPermission(user, householdId);
        List<AllocationRule> rules = allocationRuleRepository.findByHouseholdId(householdId);
        return rules.stream()
                .map(rule -> {
                    List<RoomCustomRatio> ratios = roomCustomRatioRepository.findByAllocationRuleId(rule.getId());
                    return toResponse(rule, ratios);
                })
                .collect(Collectors.toList());
    }

    @Transactional
    public AllocationRuleDto.Response update(User user, Long id, AllocationRuleDto.UpdateRequest request) {
        AllocationRule rule = allocationRuleRepository.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new BusinessException("分摊规则不存在或无权限访问"));

        if (Boolean.TRUE.equals(request.getIsActive())) {
            allocationRuleRepository.findActiveByHouseholdId(rule.getHousehold().getId())
                    .ifPresent(r -> {
                        if (!r.getId().equals(id)) {
                            r.setIsActive(false);
                            allocationRuleRepository.save(r);
                        }
                    });
        }

        if (request.getBaseAllocationType() != null) {
            rule.setBaseAllocationType(request.getBaseAllocationType());
        }
        if (request.getAcAllocationType() != null) {
            rule.setAcAllocationType(request.getAcAllocationType());
        }
        if (request.getPublicAllocationType() != null) {
            rule.setPublicAllocationType(request.getPublicAllocationType());
        }
        if (request.getPublicRatio() != null) {
            rule.setPublicRatio(request.getPublicRatio());
        }
        if (request.getIsActive() != null) {
            rule.setIsActive(request.getIsActive());
        }

        AllocationRule savedRule = allocationRuleRepository.save(rule);

        List<RoomCustomRatio> existingRatios = roomCustomRatioRepository.findByAllocationRuleId(id);
        if (request.getRoomCustomRatios() != null) {
            roomCustomRatioRepository.deleteAll(existingRatios);

            List<RoomCustomRatio> newRatios = new ArrayList<>();
            for (AllocationRuleDto.RoomCustomRatioDto ratioDto : request.getRoomCustomRatios()) {
                Room room = roomRepository.findById(ratioDto.getRoomId())
                        .orElseThrow(() -> new BusinessException("房间不存在: " + ratioDto.getRoomId()));
                if (!room.getHousehold().getId().equals(rule.getHousehold().getId())) {
                    throw new BusinessException("房间不属于该住户: " + room.getName());
                }

                RoomCustomRatio ratio = RoomCustomRatio.builder()
                        .allocationRule(savedRule)
                        .room(room)
                        .baseRatio(ratioDto.getBaseRatio())
                        .acRatio(ratioDto.getAcRatio())
                        .publicRatio(ratioDto.getPublicRatio())
                        .build();
                newRatios.add(ratio);
            }
            roomCustomRatioRepository.saveAll(newRatios);
            existingRatios = newRatios;
        }

        return toResponse(savedRule, existingRatios);
    }

    @Transactional
    public AllocationRuleDto.Response setActive(User user, Long id) {
        AllocationRule rule = allocationRuleRepository.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new BusinessException("分摊规则不存在或无权限访问"));

        allocationRuleRepository.findActiveByHouseholdId(rule.getHousehold().getId())
                .ifPresent(r -> {
                    r.setIsActive(false);
                    allocationRuleRepository.save(r);
                });

        rule.setIsActive(true);
        AllocationRule saved = allocationRuleRepository.save(rule);
        List<RoomCustomRatio> ratios = roomCustomRatioRepository.findByAllocationRuleId(id);
        return toResponse(saved, ratios);
    }

    @Transactional
    public void delete(User user, Long id) {
        AllocationRule rule = allocationRuleRepository.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new BusinessException("分摊规则不存在或无权限访问"));

        List<RoomCustomRatio> ratios = roomCustomRatioRepository.findByAllocationRuleId(id);
        roomCustomRatioRepository.deleteAll(ratios);
        allocationRuleRepository.delete(rule);
    }

    private AllocationRuleDto.Response toResponse(AllocationRule rule, List<RoomCustomRatio> ratios) {
        List<AllocationRuleDto.RoomCustomRatioResponse> ratioResponses = ratios.stream()
                .map(this::toRoomRatioResponse)
                .collect(Collectors.toList());

        return AllocationRuleDto.Response.builder()
                .id(rule.getId())
                .householdId(rule.getHousehold().getId())
                .householdName(rule.getHousehold().getName())
                .baseAllocationType(rule.getBaseAllocationType())
                .acAllocationType(rule.getAcAllocationType())
                .publicAllocationType(rule.getPublicAllocationType())
                .publicRatio(rule.getPublicRatio())
                .isActive(rule.getIsActive())
                .roomCustomRatios(ratioResponses)
                .createdAt(rule.getCreatedAt())
                .updatedAt(rule.getUpdatedAt())
                .build();
    }

    private AllocationRuleDto.RoomCustomRatioResponse toRoomRatioResponse(RoomCustomRatio ratio) {
        return AllocationRuleDto.RoomCustomRatioResponse.builder()
                .id(ratio.getId())
                .roomId(ratio.getRoom().getId())
                .roomName(ratio.getRoom().getName())
                .baseRatio(ratio.getBaseRatio())
                .acRatio(ratio.getAcRatio())
                .publicRatio(ratio.getPublicRatio())
                .build();
    }
}

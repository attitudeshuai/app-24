package com.electricitysplit.service;

import com.electricitysplit.dto.RoomDto;
import com.electricitysplit.entity.Household;
import com.electricitysplit.entity.Room;
import com.electricitysplit.entity.User;
import com.electricitysplit.exception.BusinessException;
import com.electricitysplit.repository.RoomRepository;
import com.electricitysplit.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RoomService {

    private final RoomRepository roomRepository;
    private final HouseholdService householdService;
    private final UserRepository userRepository;

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

        Room saved = roomRepository.save(room);
        return toResponse(saved);
    }

    @Transactional
    public void delete(User user, Long id) {
        Room room = getEntityByIdAndCheckPermission(user, id);
        roomRepository.delete(room);
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
                .createdAt(room.getCreatedAt())
                .build();
    }
}

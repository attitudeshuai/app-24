package com.electricitysplit.controller;

import com.electricitysplit.dto.ApiResponse;
import com.electricitysplit.dto.PageResponse;
import com.electricitysplit.dto.RoomDto;
import com.electricitysplit.entity.User;
import com.electricitysplit.service.RoomService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/rooms")
@RequiredArgsConstructor
public class RoomController {

    private final RoomService roomService;

    @GetMapping
    public ApiResponse<PageResponse<RoomDto.Response>> list(
            @RequestParam Long householdId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword
    ) {
        User currentUser = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<RoomDto.Response> result = roomService.list(currentUser, householdId, keyword, pageable);
        return ApiResponse.success(PageResponse.from(result));
    }

    @PostMapping
    public ApiResponse<RoomDto.Response> create(@Valid @RequestBody RoomDto.CreateRequest request) {
        User currentUser = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return ApiResponse.success(roomService.create(currentUser, request));
    }

    @GetMapping("/{id}")
    public ApiResponse<RoomDto.Response> getById(@PathVariable Long id) {
        User currentUser = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return ApiResponse.success(roomService.getById(currentUser, id));
    }

    @PutMapping("/{id}")
    public ApiResponse<RoomDto.Response> update(
            @PathVariable Long id,
            @Valid @RequestBody RoomDto.UpdateRequest request
    ) {
        User currentUser = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return ApiResponse.success(roomService.update(currentUser, id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        User currentUser = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        roomService.delete(currentUser, id);
        return ApiResponse.success("删除成功", null);
    }
}

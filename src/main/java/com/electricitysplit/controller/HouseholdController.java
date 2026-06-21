package com.electricitysplit.controller;

import com.electricitysplit.dto.ApiResponse;
import com.electricitysplit.dto.HouseholdDto;
import com.electricitysplit.dto.PageResponse;
import com.electricitysplit.entity.User;
import com.electricitysplit.service.HouseholdService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/households")
@RequiredArgsConstructor
public class HouseholdController {

    private final HouseholdService householdService;

    @GetMapping
    public ApiResponse<PageResponse<HouseholdDto.Response>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "createdAt,desc") String sort
    ) {
        User currentUser = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        String[] sortParams = sort.split(",");
        Sort.Direction direction = sortParams.length > 1 && sortParams[1].equalsIgnoreCase("asc")
                ? Sort.Direction.ASC : Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortParams[0]));
        Page<HouseholdDto.Response> result = householdService.list(currentUser, keyword, pageable);
        return ApiResponse.success(PageResponse.from(result));
    }

    @PostMapping
    public ApiResponse<HouseholdDto.Response> create(@Valid @RequestBody HouseholdDto.CreateRequest request) {
        User currentUser = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return ApiResponse.success(householdService.create(currentUser, request));
    }

    @GetMapping("/{id}")
    public ApiResponse<HouseholdDto.Response> getById(@PathVariable Long id) {
        User currentUser = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return ApiResponse.success(householdService.getById(currentUser, id));
    }

    @PutMapping("/{id}")
    public ApiResponse<HouseholdDto.Response> update(
            @PathVariable Long id,
            @Valid @RequestBody HouseholdDto.UpdateRequest request
    ) {
        User currentUser = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return ApiResponse.success(householdService.update(currentUser, id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        User currentUser = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        householdService.delete(currentUser, id);
        return ApiResponse.success("删除成功", null);
    }
}

package com.electricitysplit.controller;

import com.electricitysplit.dto.ApiResponse;
import com.electricitysplit.dto.MeterReadingDto;
import com.electricitysplit.dto.PageResponse;
import com.electricitysplit.entity.User;
import com.electricitysplit.service.MeterReadingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/meterreadings")
@RequiredArgsConstructor
public class MeterReadingController {

    private final MeterReadingService meterReadingService;

    @GetMapping
    public ApiResponse<PageResponse<MeterReadingDto.Response>> list(
            @RequestParam Long householdId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate
    ) {
        User currentUser = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "readingDate"));
        Page<MeterReadingDto.Response> result = meterReadingService.list(currentUser, householdId, startDate, endDate, pageable);
        return ApiResponse.success(PageResponse.from(result));
    }

    @PostMapping
    public ApiResponse<MeterReadingDto.Response> create(@Valid @RequestBody MeterReadingDto.CreateRequest request) {
        User currentUser = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return ApiResponse.success(meterReadingService.create(currentUser, request));
    }

    @GetMapping("/{id}")
    public ApiResponse<MeterReadingDto.Response> getById(@PathVariable Long id) {
        User currentUser = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return ApiResponse.success(meterReadingService.getById(currentUser, id));
    }

    @PutMapping("/{id}")
    public ApiResponse<MeterReadingDto.Response> update(
            @PathVariable Long id,
            @Valid @RequestBody MeterReadingDto.UpdateRequest request
    ) {
        User currentUser = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return ApiResponse.success(meterReadingService.update(currentUser, id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        User currentUser = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        meterReadingService.delete(currentUser, id);
        return ApiResponse.success("删除成功", null);
    }
}

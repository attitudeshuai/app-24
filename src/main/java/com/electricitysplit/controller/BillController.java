package com.electricitysplit.controller;

import com.electricitysplit.dto.ApiResponse;
import com.electricitysplit.dto.BillDto;
import com.electricitysplit.dto.PageResponse;
import com.electricitysplit.entity.Bill;
import com.electricitysplit.entity.User;
import com.electricitysplit.service.BillService;
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
@RequestMapping("/api/bills")
@RequiredArgsConstructor
public class BillController {

    private final BillService billService;

    @GetMapping
    public ApiResponse<PageResponse<BillDto.Response>> list(
            @RequestParam Long householdId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Bill.BillStatus status,
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate
    ) {
        User currentUser = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<BillDto.Response> result = billService.list(currentUser, householdId, status, startDate, endDate, pageable);
        return ApiResponse.success(PageResponse.from(result));
    }

    @PostMapping
    public ApiResponse<BillDto.Response> create(@Valid @RequestBody BillDto.CreateRequest request) {
        User currentUser = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return ApiResponse.success(billService.create(currentUser, request));
    }

    @GetMapping("/{id}")
    public ApiResponse<BillDto.Response> getById(@PathVariable Long id) {
        User currentUser = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return ApiResponse.success(billService.getById(currentUser, id));
    }

    @PutMapping("/{id}")
    public ApiResponse<BillDto.Response> update(
            @PathVariable Long id,
            @Valid @RequestBody BillDto.UpdateRequest request
    ) {
        User currentUser = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return ApiResponse.success(billService.update(currentUser, id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        User currentUser = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        billService.delete(currentUser, id);
        return ApiResponse.success("删除成功", null);
    }

    @PatchMapping("/{id}/status")
    public ApiResponse<BillDto.Response> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody BillDto.UpdateStatusRequest request
    ) {
        User currentUser = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return ApiResponse.success(billService.updateStatus(currentUser, id, request.getStatus()));
    }
}

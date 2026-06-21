package com.electricitysplit.controller;

import com.electricitysplit.dto.ApiResponse;
import com.electricitysplit.dto.BillDto;
import com.electricitysplit.dto.PageResponse;
import com.electricitysplit.entity.BillStatus;
import com.electricitysplit.entity.User;
import com.electricitysplit.service.BillService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

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
            @RequestParam(required = false) BillStatus status,
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

    @PostMapping("/{id}/transition")
    public ApiResponse<BillDto.Response> transitionStatus(
            @PathVariable Long id,
            @Valid @RequestBody BillDto.TransitionRequest request
    ) {
        User currentUser = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return ApiResponse.success(billService.transitionStatus(currentUser, id, request));
    }

    @GetMapping("/{id}/history")
    public ApiResponse<List<BillDto.StatusHistoryResponse>> getStatusHistory(@PathVariable Long id) {
        User currentUser = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return ApiResponse.success(billService.getStatusHistory(currentUser, id));
    }

    @GetMapping("/{id}/allowed-transitions")
    public ApiResponse<Set<BillStatus>> getAllowedTransitions(@PathVariable Long id) {
        User currentUser = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        BillDto.Response bill = billService.getById(currentUser, id);
        return ApiResponse.success(bill.getAllowedTransitions());
    }

    @GetMapping("/admin/abnormal-transitions")
    public ApiResponse<List<BillDto.StatusHistoryResponse>> getAbnormalTransitions(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime since
    ) {
        User currentUser = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return ApiResponse.success(billService.getAbnormalTransitions(currentUser, since));
    }

    @PostMapping("/admin/migrate-historical")
    public ApiResponse<Integer> migrateHistoricalBills() {
        User currentUser = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        int migrated = billService.migrateHistoricalBills(currentUser);
        return ApiResponse.success("已迁移 " + migrated + " 条历史账单", migrated);
    }
}

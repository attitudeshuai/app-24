package com.electricitysplit.controller;

import com.electricitysplit.dto.ApiResponse;
import com.electricitysplit.dto.MeterReadingCorrectionDto;
import com.electricitysplit.dto.PageResponse;
import com.electricitysplit.entity.User;
import com.electricitysplit.service.MeterReadingCorrectionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/meterreading-corrections")
@RequiredArgsConstructor
public class MeterReadingCorrectionController {

    private final MeterReadingCorrectionService correctionService;

    @PostMapping
    public ApiResponse<MeterReadingCorrectionDto.Response> submitCorrection(
            @Valid @RequestBody MeterReadingCorrectionDto.CreateRequest request) {
        User currentUser = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return ApiResponse.success(correctionService.submitCorrection(currentUser, request));
    }

    @GetMapping("/{id}")
    public ApiResponse<MeterReadingCorrectionDto.Response> getById(@PathVariable Long id) {
        User currentUser = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return ApiResponse.success(correctionService.getById(currentUser, id));
    }

    @GetMapping
    public ApiResponse<PageResponse<MeterReadingCorrectionDto.Response>> list(
            @RequestParam(required = false) Long householdId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        User currentUser = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<MeterReadingCorrectionDto.Response> result;

        if (householdId != null) {
            result = correctionService.listByHousehold(currentUser, householdId, pageable);
        } else {
            result = correctionService.listMyCorrections(currentUser, pageable);
        }
        return ApiResponse.success(PageResponse.from(result));
    }

    @GetMapping("/pending")
    public ApiResponse<PageResponse<MeterReadingCorrectionDto.Response>> listPendingApprovals(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        User currentUser = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<MeterReadingCorrectionDto.Response> result = correctionService.listPendingApprovals(currentUser, pageable);
        return ApiResponse.success(PageResponse.from(result));
    }

    @PostMapping("/{id}/approve")
    public ApiResponse<MeterReadingCorrectionDto.Response> approve(
            @PathVariable Long id,
            @Valid @RequestBody MeterReadingCorrectionDto.ApprovalRequest request
    ) {
        User currentUser = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return ApiResponse.success(correctionService.approveCorrection(currentUser, id, request));
    }

    @PostMapping("/{id}/cancel")
    public ApiResponse<MeterReadingCorrectionDto.Response> cancel(@PathVariable Long id) {
        User currentUser = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return ApiResponse.success(correctionService.cancelCorrection(currentUser, id));
    }

    @PostMapping("/{id}/proof")
    public ApiResponse<MeterReadingCorrectionDto.Response> uploadProof(
            @PathVariable Long id,
            @Valid @RequestBody MeterReadingCorrectionDto.UploadProofRequest request
    ) {
        User currentUser = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return ApiResponse.success(correctionService.uploadProof(currentUser, id, request));
    }
}

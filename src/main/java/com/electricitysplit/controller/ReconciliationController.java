package com.electricitysplit.controller;

import com.electricitysplit.dto.ApiResponse;
import com.electricitysplit.dto.ReconciliationDto;
import com.electricitysplit.entity.User;
import com.electricitysplit.service.ReconciliationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/reconciliation")
@RequiredArgsConstructor
public class ReconciliationController {

    private final ReconciliationService reconciliationService;

    @PostMapping("/perform")
    public ApiResponse<ReconciliationDto.ReportResponse> performReconciliation(
            @Valid @RequestBody ReconciliationDto.ReconcileRequest request
    ) {
        User currentUser = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        ReconciliationDto.ReportResponse response = reconciliationService.performMonthlyReconciliation(currentUser, request);
        return ApiResponse.success("对账完成", response);
    }

    @GetMapping("/reports")
    public ApiResponse<List<ReconciliationDto.ReportResponse>> getAllReports() {
        List<ReconciliationDto.ReportResponse> reports = reconciliationService.getAllReports();
        return ApiResponse.success(reports);
    }

    @GetMapping("/reports/month/{reportMonth}")
    public ApiResponse<ReconciliationDto.ReportResponse> getReportByMonth(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate reportMonth
    ) {
        ReconciliationDto.ReportResponse report = reconciliationService.getReportByMonth(reportMonth);
        return ApiResponse.success(report);
    }

    @GetMapping("/reports/{reportId}/discrepancies")
    public ApiResponse<List<ReconciliationDto.DiscrepancyResponse>> getDiscrepancies(@PathVariable Long reportId) {
        List<ReconciliationDto.DiscrepancyResponse> discrepancies = reconciliationService.getDiscrepanciesByReportId(reportId);
        return ApiResponse.success(discrepancies);
    }

    @GetMapping("/reports/{reportId}/discrepancies/unresolved")
    public ApiResponse<List<ReconciliationDto.DiscrepancyResponse>> getUnresolvedDiscrepancies(@PathVariable Long reportId) {
        List<ReconciliationDto.DiscrepancyResponse> discrepancies = reconciliationService.getUnresolvedDiscrepancies(reportId);
        return ApiResponse.success(discrepancies);
    }

    @PostMapping("/discrepancies/{discrepancyId}/resolve")
    public ApiResponse<ReconciliationDto.DiscrepancyResponse> resolveDiscrepancy(
            @PathVariable Long discrepancyId,
            @RequestBody(required = false) ReconciliationDto.ResolveDiscrepancyRequest request
    ) {
        User currentUser = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (request == null) {
            request = new ReconciliationDto.ResolveDiscrepancyRequest();
        }
        ReconciliationDto.DiscrepancyResponse response = reconciliationService.resolveDiscrepancy(currentUser, discrepancyId, request);
        return ApiResponse.success("差异已处理", response);
    }
}

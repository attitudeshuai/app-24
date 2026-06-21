package com.electricitysplit.controller;

import com.electricitysplit.dto.ApiResponse;
import com.electricitysplit.dto.StatsDto;
import com.electricitysplit.entity.User;
import com.electricitysplit.service.StatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/stats")
@RequiredArgsConstructor
public class StatsController {

    private final StatsService statsService;

    @GetMapping("/overview")
    public ApiResponse<StatsDto.OverviewResponse> getOverview() {
        User currentUser = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return ApiResponse.success(statsService.getOverview(currentUser));
    }

    @GetMapping("/trend")
    public ApiResponse<StatsDto.TrendResponse> getTrend(
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate
    ) {
        User currentUser = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return ApiResponse.success(statsService.getTrend(currentUser, startDate, endDate));
    }
}

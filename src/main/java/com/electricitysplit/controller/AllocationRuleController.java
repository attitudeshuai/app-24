package com.electricitysplit.controller;

import com.electricitysplit.dto.AllocationRuleDto;
import com.electricitysplit.dto.ApiResponse;
import com.electricitysplit.entity.User;
import com.electricitysplit.service.AllocationRuleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/allocation-rules")
@RequiredArgsConstructor
@Tag(name = "分摊规则管理", description = "电费分摊规则的增删改查接口")
public class AllocationRuleController {

    private final AllocationRuleService allocationRuleService;

    @PostMapping
    @Operation(summary = "创建分摊规则")
    public ResponseEntity<ApiResponse<AllocationRuleDto.Response>> create(
            @Valid @RequestBody AllocationRuleDto.CreateRequest createRequest) {
        User user = getCurrentUser();
        AllocationRuleDto.Response response = allocationRuleService.create(user, createRequest);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/{id}")
    @Operation(summary = "获取分摊规则详情")
    public ResponseEntity<ApiResponse<AllocationRuleDto.Response>> getById(
            @PathVariable Long id) {
        User user = getCurrentUser();
        AllocationRuleDto.Response response = allocationRuleService.getById(user, id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/active/{householdId}")
    @Operation(summary = "获取住户当前生效的分摊规则")
    public ResponseEntity<ApiResponse<AllocationRuleDto.Response>> getActive(
            @PathVariable Long householdId) {
        User user = getCurrentUser();
        AllocationRuleDto.Response response = allocationRuleService.getActiveByHouseholdId(user, householdId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/household/{householdId}")
    @Operation(summary = "获取住户所有分摊规则列表")
    public ResponseEntity<ApiResponse<List<AllocationRuleDto.Response>>> list(
            @PathVariable Long householdId) {
        User user = getCurrentUser();
        List<AllocationRuleDto.Response> responses = allocationRuleService.listByHouseholdId(user, householdId);
        return ResponseEntity.ok(ApiResponse.success(responses));
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新分摊规则")
    public ResponseEntity<ApiResponse<AllocationRuleDto.Response>> update(
            @PathVariable Long id,
            @Valid @RequestBody AllocationRuleDto.UpdateRequest updateRequest) {
        User user = getCurrentUser();
        AllocationRuleDto.Response response = allocationRuleService.update(user, id, updateRequest);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PutMapping("/{id}/activate")
    @Operation(summary = "设置分摊规则为生效规则")
    public ResponseEntity<ApiResponse<AllocationRuleDto.Response>> setActive(
            @PathVariable Long id) {
        User user = getCurrentUser();
        AllocationRuleDto.Response response = allocationRuleService.setActive(user, id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除分摊规则")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable Long id) {
        User user = getCurrentUser();
        allocationRuleService.delete(user, id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    private User getCurrentUser() {
        return (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}

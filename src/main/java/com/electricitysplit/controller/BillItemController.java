package com.electricitysplit.controller;

import com.electricitysplit.dto.ApiResponse;
import com.electricitysplit.dto.BillItemDto;
import com.electricitysplit.dto.PageResponse;
import com.electricitysplit.entity.User;
import com.electricitysplit.service.BillItemService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/billitems")
@RequiredArgsConstructor
public class BillItemController {

    private final BillItemService billItemService;

    @GetMapping
    public ApiResponse<PageResponse<BillItemDto.Response>> list(
            @RequestParam(required = false) Long billId,
            @RequestParam(required = false) Long roomId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        User currentUser = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"));
        Page<BillItemDto.Response> result = billItemService.list(currentUser, billId, roomId, pageable);
        return ApiResponse.success(PageResponse.from(result));
    }

    @PostMapping
    public ApiResponse<BillItemDto.Response> create(@Valid @RequestBody BillItemDto.CreateRequest request) {
        User currentUser = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return ApiResponse.success(billItemService.create(currentUser, request));
    }

    @GetMapping("/{id}")
    public ApiResponse<BillItemDto.Response> getById(@PathVariable Long id) {
        User currentUser = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return ApiResponse.success(billItemService.getById(currentUser, id));
    }

    @PutMapping("/{id}")
    public ApiResponse<BillItemDto.Response> update(
            @PathVariable Long id,
            @Valid @RequestBody BillItemDto.UpdateRequest request
    ) {
        User currentUser = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return ApiResponse.success(billItemService.update(currentUser, id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        User currentUser = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        billItemService.delete(currentUser, id);
        return ApiResponse.success("删除成功", null);
    }
}

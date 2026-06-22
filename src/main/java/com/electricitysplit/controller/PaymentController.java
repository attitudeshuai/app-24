package com.electricitysplit.controller;

import com.electricitysplit.dto.ApiResponse;
import com.electricitysplit.dto.PageResponse;
import com.electricitysplit.dto.PaymentDto;
import com.electricitysplit.entity.PaymentRecord;
import com.electricitysplit.entity.User;
import com.electricitysplit.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping
    public ApiResponse<PaymentDto.PaymentRecordResponse> createPayment(
            @Valid @RequestBody PaymentDto.CreatePaymentRequest request
    ) {
        User currentUser = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        PaymentDto.PaymentRecordResponse response = paymentService.createPayment(currentUser, request);
        return ApiResponse.success("支付成功", response);
    }

    @PostMapping("/cancel")
    public ApiResponse<PaymentDto.PaymentRecordResponse> cancelPayment(
            @Valid @RequestBody PaymentDto.CancelPaymentRequest request
    ) {
        User currentUser = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        PaymentDto.PaymentRecordResponse response = paymentService.cancelPayment(currentUser, request);
        return ApiResponse.success("取消支付成功", response);
    }

    @PostMapping("/refund")
    public ApiResponse<PaymentDto.PaymentRecordResponse> refundPayment(
            @Valid @RequestBody PaymentDto.RefundRequest request
    ) {
        User currentUser = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        PaymentDto.PaymentRecordResponse response = paymentService.refundPayment(currentUser, request);
        return ApiResponse.success("退款成功", response);
    }

    @GetMapping("/bill/{billId}")
    public ApiResponse<List<PaymentDto.PaymentRecordResponse>> getPaymentsByBill(@PathVariable Long billId) {
        List<PaymentDto.PaymentRecordResponse> records = paymentService.getPaymentRecordsByBill(billId);
        return ApiResponse.success(records);
    }

    @GetMapping("/bill-item/{billItemId}")
    public ApiResponse<List<PaymentDto.PaymentRecordResponse>> getPaymentsByBillItem(@PathVariable Long billItemId) {
        List<PaymentDto.PaymentRecordResponse> records = paymentService.getPaymentRecordsByBillItem(billItemId);
        return ApiResponse.success(records);
    }

    @GetMapping("/bill/{billId}/summary")
    public ApiResponse<PaymentDto.PaymentSummaryResponse> getPaymentSummary(@PathVariable Long billId) {
        PaymentDto.PaymentSummaryResponse summary = paymentService.getPaymentSummary(billId);
        return ApiResponse.success(summary);
    }
}

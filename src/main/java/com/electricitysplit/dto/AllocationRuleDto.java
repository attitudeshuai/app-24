package com.electricitysplit.dto;

import com.electricitysplit.entity.AllocationType;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public class AllocationRuleDto {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateRequest {
        @NotNull(message = "住户ID不能为空")
        private Long householdId;

        @NotNull(message = "基础电费分摊类型不能为空")
        private AllocationType baseAllocationType;

        @NotNull(message = "空调电费分摊类型不能为空")
        private AllocationType acAllocationType;

        @NotNull(message = "公共区域分摊类型不能为空")
        private AllocationType publicAllocationType;

        private BigDecimal publicRatio;

        private List<RoomCustomRatioDto> roomCustomRatios;

        private Boolean isActive;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateRequest {
        private AllocationType baseAllocationType;
        private AllocationType acAllocationType;
        private AllocationType publicAllocationType;
        private BigDecimal publicRatio;
        private List<RoomCustomRatioDto> roomCustomRatios;
        private Boolean isActive;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RoomCustomRatioDto {
        @NotNull(message = "房间ID不能为空")
        private Long roomId;
        private BigDecimal baseRatio;
        private BigDecimal acRatio;
        private BigDecimal publicRatio;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Response {
        private Long id;
        private Long householdId;
        private String householdName;
        private AllocationType baseAllocationType;
        private AllocationType acAllocationType;
        private AllocationType publicAllocationType;
        private BigDecimal publicRatio;
        private Boolean isActive;
        private List<RoomCustomRatioResponse> roomCustomRatios;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RoomCustomRatioResponse {
        private Long id;
        private Long roomId;
        private String roomName;
        private BigDecimal baseRatio;
        private BigDecimal acRatio;
        private BigDecimal publicRatio;
    }
}

package com.electricitysplit.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class RoomDto {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateRequest {
        @NotNull(message = "住户ID不能为空")
        private Long householdId;

        @NotBlank(message = "房间名称不能为空")
        @Size(max = 50, message = "房间名称不能超过50个字符")
        private String name;

        private BigDecimal area;

        private Long occupantId;

        private Boolean hasAirConditioner;

        private Integer headCount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateRequest {
        @Size(max = 50, message = "房间名称不能超过50个字符")
        private String name;

        private BigDecimal area;

        private Long occupantId;

        private Boolean hasAirConditioner;

        private Integer headCount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Response {
        private Long id;
        private Long householdId;
        private String householdName;
        private String name;
        private BigDecimal area;
        private Long occupantId;
        private String occupantUsername;
        private Boolean hasAirConditioner;

        private Integer headCount;

        private LocalDateTime createdAt;
    }
}

package com.electricitysplit.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

public class HouseholdDto {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateRequest {
        @NotBlank(message = "住户名称不能为空")
        @Size(max = 100, message = "住户名称不能超过100个字符")
        private String name;

        @Size(max = 300, message = "地址不能超过300个字符")
        private String address;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateRequest {
        @Size(max = 100, message = "住户名称不能超过100个字符")
        private String name;

        @Size(max = 300, message = "地址不能超过300个字符")
        private String address;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Response {
        private Long id;
        private String name;
        private String address;
        private Long createdById;
        private String createdByUsername;
        private LocalDateTime createdAt;
    }
}

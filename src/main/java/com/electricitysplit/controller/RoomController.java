package com.electricitysplit.controller;

import com.electricitysplit.dto.ApiResponse;
import com.electricitysplit.dto.PageResponse;
import com.electricitysplit.dto.RoomDto;
import com.electricitysplit.entity.User;
import com.electricitysplit.service.RoomService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@RestController
@RequestMapping("/api/rooms")
@RequiredArgsConstructor
public class RoomController {

    private final RoomService roomService;

    @GetMapping
    public ApiResponse<PageResponse<RoomDto.Response>> list(
            @RequestParam Long householdId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword
    ) {
        User currentUser = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<RoomDto.Response> result = roomService.list(currentUser, householdId, keyword, pageable);
        return ApiResponse.success(PageResponse.from(result));
    }

    @PostMapping
    public ApiResponse<RoomDto.Response> create(@Valid @RequestBody RoomDto.CreateRequest request) {
        User currentUser = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return ApiResponse.success(roomService.create(currentUser, request));
    }

    @GetMapping("/{id}")
    public ApiResponse<RoomDto.Response> getById(@PathVariable Long id) {
        User currentUser = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return ApiResponse.success(roomService.getById(currentUser, id));
    }

    @PutMapping("/{id}")
    public ApiResponse<RoomDto.Response> update(
            @PathVariable Long id,
            @Valid @RequestBody RoomDto.UpdateRequest request
    ) {
        User currentUser = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return ApiResponse.success(roomService.update(currentUser, id, request));
    }

    @GetMapping("/{id}/delete-check")
    public ApiResponse<RoomDto.DeleteCheckResult> checkDeleteConditions(@PathVariable Long id) {
        User currentUser = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        RoomDto.DeleteCheckResult result = roomService.checkDeleteConditions(currentUser, id);
        return ApiResponse.success(result);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id, @RequestBody(required = false) RoomDto.DeleteRequest request) {
        User currentUser = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        String reason = request != null ? request.getReason() : null;
        roomService.delete(currentUser, id, reason);
        return ApiResponse.success("删除成功", null);
    }

    @PostMapping("/{id}/force-delete")
    public ApiResponse<Void> forceDelete(@PathVariable Long id, @RequestBody(required = false) RoomDto.DeleteRequest request) {
        User currentUser = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        String reason = request != null ? request.getReason() : null;
        roomService.forceDelete(currentUser, id, reason);
        return ApiResponse.success("强制删除成功", null);
    }

    @GetMapping("/{id}/export")
    public ApiResponse<RoomDto.RoomDataExport> exportRoomData(@PathVariable Long id) {
        User currentUser = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        RoomDto.RoomDataExport result = roomService.exportRoomData(currentUser, id);
        return ApiResponse.success(result);
    }

    @GetMapping("/{id}/export/download")
    public ResponseEntity<byte[]> exportRoomDataDownload(@PathVariable Long id) {
        try {
            User currentUser = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            RoomDto.RoomDataExport result = roomService.exportRoomData(currentUser, id);

            ObjectMapper objectMapper = new ObjectMapper();
            objectMapper.registerModule(new JavaTimeModule());
            objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

            byte[] data = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(result);

            String fileName = "room_" + id + "_data_" +
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".json";

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                    .contentType(MediaType.APPLICATION_JSON)
                    .contentLength(data.length)
                    .body(data);
        } catch (Exception e) {
            throw new RuntimeException("导出数据失败", e);
        }
    }
}

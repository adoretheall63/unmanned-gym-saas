package com.wrgym.store.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/**
 * §4 门店、营业规则与时段 的 DTO（docs/接口文档.md §4.1–§4.7、§4.13）。
 *
 * <p>注意公开/后台是两套 DTO：公开门店坐标是 JSON number，后台是 string（≤7 位小数）；
 * 公开时段响应不含 etag，后台时段列表项含 etag；营业时间统一 {@code HH:mm:ss}。
 */
public final class StoreDtos {

    private StoreDtos() {
    }

    // ---------- 4.1 / 4.2 公开 ----------

    public record PublicStoreItem(String id, String merchantId, String merchantName, String name,
                                  String address, String status, long slotPriceCent,
                                  Long estimatedOccupancy, String occupancySource, String deviceSource) {
    }

    public record PublicStoreDetail(PublicStoreItem store, String description, List<String> images,
                                    BigDecimal latitude, BigDecimal longitude, String openTime,
                                    String closeTime, boolean is24h, boolean endsNextDay,
                                    String timezone, int slotMinutes, int bookingDays,
                                    long overtimePriceCent, long overtimeCapCent,
                                    boolean admissionPaused, String pauseReason,
                                    int entryGraceMinutes, int exitGraceMinutes) {
    }

    // ---------- 4.3 / 4.13 时段 ----------

    public record PublicSlotItem(String id, String storeId, LocalDateTime startAt, LocalDateTime endAt,
                                 long capacity, long remaining, long priceCent, String status,
                                 boolean bookable, String unavailableReason) {
    }

    public record PublicSlotList(String storeId, LocalDate date, LocalDate lastBookableDate,
                                 List<PublicSlotItem> items) {
    }

    public record AdminSlotItem(String id, String storeId, LocalDateTime startAt, LocalDateTime endAt,
                                long capacity, long remaining, long priceCent, String status,
                                boolean bookable, String unavailableReason, String etag) {
    }

    public record AdminSlotList(String storeId, LocalDate date, LocalDate lastBookableDate,
                                List<AdminSlotItem> items) {
    }

    // ---------- 4.4 / 4.5 / 4.6 / 4.7 后台门店 ----------

    public record AdminStoreVo(String id, String name, String address, String latitude, String longitude,
                               String description, List<String> images, String openTime, String closeTime,
                               boolean is24h, boolean endsNextDay, int capacity, long slotPriceCent,
                               long overtimePriceCent, long overtimeCapCent, String status,
                               boolean admissionPaused, String pauseReason, String etag) {
    }

    /** 4.6 创建门店：创建后固定 CLOSED，启用前需配置设备并生成可售时段。 */
    public record CreateStoreRequest(
            @NotBlank @Size(min = 1, max = 100) String name,
            @NotBlank @Size(min = 1, max = 255) String address,
            @Size(max = 2000) String description,
            List<String> images,
            String latitude,
            String longitude,
            @NotNull LocalTime openTime,
            @NotNull LocalTime closeTime,
            @NotNull Boolean is24h,
            @NotNull Boolean endsNextDay,
            @NotNull @Min(1) @Max(100000) Integer capacity,
            @NotNull @Min(0) Long slotPriceCent,
            @NotNull @Min(0) Long overtimePriceCent,
            @NotNull @Min(0) Long overtimeCapCent,
            @NotBlank @Size(min = 1, max = 500) String reason) {
    }

    /** 4.7 展示资料修改：只接受这些字段；缺省保持原值，显式 null 仅清除可空字段。 */
    public static final List<String> PROFILE_WRITABLE_FIELDS =
            List.of("name", "address", "latitude", "longitude", "description", "images");

    /** 4.7 的冲突/校验失败时便于抛字段错误。 */
    public record FieldIssue(String field, String message) {
        public static FieldIssue of(String field, String message) {
            return new FieldIssue(field, message);
        }
    }

    /** 便于测试与日志记录请求体摘要（不承载业务状态）。 */
    public record ParsedPatch(JsonNode body, String reason) {
    }
}

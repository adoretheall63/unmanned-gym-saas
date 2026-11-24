package com.wrgym.store.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * §4 第二批接口的 DTO：4.8 图片上传、4.9 规则预览、4.10 应用规则、4.11 营业状态、4.12 准入、4.15 调整时段。
 */
public final class StoreRuleDtos {

    private StoreRuleDtos() {
    }

    // ---------- 4.8 ----------

    public record UploadResult(String objectKey, String url, String contentType, long sizeBytes) {
    }

    // ---------- 4.9 / 4.10 规则 ----------

    /** 规则计划（4.9 请求体也包含可选的展示字段；4.10 只含核心规则字段）。 */
    public record RulePlan(
            String name,
            String address,
            String latitude,
            String longitude,
            String description,
            List<String> images,
            @NotNull java.time.LocalTime openTime,
            @NotNull java.time.LocalTime closeTime,
            @NotNull Boolean is24h,
            @NotNull Boolean endsNextDay,
            @NotNull @Min(1) Integer capacity,
            @NotNull @Min(0) Long slotPriceCent,
            @NotNull @Min(0) Long overtimePriceCent,
            @NotNull @Min(0) Long overtimeCapCent) {
    }

    public record RulePreviewRequest(
            String name,
            String address,
            String latitude,
            String longitude,
            String description,
            List<String> images,
            @NotNull java.time.LocalTime openTime,
            @NotNull java.time.LocalTime closeTime,
            @NotNull Boolean is24h,
            @NotNull Boolean endsNextDay,
            @NotNull @Min(1) Integer capacity,
            @NotNull @Min(0) Long slotPriceCent,
            @NotNull @Min(0) Long overtimePriceCent,
            @NotNull @Min(0) Long overtimeCapCent,
            @NotBlank @Size(min = 1, max = 500) String reason) {
    }

    public record RuleConflict(String orderId, String reason) {
    }

    public record RulePreviewResult(String storeId, String previewHash, int affectedUnsoldSlots,
                                    List<RuleConflict> conflicts, int uncoveredMinutes, boolean canApply) {
    }

    /** 4.10 应用规则：核心规则字段 + previewHash + reason（不含 name/address/images）。 */
    public record ApplyRulesRequest(
            @NotNull java.time.LocalTime openTime,
            @NotNull java.time.LocalTime closeTime,
            @NotNull Boolean is24h,
            @NotNull Boolean endsNextDay,
            @NotNull @Min(1) Integer capacity,
            @NotNull @Min(0) Long slotPriceCent,
            @NotNull @Min(0) Long overtimePriceCent,
            @NotNull @Min(0) Long overtimeCapCent,
            @NotBlank String previewHash,
            @NotBlank @Size(min = 1, max = 500) String reason) {
    }

    // ---------- 4.11 / 4.12 ----------

    public record StoreStatusRequest(
            @NotBlank @Pattern(regexp = "^(OPEN|CLOSED|DISABLED)$") String status,
            @NotBlank @Size(min = 1, max = 500) String reason) {
    }

    public record AdmissionRequest(
            @NotNull Boolean paused,
            @NotBlank @Size(min = 1, max = 500) String reason) {
    }

    // ---------- 4.15 ----------

    /** 4.14 的响应只有 accepted。 */
    public record Accepted(boolean accepted) {
    }

    public record SlotAdjustRequest(
            @NotNull @Min(1) Integer capacity,
            @NotNull @Min(0) Long priceCent,
            @NotBlank @Pattern(regexp = "^(OPEN|CLOSED)$") String status,
            @NotBlank @Size(min = 1, max = 500) String reason) {
    }
}

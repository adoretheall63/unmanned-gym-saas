package com.wrgym.store.support;

import com.wrgym.store.entity.StoreSlotEntity;

import java.time.LocalDateTime;

/**
 * 时段展示口径（docs/接口文档.md §4.3 / §4.13）：
 * remaining 是"可预约余量"（capacity - reservedCount），不是实际空位；
 * bookable 要求时段未开始、状态 OPEN、余量大于 0；不可售必须给出原因。
 */
public final class SlotViewSupport {

    private SlotViewSupport() {
    }

    public static long capacity(StoreSlotEntity slot) {
        return slot.getCapacity() == null ? 0 : slot.getCapacity();
    }

    public static long remaining(StoreSlotEntity slot) {
        long reserved = slot.getReservedCount() == null ? 0 : slot.getReservedCount();
        return Math.max(0, capacity(slot) - reserved);
    }

    public static boolean bookable(StoreSlotEntity slot, LocalDateTime nowUtc) {
        return "OPEN".equals(slot.getStatus())
                && slot.getStartAt() != null && slot.getStartAt().isAfter(nowUtc)
                && remaining(slot) > 0;
    }

    public static String unavailableReason(StoreSlotEntity slot, LocalDateTime nowUtc) {
        if (!"OPEN".equals(slot.getStatus())) {
            return "SLOT_CLOSED";
        }
        if (slot.getStartAt() == null || !slot.getStartAt().isAfter(nowUtc)) {
            return "SLOT_STARTED";
        }
        if (remaining(slot) <= 0) {
            return "SLOT_FULL";
        }
        return null;
    }

    /** 后台单时段配置摘要（只含可写配置字段，不含 reservedCount 日常变化）。 */
    public static String etagFor(StoreSlotEntity slot) {
        return com.wrgym.common.web.Etags.of(slot.getCapacity(), slot.getPriceCent(), slot.getStatus());
    }
}

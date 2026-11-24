package com.wrgym.store.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.wrgym.common.entity.BaseEntity;

import java.time.LocalDateTime;

/**
 * 可预约时段（docs/数据库设计.md §1.6 store_slot）。
 *
 * <p>start_at/end_at 为 UTC；capacity 是该时段可售容量，reserved_count 是已占容量（由预约模块写）。
 * 唯一键 (store_id, start_at, end_at) 防重复生成；只允许调整未开始且未售出的时段。
 */
@TableName("store_slot")
public class StoreSlotEntity extends BaseEntity {

    private Long merchantId;
    private Long storeId;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private Integer capacity;
    private Integer reservedCount;
    private Long priceCent;
    private String status;

    public Long getMerchantId() {
        return merchantId;
    }

    public void setMerchantId(Long merchantId) {
        this.merchantId = merchantId;
    }

    public Long getStoreId() {
        return storeId;
    }

    public void setStoreId(Long storeId) {
        this.storeId = storeId;
    }

    public LocalDateTime getStartAt() {
        return startAt;
    }

    public void setStartAt(LocalDateTime startAt) {
        this.startAt = startAt;
    }

    public LocalDateTime getEndAt() {
        return endAt;
    }

    public void setEndAt(LocalDateTime endAt) {
        this.endAt = endAt;
    }

    public Integer getCapacity() {
        return capacity;
    }

    public void setCapacity(Integer capacity) {
        this.capacity = capacity;
    }

    public Integer getReservedCount() {
        return reservedCount;
    }

    public void setReservedCount(Integer reservedCount) {
        this.reservedCount = reservedCount;
    }

    public Long getPriceCent() {
        return priceCent;
    }

    public void setPriceCent(Long priceCent) {
        this.priceCent = priceCent;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}

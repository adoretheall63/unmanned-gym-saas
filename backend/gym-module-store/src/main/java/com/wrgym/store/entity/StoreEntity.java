package com.wrgym.store.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.wrgym.common.entity.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/**
 * 门店与运营规则（docs/数据库设计.md §1.5 store）。
 *
 * <p>时间口径：open_time/close_time 是商家本地时间（HH:mm:ss，Asia/Shanghai），
 * is_24h/ends_next_day 表达跨午夜营业；start/end 事实在 store_slot 中以 UTC 存储。
 * images 只存私有/受控展示材料（JSON 数组），不存人脸或密钥。
 */
@TableName(value = "store", autoResultMap = true)
public class StoreEntity extends BaseEntity {

    private Long merchantId;
    private String name;
    private String address;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private String description;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> images;

    // 属性名含数字，MyBatis-Plus 默认驼峰转下划线不会生成 is_24h，必须显式指定列名
    @TableField("is_24h")
    private Boolean is24h;

    private LocalTime openTime;
    private LocalTime closeTime;
    private Boolean endsNextDay;
    private Integer capacity;
    private Long slotPriceCent;
    private Long overtimePriceCent;
    private Long overtimeCapCent;
    private String status;
    private Boolean admissionPaused;
    private String pauseReason;
    private String lightMode;
    private Integer lightGeneration;
    private String messageHealth;
    private LocalDateTime lastHumanAlarmAt;
    private LocalDateTime lastMessageObservedAt;

    public Long getMerchantId() {
        return merchantId;
    }

    public void setMerchantId(Long merchantId) {
        this.merchantId = merchantId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public BigDecimal getLatitude() {
        return latitude;
    }

    public void setLatitude(BigDecimal latitude) {
        this.latitude = latitude;
    }

    public BigDecimal getLongitude() {
        return longitude;
    }

    public void setLongitude(BigDecimal longitude) {
        this.longitude = longitude;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<String> getImages() {
        return images;
    }

    public void setImages(List<String> images) {
        this.images = images;
    }

    public LocalTime getOpenTime() {
        return openTime;
    }

    public void setOpenTime(LocalTime openTime) {
        this.openTime = openTime;
    }

    public LocalTime getCloseTime() {
        return closeTime;
    }

    public void setCloseTime(LocalTime closeTime) {
        this.closeTime = closeTime;
    }

    public Boolean getIs24h() {
        return is24h;
    }

    public void setIs24h(Boolean is24h) {
        this.is24h = is24h;
    }

    public Boolean getEndsNextDay() {
        return endsNextDay;
    }

    public void setEndsNextDay(Boolean endsNextDay) {
        this.endsNextDay = endsNextDay;
    }

    public Integer getCapacity() {
        return capacity;
    }

    public void setCapacity(Integer capacity) {
        this.capacity = capacity;
    }

    public Long getSlotPriceCent() {
        return slotPriceCent;
    }

    public void setSlotPriceCent(Long slotPriceCent) {
        this.slotPriceCent = slotPriceCent;
    }

    public Long getOvertimePriceCent() {
        return overtimePriceCent;
    }

    public void setOvertimePriceCent(Long overtimePriceCent) {
        this.overtimePriceCent = overtimePriceCent;
    }

    public Long getOvertimeCapCent() {
        return overtimeCapCent;
    }

    public void setOvertimeCapCent(Long overtimeCapCent) {
        this.overtimeCapCent = overtimeCapCent;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Boolean getAdmissionPaused() {
        return admissionPaused;
    }

    public void setAdmissionPaused(Boolean admissionPaused) {
        this.admissionPaused = admissionPaused;
    }

    public String getPauseReason() {
        return pauseReason;
    }

    public void setPauseReason(String pauseReason) {
        this.pauseReason = pauseReason;
    }

    public String getLightMode() {
        return lightMode;
    }

    public void setLightMode(String lightMode) {
        this.lightMode = lightMode;
    }

    public Integer getLightGeneration() {
        return lightGeneration;
    }

    public void setLightGeneration(Integer lightGeneration) {
        this.lightGeneration = lightGeneration;
    }

    public String getMessageHealth() {
        return messageHealth;
    }

    public void setMessageHealth(String messageHealth) {
        this.messageHealth = messageHealth;
    }

    public LocalDateTime getLastHumanAlarmAt() {
        return lastHumanAlarmAt;
    }

    public void setLastHumanAlarmAt(LocalDateTime lastHumanAlarmAt) {
        this.lastHumanAlarmAt = lastHumanAlarmAt;
    }

    public LocalDateTime getLastMessageObservedAt() {
        return lastMessageObservedAt;
    }

    public void setLastMessageObservedAt(LocalDateTime lastMessageObservedAt) {
        this.lastMessageObservedAt = lastMessageObservedAt;
    }
}

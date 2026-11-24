package com.wrgym.store.support;

import com.wrgym.common.api.ErrorCode;
import com.wrgym.common.api.FieldErrorItem;
import com.wrgym.common.exception.BizException;
import com.wrgym.common.web.Etags;
import com.wrgym.store.dto.StoreDtos;
import com.wrgym.store.entity.StoreEntity;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 门店配置的公共计算：ETag、展示字段、营业时间规则校验。
 *
 * <p>ETag 只由**可写配置字段**生成（不含 reservedCount、设备心跳、灯控观察等易变字段），
 * 与 docs/接口文档.md §1.2「摘要由可写配置字段生成，不新增 version 列」一致。
 */
public final class StoreSupport {

    /** 营业时间展示格式。 */
    public static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");
    /** 固定 60 分钟时段、含当天共 7 个自然日（docs/接口文档.md §4.2 / §4.6）。 */
    public static final int SLOT_MINUTES = 60;
    public static final int BOOKING_DAYS = 7;
    public static final String TIMEZONE = "Asia/Shanghai";

    private StoreSupport() {
    }

    public static String timeText(LocalTime time) {
        return time == null ? null : time.format(TIME_FORMAT);
    }

    /** 后台坐标：十进制定点字符串，≤7 位小数（公开接口用 number，另行转换）。 */
    public static String decimalText(BigDecimal value) {
        if (value == null) {
            return null;
        }
        String text = value.stripTrailingZeros().toPlainString();
        return text.contains(".") ? text : text;
    }

    public static String etagFor(StoreEntity store) {
        List<String> images = store.getImages() == null ? List.of() : store.getImages();
        return Etags.of(store.getName(), store.getAddress(), decimalText(store.getLatitude()),
                decimalText(store.getLongitude()), store.getDescription(), String.join(",", images),
                timeText(store.getOpenTime()), timeText(store.getCloseTime()), store.getIs24h(),
                store.getEndsNextDay(), store.getCapacity(), store.getSlotPriceCent(),
                store.getOvertimePriceCent(), store.getOvertimeCapCent(), store.getStatus(),
                store.getAdmissionPaused());
    }

    public static StoreDtos.AdminStoreVo toAdminVo(StoreEntity store) {
        List<String> images = store.getImages() == null ? List.of() : store.getImages();
        return new StoreDtos.AdminStoreVo(String.valueOf(store.getId()), store.getName(), store.getAddress(),
                decimalText(store.getLatitude()), decimalText(store.getLongitude()), store.getDescription(),
                images, timeText(store.getOpenTime()), timeText(store.getCloseTime()),
                Boolean.TRUE.equals(store.getIs24h()), Boolean.TRUE.equals(store.getEndsNextDay()),
                store.getCapacity(), store.getSlotPriceCent(), store.getOvertimePriceCent(),
                store.getOvertimeCapCent(), store.getStatus(), Boolean.TRUE.equals(store.getAdmissionPaused()),
                store.getPauseReason(), etagFor(store));
    }

    /**
     * 营业时间规则（docs/接口文档.md §4.6）：
     * 全天门店 endsNextDay 必须 false；非全天跨日 closeTime&lt;openTime，非跨日 closeTime&gt;openTime。
     */
    public static void validateOpeningRules(LocalTime openTime, LocalTime closeTime,
                                            boolean is24h, boolean endsNextDay) {
        if (is24h && endsNextDay) {
            throw validation("endsNextDay", "全天门店 endsNextDay 必须为 false");
        }
        if (is24h) {
            return;
        }
        if (openTime == null || closeTime == null) {
            throw validation("openTime", "非全天门店必须提供 openTime 与 closeTime");
        }
        if (endsNextDay) {
            if (!closeTime.isBefore(openTime)) {
                throw validation("closeTime", "跨日营业要求 closeTime 早于 openTime");
            }
        } else if (!closeTime.isAfter(openTime)) {
            throw validation("closeTime", "非跨日营业要求 closeTime 晚于 openTime");
        }
    }

    public static BizException validation(String field, String message) {
        return new BizException(ErrorCode.VALIDATION_ERROR, message,
                List.of(new FieldErrorItem(field, message)));
    }
}

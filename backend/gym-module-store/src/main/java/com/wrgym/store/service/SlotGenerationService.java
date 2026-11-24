package com.wrgym.store.service;

import com.wrgym.common.time.TimeZones;
import com.wrgym.store.entity.StoreEntity;
import com.wrgym.store.mapper.StoreSlotMapper;
import com.wrgym.store.support.StoreSupport;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 时段生成（docs/接口文档.md §4.14，并与 §4.10 规则应用共用）。
 *
 * <p>规则：
 * <ul>
 *   <li>只补"含当天共 7 个自然日"内的**完整 60 分钟**时段，不足一小时的尾段跳过；</li>
 *   <li>业务时间按 Asia/Shanghai，存储为 UTC；跨午夜按起始日归档，不截断跨日时段；</li>
 *   <li>以 (store_id, start_at, end_at) 唯一键防重，不改已售时段、不释放承诺；</li>
 *   <li>只生成开始时间仍在未来的时段。</li>
 * </ul>
 */
@Service
public class SlotGenerationService {

    private final StoreSlotMapper storeSlotMapper;

    public SlotGenerationService(StoreSlotMapper storeSlotMapper) {
        this.storeSlotMapper = storeSlotMapper;
    }

    /** @return 实际新增的时段数 */
    public int generate(StoreEntity store, LocalDateTime nowUtc) {
        int capacity = store.getCapacity() == null ? 0 : store.getCapacity();
        int price = store.getSlotPriceCent() == null ? 0 : store.getSlotPriceCent().intValue();
        int inserted = 0;
        LocalDate today = TimeZones.today();
        for (int offset = 0; offset < StoreSupport.BOOKING_DAYS; offset++) {
            LocalDate date = today.plusDays(offset);
            for (LocalDateTime businessStart : slotStarts(store, date)) {
                LocalDateTime businessEnd = businessStart.plusMinutes(StoreSupport.SLOT_MINUTES);
                LocalDateTime startUtc = TimeZones.businessToUtc(businessStart);
                LocalDateTime endUtc = TimeZones.businessToUtc(businessEnd);
                if (!startUtc.isAfter(nowUtc)) {
                    continue;
                }
                inserted += storeSlotMapper.insertIgnore(store.getMerchantId(), store.getId(),
                        startUtc, endUtc, capacity, price);
            }
        }
        return inserted;
    }

    /**
     * 该业务日按门店营业规则可生成的完整小时段起始时间（业务时区）。
     */
    public List<LocalDateTime> slotStarts(StoreEntity store, LocalDate date) {
        boolean is24h = Boolean.TRUE.equals(store.getIs24h());
        LocalDateTime windowStart;
        LocalDateTime windowEnd;
        if (is24h) {
            windowStart = date.atStartOfDay();
            windowEnd = date.plusDays(1).atStartOfDay();
        } else {
            windowStart = LocalDateTime.of(date, store.getOpenTime());
            LocalTime closeTime = store.getCloseTime();
            LocalDate endDate = Boolean.TRUE.equals(store.getEndsNextDay()) ? date.plusDays(1) : date;
            windowEnd = LocalDateTime.of(endDate, closeTime);
        }
        List<LocalDateTime> starts = new ArrayList<>();
        LocalDateTime cursor = windowStart;
        while (!cursor.plusMinutes(StoreSupport.SLOT_MINUTES).isAfter(windowEnd)) {
            starts.add(cursor);
            cursor = cursor.plusMinutes(StoreSupport.SLOT_MINUTES);
        }
        return starts;
    }

    /** 每日营业窗口总分钟数（用于"不足一小时尾段"计算）。 */
    public int windowMinutes(StoreEntity store) {
        if (Boolean.TRUE.equals(store.getIs24h())) {
            return 24 * 60;
        }
        LocalTime open = store.getOpenTime();
        LocalTime close = store.getCloseTime();
        int minutes;
        if (Boolean.TRUE.equals(store.getEndsNextDay())) {
            minutes = (int) java.time.Duration.between(open, close).toMinutes() + 24 * 60;
            if (minutes < 0) {
                minutes += 24 * 60;
            }
        } else {
            minutes = (int) java.time.Duration.between(open, close).toMinutes();
        }
        return Math.max(0, minutes);
    }

    /** 计划中的每日窗口总分钟数（用于 4.9 uncoveredMinutes）。 */
    public int windowMinutes(LocalTime open, LocalTime close, boolean is24h, boolean endsNextDay) {
        if (is24h) {
            return 24 * 60;
        }
        int minutes;
        if (endsNextDay) {
            minutes = (int) java.time.Duration.between(open, close).toMinutes() + 24 * 60;
            if (minutes < 0) {
                minutes += 24 * 60;
            }
        } else {
            minutes = (int) java.time.Duration.between(open, close).toMinutes();
        }
        return Math.max(0, minutes);
    }
}

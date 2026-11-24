package com.wrgym.store.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wrgym.common.api.ErrorCode;
import com.wrgym.common.api.FieldErrorItem;
import com.wrgym.common.api.PageQuery;
import com.wrgym.common.api.PageResult;
import com.wrgym.common.exception.BizException;
import com.wrgym.common.port.MerchantPort;
import com.wrgym.common.time.TimeZones;
import com.wrgym.common.time.UtcTimes;
import com.wrgym.common.web.Ids;
import com.wrgym.store.dto.StoreDtos;
import com.wrgym.store.entity.StoreEntity;
import com.wrgym.store.entity.StoreSlotEntity;
import com.wrgym.store.mapper.StoreMapper;
import com.wrgym.store.mapper.StoreSlotMapper;
import com.wrgym.store.support.SlotViewSupport;
import com.wrgym.store.support.StoreSupport;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

/**
 * 公开门店与时段查询（docs/接口文档.md §4.1–§4.3）。
 *
 * <p>可见性：只展示"启用商家 + OPEN 门店"；详情允许 OPEN/CLOSED，DISABLED 或商家停用一律 404。
 * <p>时段：公开接口只返回**尚未开始**的时段；日期范围限定含当天共 7 个自然日，超范围 422。
 * <p>occupancy/device 来源在对应模块实现前明确返回 UNKNOWN，不伪造估计值。
 */
@Service
public class StoreQueryService {

    private final StoreMapper storeMapper;
    private final StoreSlotMapper storeSlotMapper;
    private final MerchantPort merchantPort;

    public StoreQueryService(StoreMapper storeMapper, StoreSlotMapper storeSlotMapper,
                             MerchantPort merchantPort) {
        this.storeMapper = storeMapper;
        this.storeSlotMapper = storeSlotMapper;
        this.merchantPort = merchantPort;
    }

    /** 4.1 公开营业门店列表（启用商家 + OPEN）。 */
    public PageResult<StoreDtos.PublicStoreItem> publicList(PageQuery query) {
        long total = storeMapper.countPublic();
        List<StoreEntity> stores = storeMapper.listPublic(query.offset(), query.getPageSize());
        Map<Long, MerchantPort.MerchantInfo> merchants = merchantPort.getByIds(
                stores.stream().map(StoreEntity::getMerchantId).distinct().toList());
        List<StoreDtos.PublicStoreItem> items = stores.stream()
                .map(store -> toPublicItem(store, merchants.get(store.getMerchantId())))
                .toList();
        return new PageResult<>(items, total, query.getPage(), query.getPageSize());
    }

    /** 4.2 公开门店详情（不返回 capacity，不返回 etag）。 */
    public StoreDtos.PublicStoreDetail publicDetail(String storeIdRaw) {
        long storeId = Ids.parse(storeIdRaw, "storeId");
        StoreEntity store = storeMapper.selectById(storeId);
        if (store == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        MerchantPort.MerchantInfo merchant = merchantPort.get(store.getMerchantId());
        boolean visible = merchant != null && "ACTIVE".equals(merchant.status())
                && ("OPEN".equals(store.getStatus()) || "CLOSED".equals(store.getStatus()));
        if (!visible) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        return new StoreDtos.PublicStoreDetail(toPublicItem(store, merchant), store.getDescription(),
                store.getImages() == null ? List.of() : store.getImages(), store.getLatitude(),
                store.getLongitude(), StoreSupport.timeText(store.getOpenTime()),
                StoreSupport.timeText(store.getCloseTime()), Boolean.TRUE.equals(store.getIs24h()),
                Boolean.TRUE.equals(store.getEndsNextDay()), StoreSupport.TIMEZONE, StoreSupport.SLOT_MINUTES,
                StoreSupport.BOOKING_DAYS, store.getOvertimePriceCent(), store.getOvertimeCapCent(),
                Boolean.TRUE.equals(store.getAdmissionPaused()), store.getPauseReason(), 0, 0);
    }

    /** 4.3 公开时段：只返回尚未开始时段，日期必须在含今天的 7 个自然日内。 */
    public StoreDtos.PublicSlotList publicSlots(String storeIdRaw, String dateRaw) {
        long storeId = Ids.parse(storeIdRaw, "storeId");
        LocalDate date = parseDate(dateRaw);
        LocalDate today = TimeZones.today();
        LocalDate lastBookable = today.plusDays(StoreSupport.BOOKING_DAYS - 1);
        if (date.isBefore(today) || date.isAfter(lastBookable)) {
            throw new BizException(ErrorCode.INVALID_TIME_RANGE, "只可查询含当天共 7 个自然日的时段");
        }
        StoreEntity store = visibleStore(storeId);
        List<StoreSlotEntity> slots = storeSlotMapper.listByRange(store.getMerchantId(), storeId,
                TimeZones.businessDayStartUtc(date), TimeZones.businessDayEndUtc(date));
        var now = UtcTimes.now();
        List<StoreDtos.PublicSlotItem> items = slots.stream()
                .filter(slot -> slot.getStartAt() != null && slot.getStartAt().isAfter(now))
                .map(slot -> new StoreDtos.PublicSlotItem(String.valueOf(slot.getId()),
                        String.valueOf(slot.getStoreId()), slot.getStartAt(), slot.getEndAt(),
                        SlotViewSupport.capacity(slot), SlotViewSupport.remaining(slot),
                        slot.getPriceCent(), slot.getStatus(), SlotViewSupport.bookable(slot, now),
                        SlotViewSupport.unavailableReason(slot, now)))
                .toList();
        return new StoreDtos.PublicSlotList(String.valueOf(storeId), date, lastBookable, items);
    }

    /** 4.13 后台时段：包括已开始与关闭时段；不触发生成。 */
    public StoreDtos.AdminSlotList adminSlots(String storeIdRaw, String dateRaw) {
        com.wrgym.security.auth.SecurityUtils.requirePermission(com.wrgym.common.security.PermissionCodes.STORE_READ);
        long merchantId = com.wrgym.security.auth.SecurityUtils.currentMerchantId();
        long storeId = Ids.parse(storeIdRaw, "storeId");
        LocalDate date = parseDate(dateRaw);
        StoreEntity store = storeMapper.selectTenant(storeId, merchantId);
        if (store == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        List<StoreSlotEntity> slots = storeSlotMapper.listByRange(merchantId, storeId,
                TimeZones.businessDayStartUtc(date), TimeZones.businessDayEndUtc(date));
        var now = UtcTimes.now();
        List<StoreDtos.AdminSlotItem> items = slots.stream()
                .map(slot -> new StoreDtos.AdminSlotItem(String.valueOf(slot.getId()),
                        String.valueOf(slot.getStoreId()), slot.getStartAt(), slot.getEndAt(),
                        SlotViewSupport.capacity(slot), SlotViewSupport.remaining(slot),
                        slot.getPriceCent(), slot.getStatus(), SlotViewSupport.bookable(slot, now),
                        SlotViewSupport.unavailableReason(slot, now), SlotViewSupport.etagFor(slot)))
                .toList();
        LocalDate lastBookable = TimeZones.today().plusDays(StoreSupport.BOOKING_DAYS - 1);
        return new StoreDtos.AdminSlotList(String.valueOf(storeId), date, lastBookable, items);
    }

    private StoreEntity visibleStore(long storeId) {
        StoreEntity store = storeMapper.selectById(storeId);
        if (store == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        MerchantPort.MerchantInfo merchant = merchantPort.get(store.getMerchantId());
        if (merchant == null || !"ACTIVE".equals(merchant.status())
                || !("OPEN".equals(store.getStatus()) || "CLOSED".equals(store.getStatus()))) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        return store;
    }

    private StoreDtos.PublicStoreItem toPublicItem(StoreEntity store, MerchantPort.MerchantInfo merchant) {
        return new StoreDtos.PublicStoreItem(String.valueOf(store.getId()),
                String.valueOf(store.getMerchantId()), merchant == null ? null : merchant.name(),
                store.getName(), store.getAddress(), store.getStatus(),
                store.getSlotPriceCent() == null ? 0L : store.getSlotPriceCent(),
                null, "UNKNOWN", "UNKNOWN");
    }

    private LocalDate parseDate(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "date 必填",
                    List.of(new FieldErrorItem("date", "格式 yyyy-MM-dd")));
        }
        try {
            return LocalDate.parse(raw.trim());
        } catch (DateTimeParseException ex) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "date 格式不合法",
                    List.of(new FieldErrorItem("date", "格式 yyyy-MM-dd")));
        }
    }
}

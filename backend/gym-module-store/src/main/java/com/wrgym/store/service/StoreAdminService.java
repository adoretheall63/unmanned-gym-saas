package com.wrgym.store.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.wrgym.common.api.ErrorCode;
import com.wrgym.common.api.FieldErrorItem;
import com.wrgym.common.api.PageQuery;
import com.wrgym.common.api.PageResult;
import com.wrgym.common.audit.AuditWriter;
import com.wrgym.common.exception.BizException;
import com.wrgym.common.idempotency.IdempotencyService;
import com.wrgym.common.time.UtcTimes;
import com.wrgym.common.web.Etags;
import com.wrgym.common.web.Ids;
import com.wrgym.store.dto.StoreDtos;
import com.wrgym.store.entity.StoreEntity;
import com.wrgym.store.mapper.StoreMapper;
import com.wrgym.store.support.StoreSupport;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 后台门店管理（docs/接口文档.md §4.4–§4.7）。
 *
 * <p>关键约束：
 * <ul>
 *   <li>查询需要 STORE_READ；创建/修改需要 ADMIN（4.6/4.7）。</li>
 *   <li>创建后固定 CLOSED；固定 60 分钟时段、含当天 7 个自然日、宽限 0。</li>
 *   <li>4.7 必须带 Idempotency-Key 与 If-Match；锁内用当前字段重算 ETag 再比对。</li>
 *   <li>reason 必填并写审计；图片只能引用本商家私有对象键；本接口不改时段与计费。</li>
 * </ul>
 */
@Service
public class StoreAdminService {

    private final StoreMapper storeMapper;
    private final AuditWriter auditWriter;
    private final IdempotencyService idempotencyService;

    public StoreAdminService(StoreMapper storeMapper, AuditWriter auditWriter,
                             IdempotencyService idempotencyService) {
        this.storeMapper = storeMapper;
        this.auditWriter = auditWriter;
        this.idempotencyService = idempotencyService;
    }

    /** 4.4 后台门店列表（按 JWT 商家过滤，可选状态筛选）。 */
    public PageResult<StoreDtos.AdminStoreVo> list(PageQuery query, String status) {
        com.wrgym.security.auth.SecurityUtils.requirePermission(
                com.wrgym.common.security.PermissionCodes.STORE_READ);
        long merchantId = com.wrgym.security.auth.SecurityUtils.currentMerchantId();
        int page = Math.max(1, query.getPage());
        int pageSize = Math.min(Math.max(1, query.getPageSize()), 100);
        LambdaQueryWrapper<StoreEntity> wrapper = new LambdaQueryWrapper<StoreEntity>()
                .eq(StoreEntity::getMerchantId, merchantId)
                .eq(status != null && !status.isBlank(), StoreEntity::getStatus, status)
                .orderByAsc(StoreEntity::getId);
        Page<StoreEntity> result = storeMapper.selectPage(Page.of(page, pageSize), wrapper);
        List<StoreDtos.AdminStoreVo> items = result.getRecords().stream()
                .map(StoreSupport::toAdminVo).toList();
        return new PageResult<>(items, result.getTotal(), page, pageSize);
    }

    /** 4.5 后台门店详情（资源归属校验，返回 ETag）。 */
    public StoreDtos.AdminStoreVo detail(String storeIdRaw) {
        com.wrgym.security.auth.SecurityUtils.requirePermission(
                com.wrgym.common.security.PermissionCodes.STORE_READ);
        long merchantId = com.wrgym.security.auth.SecurityUtils.currentMerchantId();
        long storeId = Ids.parse(storeIdRaw, "storeId");
        StoreEntity store = storeMapper.selectTenant(storeId, merchantId);
        if (store == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        return StoreSupport.toAdminVo(store);
    }

    /** 4.6 创建门店：201，创建后固定 CLOSED，审计与幂等同事务。 */
    public StoreDtos.AdminStoreVo create(StoreDtos.CreateStoreRequest request, String idempotencyKey) {
        com.wrgym.security.auth.SecurityUtils.requireAdminRole();
        long merchantId = com.wrgym.security.auth.SecurityUtils.currentMerchantId();
        long adminId = com.wrgym.security.auth.SecurityUtils.currentAdminId();
        StoreSupport.validateOpeningRules(request.openTime(), request.closeTime(),
                Boolean.TRUE.equals(request.is24h()), Boolean.TRUE.equals(request.endsNextDay()));
        List<String> images = validateImages(request.images(), merchantId);
        BigDecimal latitude = parseCoordinate(request.latitude(), "latitude", -90, 90);
        BigDecimal longitude = parseCoordinate(request.longitude(), "longitude", -180, 180);
        String key = idempotencyService.requireKey(idempotencyKey);
        String requestBody = String.join("\n", request.name(), request.address(),
                String.valueOf(request.openTime()), String.valueOf(request.closeTime()),
                String.valueOf(request.is24h()), String.valueOf(request.endsNextDay()),
                String.valueOf(request.capacity()), String.valueOf(request.slotPriceCent()),
                String.valueOf(request.overtimePriceCent()), String.valueOf(request.overtimeCapCent()),
                String.valueOf(images), request.reason());
        return idempotencyService.execute("admin.store.create", key, requestBody,
                StoreDtos.AdminStoreVo.class, () -> {
                    StoreEntity store = new StoreEntity();
                    store.setMerchantId(merchantId);
                    store.setName(request.name());
                    store.setAddress(request.address());
                    store.setDescription(request.description());
                    store.setImages(images);
                    store.setLatitude(latitude);
                    store.setLongitude(longitude);
                    store.setOpenTime(request.openTime());
                    store.setCloseTime(request.closeTime());
                    store.setIs24h(Boolean.TRUE.equals(request.is24h()));
                    store.setEndsNextDay(Boolean.TRUE.equals(request.endsNextDay()));
                    store.setCapacity(request.capacity());
                    store.setSlotPriceCent(request.slotPriceCent());
                    store.setOvertimePriceCent(request.overtimePriceCent());
                    store.setOvertimeCapCent(request.overtimeCapCent());
                    // 创建即 CLOSED；启用前需配置设备并生成可售时段
                    store.setStatus("CLOSED");
                    store.setAdmissionPaused(false);
                    storeMapper.insert(store);
                    auditWriter.record(merchantId, "STORE_CREATED", "STORE", store.getId(), adminId,
                            request.reason(), Map.of("name", request.name(), "address", request.address()));
                    return StoreSupport.toAdminVo(storeMapper.selectById(store.getId()));
                });
    }

    /**
     * 4.7 修改门店展示资料：只接受 name/address/latitude/longitude/description/images；
     * 缺省保持原值，显式 null 仅清除可空字段；必须 reason；幂等 + If-Match。
     */
    public StoreDtos.AdminStoreVo updateProfile(String storeIdRaw, String ifMatch, String idempotencyKey,
                                                JsonNode body) {
        com.wrgym.security.auth.SecurityUtils.requireAdminRole();
        long merchantId = com.wrgym.security.auth.SecurityUtils.currentMerchantId();
        long adminId = com.wrgym.security.auth.SecurityUtils.currentAdminId();
        long storeId = Ids.parse(storeIdRaw, "storeId");
        String reason = requireReason(body);
        List<String> present = presentProfileFields(body);
        if (present.isEmpty()) {
            throw StoreSupport.validation("body", "reason 之外至少提供一个可修改字段");
        }
        String key = idempotencyService.requireKey(idempotencyKey);
        String requestBody = storeId + "\n" + body.toString();
        return idempotencyService.execute("admin.store.profile", key, requestBody,
                StoreDtos.AdminStoreVo.class, () -> {
                    StoreEntity locked = storeMapper.selectTenantForUpdate(storeId, merchantId);
                    if (locked == null) {
                        throw new BizException(ErrorCode.NOT_FOUND);
                    }
                    // 锁内用当前字段重算 ETag 再比对
                    Etags.verify(ifMatch, StoreSupport.etagFor(locked));

                    LambdaUpdateWrapper<StoreEntity> update = new LambdaUpdateWrapper<StoreEntity>()
                            .eq(StoreEntity::getId, storeId)
                            .eq(StoreEntity::getMerchantId, merchantId);
                    Map<String, Object> before = new LinkedHashMap<>();
                    Map<String, Object> after = new LinkedHashMap<>();

                    if (body.has("name")) {
                        String name = requireText(body.get("name"), "name", 1, 100);
                        before.put("name", locked.getName());
                        after.put("name", name);
                        update.set(StoreEntity::getName, name);
                    }
                    if (body.has("address")) {
                        String address = requireText(body.get("address"), "address", 1, 255);
                        before.put("address", locked.getAddress());
                        after.put("address", address);
                        update.set(StoreEntity::getAddress, address);
                    }
                    if (body.has("latitude")) {
                        BigDecimal latitude = body.get("latitude").isNull() ? null
                                : parseCoordinateNode(body.get("latitude"), "latitude", -90, 90);
                        before.put("latitude", StoreSupport.decimalText(locked.getLatitude()));
                        after.put("latitude", StoreSupport.decimalText(latitude));
                        update.set(StoreEntity::getLatitude, latitude);
                    }
                    if (body.has("longitude")) {
                        BigDecimal longitude = body.get("longitude").isNull() ? null
                                : parseCoordinateNode(body.get("longitude"), "longitude", -180, 180);
                        before.put("longitude", StoreSupport.decimalText(locked.getLongitude()));
                        after.put("longitude", StoreSupport.decimalText(longitude));
                        update.set(StoreEntity::getLongitude, longitude);
                    }
                    if (body.has("description")) {
                        String description = body.get("description").isNull() ? null
                                : requireText(body.get("description"), "description", 0, 2000);
                        before.put("description", locked.getDescription());
                        after.put("description", description);
                        update.set(StoreEntity::getDescription, description);
                    }
                    if (body.has("images")) {
                        List<String> images = body.get("images").isNull() ? List.of()
                                : validateImagesNode(body.get("images"), merchantId);
                        before.put("images", locked.getImages() == null ? List.of() : locked.getImages());
                        after.put("images", images);
                        // JSON 列走实体更新，才能套用 JacksonTypeHandler；wrapper.set 会按 binary 绑定而报错
                        StoreEntity imagesPatch = new StoreEntity();
                        imagesPatch.setId(storeId);
                        imagesPatch.setImages(images);
                        storeMapper.updateById(imagesPatch);
                    }
                    update.set(StoreEntity::getUpdatedAt, UtcTimes.now());
                    storeMapper.update(null, update);
                    auditWriter.record(merchantId, "STORE_PROFILE_CHANGED", "STORE", storeId, adminId,
                            reason, Map.of("before", before, "after", after));
                    return StoreSupport.toAdminVo(storeMapper.selectById(storeId));
                });
    }

    // ---------- 校验与解析 ----------

    private String requireReason(JsonNode body) {
        if (body == null || !body.hasNonNull("reason")) {
            throw StoreSupport.validation("reason", "reason 必填（1–500 字符）");
        }
        return requireText(body.get("reason"), "reason", 1, 500);
    }

    private List<String> presentProfileFields(JsonNode body) {
        List<String> present = new ArrayList<>();
        if (body == null) {
            return present;
        }
        Iterator<String> names = body.fieldNames();
        while (names.hasNext()) {
            String field = names.next();
            if (StoreDtos.PROFILE_WRITABLE_FIELDS.contains(field)) {
                present.add(field);
            }
        }
        return present;
    }

    private String requireText(JsonNode node, String field, int min, int max) {
        if (node == null || !node.isTextual()) {
            throw StoreSupport.validation(field, "必须是字符串");
        }
        String value = node.asText();
        if (value.length() < min) {
            throw StoreSupport.validation(field, "长度至少 " + min);
        }
        if (value.length() > max) {
            throw StoreSupport.validation(field, "长度最多 " + max);
        }
        return value;
    }

    private List<String> validateImages(List<String> images, long merchantId) {
        if (images == null) {
            return List.of();
        }
        if (images.size() > 20) {
            throw StoreSupport.validation("images", "最多 20 张图片");
        }
        for (String key : images) {
            if (key == null || !key.startsWith("stores/" + merchantId + "/")) {
                throw new BizException(ErrorCode.VALIDATION_ERROR, "图片必须属于本商家私有对象键",
                        List.of(new FieldErrorItem("images", "拒绝任意外链，请先上传后再引用")));
            }
        }
        return List.copyOf(images);
    }

    private List<String> validateImagesNode(JsonNode node, long merchantId) {
        if (!node.isArray()) {
            throw StoreSupport.validation("images", "必须是字符串数组");
        }
        List<String> images = new ArrayList<>();
        for (JsonNode item : node) {
            if (!item.isTextual()) {
                throw StoreSupport.validation("images", "必须是字符串数组");
            }
            images.add(item.asText());
        }
        return validateImages(images, merchantId);
    }

    private BigDecimal parseCoordinate(String raw, String field, int min, int max) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return validateCoordinate(new BigDecimal(raw.trim()), field, min, max);
        } catch (NumberFormatException ex) {
            throw StoreSupport.validation(field, "必须是十进制定点数");
        }
    }

    private BigDecimal parseCoordinateNode(JsonNode node, String field, int min, int max) {
        if (!node.isNumber() && !node.isTextual()) {
            throw StoreSupport.validation(field, "必须是数字或十进制字符串");
        }
        try {
            return validateCoordinate(new BigDecimal(node.asText()), field, min, max);
        } catch (NumberFormatException ex) {
            throw StoreSupport.validation(field, "必须是十进制定点数");
        }
    }

    private BigDecimal validateCoordinate(BigDecimal value, String field, int min, int max) {
        if (value.compareTo(BigDecimal.valueOf(min)) < 0 || value.compareTo(BigDecimal.valueOf(max)) > 0) {
            throw StoreSupport.validation(field, "超出范围 [" + min + ", " + max + "]");
        }
        if (value.scale() > 7) {
            throw StoreSupport.validation(field, "最多 7 位小数");
        }
        return value;
    }
}

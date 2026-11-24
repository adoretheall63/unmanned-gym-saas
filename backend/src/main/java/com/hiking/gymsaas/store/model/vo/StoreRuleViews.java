package com.hiking.gymsaas.store.model.vo;

import java.util.List;

/** §4.8–§4.15 视图。 */
public final class StoreRuleViews {
    private StoreRuleViews() { }

    /**
     * 4.8 上传结果。
     * 字段名用 imageKey 而非 objectKey：通用幂等/审计脱敏规则把 objectKey 视为敏感键，
     * 禁止进入重放快照；值仍是对象键，4.7 的 images 直接引用该字符串。
     */
    public record Upload(String imageKey, String url, String contentType, long sizeBytes) { }

    /** 4.9 冲突项（已售订单）。 */
    public record Conflict(String orderId, String reason) { }

    /** 4.9 预览结果；previewHash 是方案+当前承诺量摘要，不是授权凭据。 */
    public record RulePreview(String storeId, String previewHash, int affectedUnsoldSlots,
                              List<Conflict> conflicts, int uncoveredMinutes, boolean canApply) { }

    /** 4.14 受理结果。 */
    public record Accepted(boolean accepted) { }
}

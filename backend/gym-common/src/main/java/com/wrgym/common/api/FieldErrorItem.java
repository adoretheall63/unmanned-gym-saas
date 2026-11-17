package com.wrgym.common.api;

/**
 * 失败响应的字段级错误（docs/接口文档.md §1.4 fieldErrors[]）。
 *
 * @param field   错误字段路径，例如 {@code data.startAt}、{@code query.pageSize}
 * @param message 校验原因，可直接展示
 */
public record FieldErrorItem(String field, String message) {
}

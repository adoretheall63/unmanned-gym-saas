package com.wrgym.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * 分页响应（docs/接口文档.md §1.1）：无结果时 {@code items=[]}、{@code total=0}。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PageResult<T>(List<T> items, long total, int page, int pageSize) {

    public static <T> PageResult<T> of(List<T> items, long total, PageQuery query) {
        return new PageResult<>(items == null ? List.of() : items, total, query.getPage(), query.getPageSize());
    }

    public static <T> PageResult<T> empty(PageQuery query) {
        return new PageResult<>(List.of(), 0L, query.getPage(), query.getPageSize());
    }
}

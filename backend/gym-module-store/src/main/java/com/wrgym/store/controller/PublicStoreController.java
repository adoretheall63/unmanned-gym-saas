package com.wrgym.store.controller;

import com.wrgym.common.api.PageQuery;
import com.wrgym.common.api.PageResult;
import com.wrgym.common.api.R;
import com.wrgym.store.dto.StoreDtos;
import com.wrgym.store.service.StoreQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 顾客端公开门店与时段（docs/接口文档.md §4.1–§4.3，无需登录）。
 */
@RestController
@RequestMapping("/api/v1/app/stores")
public class PublicStoreController {

    private final StoreQueryService storeQueryService;

    public PublicStoreController(StoreQueryService storeQueryService) {
        this.storeQueryService = storeQueryService;
    }

    @GetMapping
    public R<PageResult<StoreDtos.PublicStoreItem>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        PageQuery query = new PageQuery();
        query.setPage(page);
        query.setPageSize(pageSize);
        return R.ok(storeQueryService.publicList(query));
    }

    @GetMapping("/{storeId}")
    public R<StoreDtos.PublicStoreDetail> detail(@PathVariable String storeId) {
        return R.ok(storeQueryService.publicDetail(storeId));
    }

    @GetMapping("/{storeId}/slots")
    public R<StoreDtos.PublicSlotList> slots(@PathVariable String storeId,
                                             @RequestParam String date) {
        return R.ok(storeQueryService.publicSlots(storeId, date));
    }
}

package com.hiking.gymsaas.common.page;

import com.baomidou.mybatisplus.core.metadata.IPage;
import java.util.List;

public record PageResult<T>(long page, long pageSize, long total, List<T> items) {
    public PageResult {
        items = List.copyOf(items);
    }

    public static <T> PageResult<T> from(IPage<T> page) {
        return new PageResult<>(page.getCurrent(), page.getSize(), page.getTotal(), page.getRecords());
    }
}

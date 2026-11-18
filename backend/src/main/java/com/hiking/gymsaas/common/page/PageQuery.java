package com.hiking.gymsaas.common.page;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PageQuery {
    @NotNull @Min(1)
    private Integer page = 1;

    @NotNull @Min(1) @Max(100)
    private Integer pageSize = 20;

    public <T> Page<T> toPage() {
        return new Page<>(page, pageSize);
    }
}

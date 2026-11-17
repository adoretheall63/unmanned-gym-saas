package com.wrgym.common.api;

/**
 * 分页请求参数（docs/接口文档.md §1.1）。
 *
 * <p>page 从 1 开始，pageSize 默认 20、上限 100。Controller 用 {@code @Valid} 绑定后传入 Service。
 */
public class PageQuery {

    public static final int DEFAULT_PAGE = 1;
    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 100;

    private int page = DEFAULT_PAGE;
    private int pageSize = DEFAULT_PAGE_SIZE;

    public int getPage() {
        return page;
    }

    public void setPage(Integer page) {
        this.page = page == null || page < 1 ? DEFAULT_PAGE : page;
    }

    public int getPageSize() {
        return pageSize;
    }

    public void setPageSize(Integer pageSize) {
        if (pageSize == null || pageSize < 1) {
            this.pageSize = DEFAULT_PAGE_SIZE;
        } else {
            this.pageSize = Math.min(pageSize, MAX_PAGE_SIZE);
        }
    }

    /** 转换为 MyBatis-Plus 分页偏移量。 */
    public long offset() {
        return (long) (page - 1) * pageSize;
    }
}

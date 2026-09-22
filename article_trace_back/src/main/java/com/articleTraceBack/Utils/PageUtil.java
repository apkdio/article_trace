package com.articleTraceBack.Utils;

/** 分页参数归一化：{@code pageNum} 与 {@code pageSize} 都收敛到合法区间，避免负数或过大值生成非法 SQL、或把整表拉进内存。 */
public final class PageUtil {

    /** 未指定或非法时的默认每页条数 */
    public static final int DEFAULT_PAGE_SIZE = 10;

    /** 每页条数上限，防止单次查询拉取过多数据 */
    public static final int MAX_PAGE_SIZE = 100;

    private PageUtil() {
    }

    /** 页码小于 1 时归一到第 1 页 */
    public static int normalizePageNum(int pageNum) {
        return pageNum < 1 ? 1 : pageNum;
    }

    /** 每页条数非法时取默认值，过大时截到上限 */
    public static int normalizePageSize(int pageSize) {
        if (pageSize < 1) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }
}

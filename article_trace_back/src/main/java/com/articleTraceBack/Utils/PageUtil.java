package com.articleTraceBack.Utils;

/**
 * 分页参数归一化。
 *
 * <p>此前只有 {@code pageNum} 做了 {@code <= 0} 保护，{@code pageSize} 完全没校验：
 * 传 {@code -1} 会让 {@code pageTotal} 变成负数，进而算出 {@code limit x,-1} 这种非法 SQL；
 * 传 {@code 0} 则会被 {@code Math.ceil} 放大成 {@code Integer.MAX_VALUE}。
 * {@code pageSize} 过大同样危险——一次查询把整表拉进内存。这里统一收敛。</p>
 */
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

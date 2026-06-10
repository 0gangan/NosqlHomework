package com.example.Nosql_Homework.common;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Page;

import java.util.List;

/**
 * 分页结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PageResult<T> {

    /** 总记录数 */
    private long total;

    /** 当前页码 (1-based) */
    private int page;

    /** 每页条数 */
    private int size;

    /** 数据列表 */
    private List<T> records;

    // ========== 静态工厂 ==========

    public static <T> PageResult<T> of(Page<T> page) {
        return PageResult.<T>builder()
                .total(page.getTotalElements())
                .page(page.getNumber() + 1)   // Spring Page 从 0 开始，转成 1-based
                .size(page.getSize())
                .records(page.getContent())
                .build();
    }

    public static <T> PageResult<T> of(long total, int page, int size, List<T> records) {
        return PageResult.<T>builder()
                .total(total)
                .page(page)
                .size(size)
                .records(records)
                .build();
    }
}

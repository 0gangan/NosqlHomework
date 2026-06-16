package com.example.Nosql_Homework.crawler.exception;

/**
 * 爬虫统一异常基类
 */
public class CrawlerException extends RuntimeException {

    private final int code;

    public CrawlerException(String message) {
        super(message);
        this.code = 500;
    }

    public CrawlerException(int code, String message) {
        super(message);
        this.code = code;
    }

    public CrawlerException(int code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}

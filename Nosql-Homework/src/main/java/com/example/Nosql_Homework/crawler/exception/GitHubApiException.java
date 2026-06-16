package com.example.Nosql_Homework.crawler.exception;

/**
 * GitHub API 调用异常
 */
public class GitHubApiException extends CrawlerException {

    private final int httpStatus;

    public GitHubApiException(int httpStatus, String message) {
        super(httpStatus, message);
        this.httpStatus = httpStatus;
    }

    public GitHubApiException(int httpStatus, String message, Throwable cause) {
        super(httpStatus, message, cause);
        this.httpStatus = httpStatus;
    }

    public int getHttpStatus() {
        return httpStatus;
    }
}

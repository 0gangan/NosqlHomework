package com.example.Nosql_Homework.exception;

import com.example.Nosql_Homework.common.R;
import com.example.Nosql_Homework.crawler.exception.CrawlerException;
import com.example.Nosql_Homework.crawler.exception.GitHubApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * 全局统一异常处理器
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // ======================== 爬虫异常 ========================

    @ExceptionHandler(GitHubApiException.class)
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    public R<Void> handleGitHubApi(GitHubApiException e) {
        log.error("GitHub API 异常: [{}] {}", e.getHttpStatus(), e.getMessage());
        return R.fail(e.getCode(), "GitHub API 错误: " + e.getMessage());
    }

    @ExceptionHandler(CrawlerException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public R<Void> handleCrawler(CrawlerException e) {
        log.error("爬虫异常: {}", e.getMessage(), e);
        return R.fail(e.getCode(), "爬虫异常: " + e.getMessage());
    }

    // ======================== 参数校验 ========================

    @ExceptionHandler(MissingServletRequestParameterException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public R<Void> handleMissingParam(MissingServletRequestParameterException e) {
        log.warn("缺少请求参数: {}", e.getMessage());
        return R.fail(400, "缺少必要参数: " + e.getParameterName());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public R<Void> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        log.warn("参数类型不匹配: {} 期望类型 {}", e.getName(), e.getRequiredType());
        return R.fail(400, "参数类型错误: " + e.getName());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public R<Void> handleMessageNotReadable(HttpMessageNotReadableException e) {
        log.warn("请求体不可读: {}", e.getMessage());
        return R.fail(400, "请求体格式错误");
    }

    // ======================== 兜底 ========================

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public R<Void> handleIllegalArg(IllegalArgumentException e) {
        log.warn("非法参数: {}", e.getMessage());
        return R.fail(400, "参数错误: " + e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public R<Void> handleAll(Exception e) {
        log.error("未捕获异常: {}", e.getMessage(), e);
        return R.fail(500, "服务器内部错误, 请稍后重试");
    }
}

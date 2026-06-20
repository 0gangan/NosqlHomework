package com.example.Nosql_Homework.config;

import com.example.Nosql_Homework.crawler.CrawlerJob;
import org.quartz.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class QuartzConfig {

    @Bean
    public JobDetail crawlerJobDetail() {
        return JobBuilder.newJob(CrawlerJob.class)
                .withIdentity("crawlerJob")
                .storeDurably()
                .build();
    }

    /**
     * Cron: 每天凌晨 02:00 执行
     * 调试时可改为 "0 * * * * ?" (每分钟) 快速验证
     */
    @Bean
    public Trigger crawlerTrigger() {
        return TriggerBuilder.newTrigger()
                .forJob(crawlerJobDetail())
                .withIdentity("crawlerTrigger")
                .withSchedule(CronScheduleBuilder.cronSchedule("0 0 2 * * ?"))
                .build();
    }
}

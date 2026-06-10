package com.example.Nosql_Homework.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI nosqlHomeworkOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("NoSQL Homework API")
                        .description("基于 MongoDB 的 GitHub 数据爬取与语义搜索系统 API 文档")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("开发者")
                                .email("dev@example.com"))
                        .license(new License()
                                .name("Apache 2.0")
                                .url("https://www.apache.org/licenses/LICENSE-2.0")));
    }
}

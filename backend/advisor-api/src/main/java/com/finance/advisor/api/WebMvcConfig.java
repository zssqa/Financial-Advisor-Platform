package com.finance.advisor.api;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 将 /charts/** 映射到项目根目录的 charts/ 文件夹
        registry.addResourceHandler("/charts/**")
                .addResourceLocations("file:charts/");
    }
}

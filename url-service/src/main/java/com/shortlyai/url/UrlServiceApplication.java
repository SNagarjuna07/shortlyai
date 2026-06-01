package com.shortlyai.url;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

// @EnableFeignClients — scans for @FeignClient interfaces (calls to ai-service)
@SpringBootApplication
@EnableScheduling
@EnableFeignClients
public class UrlServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(UrlServiceApplication.class, args);
    }

}

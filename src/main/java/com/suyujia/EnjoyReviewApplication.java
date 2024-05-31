package com.suyujia;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@MapperScan("com.suyujia.mapper")
@SpringBootApplication
@EnableAspectJAutoProxy(exposeProxy = true)
public class EnjoyReviewApplication {

    public static void main(String[] args) {
        SpringApplication.run(EnjoyReviewApplication.class, args);
    }

}

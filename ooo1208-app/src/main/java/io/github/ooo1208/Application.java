package io.github.ooo1208;


import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Configuration;

/**
 * Spring Boot 启动入口。
 * boot 模块只负责启动和装配，不承载聊天或 RAG 业务逻辑。
 */
@SpringBootApplication
@Configuration
public class Application {


    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

}


package com.digitalbanking.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = {"com.digitalbanking"})
@EntityScan(basePackages = {"com.digitalbanking"})
@EnableJpaRepositories(basePackages = {"com.digitalbanking"})
public class BankingServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(BankingServerApplication.class, args);
    }
}

package com.emphasis.transactionprocessor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class TransactionProcessorApplication {

    public static void main(String[] args) {
        SpringApplication.run(TransactionProcessorApplication.class, args);
    }
}

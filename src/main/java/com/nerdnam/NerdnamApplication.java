package com.nerdnam;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class NerdnamApplication {

    public static void main(String[] args) {
        SpringApplication.run(NerdnamApplication.class, args);
    }
}

package com.company.seqdiagram;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class SeqDiagramApplication {

    public static void main(String[] args) {
        SpringApplication.run(SeqDiagramApplication.class, args);
    }
}

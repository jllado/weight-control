package com.jllado.weightcontrol;

import com.jllado.weightcontrol.config.AppProperties;
import com.jllado.weightcontrol.service.BootstrapImportService;
import java.io.IOException;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
@EnableConfigurationProperties(AppProperties.class)
public class WeightControlBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(WeightControlBackendApplication.class, args);
	}

    @Bean
    CommandLineRunner importRunner(BootstrapImportService bootstrapImportService) {
        return args -> bootstrapImportService.importIfNeeded();
    }

}

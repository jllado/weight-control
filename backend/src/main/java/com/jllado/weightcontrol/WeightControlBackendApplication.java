package com.jllado.weightcontrol;

import com.jllado.weightcontrol.config.AppProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(AppProperties.class)
public class WeightControlBackendApplication {

	public static void main(String[] args) {
		var context = SpringApplication.run(WeightControlBackendApplication.class, args);
		if (context.getEnvironment().containsProperty("app.weekly-summary.backfill.mode")) {
			context.close();
		}
	}

}

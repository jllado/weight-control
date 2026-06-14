package com.jllado.weightcontrol;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
	"spring.datasource.url=jdbc:h2:mem:testdb;MODE=MySQL;DB_CLOSE_DELAY=-1",
	"spring.datasource.driver-class-name=org.h2.Driver",
	"spring.datasource.username=sa",
	"spring.datasource.password=",
	"spring.flyway.enabled=false",
	"spring.jpa.hibernate.ddl-auto=create-drop",
	"app.import-data.enabled=false",
	"app.auth.google-client-id=test-client-id"
})
class WeightControlBackendApplicationTests {

	@Test
	void contextLoads() {
	}

}

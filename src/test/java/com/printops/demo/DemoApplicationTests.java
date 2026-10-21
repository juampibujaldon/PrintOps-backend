package com.printops.demo;

import com.printops.demo.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;

// Aislado de la base de desarrollo: hereda el Postgres descartable (Testcontainers).
class DemoApplicationTests extends IntegrationTestBase {

	@Test
	void contextLoads() {
	}

}

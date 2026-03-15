package com.porquinho;

import com.porquinho.config.TestWebSecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestWebSecurityConfig.class)
class PorquinhoBackendApplicationTests {

	@Test
	void contextLoads() {
	}

}

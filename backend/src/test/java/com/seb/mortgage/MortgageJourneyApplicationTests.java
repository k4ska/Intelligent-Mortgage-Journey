package com.seb.mortgage;

import com.seb.mortgage.agent.AgentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class MortgageJourneyApplicationTests {

	@Autowired
	AgentService agentService;

	@Test
	void contextLoads() {
		// With no OPENAI_API_KEY in the test environment, the stub agent must be active.
		assertThat(agentService.mode()).isEqualTo("stub");
	}
}

package com.seb.mortgage;

import com.seb.mortgage.agent.AgentMode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class MortgageJourneyApplicationTests {

	@Autowired
	AgentMode agentMode;

	@Test
	void contextLoads() {
		// With no OPENAI_API_KEY in the test environment, the app must run in stub mode.
		assertThat(agentMode.value()).isEqualTo("stub");
	}
}

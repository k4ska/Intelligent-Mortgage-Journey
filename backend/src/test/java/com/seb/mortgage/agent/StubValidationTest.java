package com.seb.mortgage.agent;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.seb.mortgage.agent.AgentDtos.ValidationStatus;
import com.seb.mortgage.application.ApplicationService;

/**
 * Issue #20: "Check my answers" early in the journey must not warn about figures built
 * from defaults the user never entered. Uses the stub (no API key).
 */
@SpringBootTest(properties = { "spring.ai.anthropic.api-key=", "spring.ai.openai.api-key=" })
class StubValidationTest {

	@Autowired AgentService agentService;
	@Autowired ApplicationService applicationService;

	@Test
	void earlyStepIgnoresDefaultsThenLoanStepFlagsRealFigures() {
		assertThat(agentService.mode()).isEqualTo("stub");
		UUID id = applicationService.create().getId();

		// A 500k price with the default 25k down payment would be a 95% loan-to-value,
		// but the user hasn't entered a down payment yet, so there is nothing to flag.
		applicationService.submitStep(id, "property",
				Map.of("propertyAddress", "Main St 1", "propertyType", "HOUSE", "purchasePrice", 500_000));
		var property = agentService.validate(id, "property");
		assertThat(property.status()).isEqualTo(ValidationStatus.OK);
		assertThat(property.issues()).isEmpty();

		// Once the user really enters that down payment, the same figure is flagged.
		applicationService.submitStep(id, "loan",
				Map.of("downPayment", 25_000, "loanAmount", 475_000, "termYears", 25, "rateType", "VARIABLE"));
		var loan = agentService.validate(id, "loan");
		assertThat(loan.issues()).anyMatch(i -> i.message().contains("loan-to-value is 95.0%"));
	}
}

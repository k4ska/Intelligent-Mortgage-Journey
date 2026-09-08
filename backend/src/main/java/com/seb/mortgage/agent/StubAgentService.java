package com.seb.mortgage.agent;

import com.seb.mortgage.agent.AgentDtos.ExplainResponse;
import com.seb.mortgage.agent.AgentDtos.ReviewResponse;
import com.seb.mortgage.agent.AgentDtos.ValidateResponse;
import com.seb.mortgage.agent.AgentDtos.ValidationIssue;
import com.seb.mortgage.agent.AgentDtos.ValidationStatus;
import com.seb.mortgage.application.ApplicationService;
import com.seb.mortgage.profile.LoanProfile;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Deterministic stand-in for the real agent. Good enough to build and demo the whole
 * frontend against; swapped out automatically once an Anthropic API key is configured.
 */
public class StubAgentService implements AgentService {

	private final ApplicationService applicationService;

	public StubAgentService(ApplicationService applicationService) {
		this.applicationService = applicationService;
	}

	@Override
	public String mode() {
		return "stub";
	}

	@Override
	public ExplainResponse explain(String topicOrFieldKey, UUID applicationId) {
		Explanations.Entry entry = Explanations.lookup(topicOrFieldKey);
		if (entry != null) {
			return new ExplainResponse(entry.text(), entry.related());
		}
		return new ExplainResponse(
				"That's a good question. In a mortgage context, \"" + topicOrFieldKey + "\" affects how a "
						+ "lender views your application - ask your mentor to connect the Claude API key and "
						+ "the assistant will explain this properly using your own numbers.",
				List.of());
	}

	@Override
	public ValidateResponse validate(UUID applicationId, String stepId) {
		LoanProfile profile = applicationService.profile(applicationId);
		List<ValidationIssue> issues = new ArrayList<>();

		LoanProfile.Financials f = profile.financials();
		if (f != null) {
			if (f.ltv() > 90) {
				issues.add(new ValidationIssue("downPayment", ValidationStatus.WARNING,
						"Your loan-to-value is " + f.ltv() + "%. Above 90% only a few lenders will "
								+ "consider the application and the rate is higher. A larger down payment helps."));
			}
			if ("FAIL".equals(f.stressTestResult())) {
				issues.add(new ValidationIssue("loanAmount", ValidationStatus.BLOCKER,
						"After a rate-rise stress test the monthly payment (" + f.stressedMonthlyPayment()
								+ ") is more than the income available. Consider a smaller loan or a longer term."));
			} else if ("TIGHT".equals(f.stressTestResult())) {
				issues.add(new ValidationIssue("loanAmount", ValidationStatus.WARNING,
						"This leaves about " + f.monthlyBufferAfterStress() + " per month after a rate-rise "
								+ "stress test. Lenders usually want to see a larger cushion."));
			}
			if (f.dti() > 40) {
				issues.add(new ValidationIssue("monthlyDebtPayments", ValidationStatus.WARNING,
						"Debt-to-income of " + f.dti() + "% is on the high side; paying down other debt "
								+ "before applying would strengthen the application."));
			}
		}

		ValidationStatus overall = issues.stream().anyMatch(i -> i.severity() == ValidationStatus.BLOCKER)
				? ValidationStatus.BLOCKER
				: issues.isEmpty() ? ValidationStatus.OK : ValidationStatus.WARNING;
		return new ValidateResponse(overall, issues);
	}

	@Override
	public ReviewResponse review(UUID applicationId) {
		LoanProfile p = applicationService.profile(applicationId);
		LoanProfile.LoanRequest lr = p.loanRequest();
		LoanProfile.Financials f = p.financials();

		String summary = "You are asking to borrow %,.0f over %d years at a %s rate to %s a %s in %s. "
				.formatted(lr.amount(), lr.termYears(), lr.rateType().toLowerCase(),
						lr.purpose().toLowerCase(), p.property().type().toLowerCase(),
						p.property().location())
				+ "That is a loan-to-value of %.1f%% and an estimated payment of %,.0f per month (%,.0f if rates rose 3 points)."
						.formatted(f.ltv(), f.estimatedMonthlyPayment(), f.stressedMonthlyPayment());

		return new ReviewResponse(summary, new ArrayList<>(p.riskIndicators()));
	}

	@Override
	public Flux<String> chat(UUID applicationId, String message) {
		String reply = "You asked: \"" + message + "\". The stub assistant can only give canned answers "
				+ "right now. Once the Anthropic API key is configured, the assistant will answer using "
				+ "your application details and the mortgage explanation library.";
		String[] words = reply.split(" ");
		return Flux.fromArray(words)
				.delayElements(Duration.ofMillis(25))
				.map(w -> w + " ");
	}
}

package com.seb.mortgage.agent;

import com.seb.mortgage.agent.AgentDtos.ExplainResponse;
import com.seb.mortgage.agent.AgentDtos.ReviewResponse;
import com.seb.mortgage.agent.AgentDtos.ValidateResponse;
import com.seb.mortgage.agent.AgentDtos.ValidationIssue;
import com.seb.mortgage.agent.AgentDtos.ValidationStatus;
import com.seb.mortgage.application.ApplicationService;
import com.seb.mortgage.mockdata.AffordabilityCalculator;
import com.seb.mortgage.profile.LoanProfile;
import com.seb.mortgage.profile.ProfileFacts;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Deterministic stand-in for the real agent. Good enough to build and demo the whole
 * frontend against; swapped out automatically once an OpenAI API key is configured.
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
			return new ExplainResponse(entry.text(), entry.related(), null);
		}
		return new ExplainResponse(
				"This topic is not in our glossary yet.",
				List.of(),
				null);
	}

	@Override
	public ValidateResponse validate(UUID applicationId, String stepId) {
		// Only figures this step feeds into, and only once their inputs are real answers.
		ProfileFacts.StepFacts facts = applicationService.stepFacts(applicationId, stepId);
		List<ValidationIssue> issues = new ArrayList<>();

		facts.figure("loanToValuePercent").map(f -> (Double) f.value()).filter(ltv -> ltv > 90)
				.ifPresent(ltv -> issues.add(new ValidationIssue("downPayment", ValidationStatus.WARNING,
						"Your loan-to-value is " + ltv + "%. Above 90% only a few lenders will "
								+ "consider the application and the rate is higher. A larger down payment helps.")));
		facts.figure("stressTestResult").map(ProfileFacts.Figure::value).ifPresent(result -> {
			if ("FAIL".equals(result)) {
				issues.add(new ValidationIssue("loanAmount", ValidationStatus.BLOCKER,
						"At the stress-test rate the monthly payment ("
								+ facts.figure("stressedMonthlyPayment").map(ProfileFacts.Figure::value).orElse("")
								+ ") plus your other debts would take "
								+ facts.figure("stressedDebtToIncomePercent").map(ProfileFacts.Figure::value).orElse("")
								+ "% of your net income. Lenders allow at most "
								+ (int) AffordabilityCalculator.MAX_DTI_PCT
								+ "%. Consider a smaller loan or a longer term."));
			} else if ("TIGHT".equals(result)) {
				issues.add(new ValidationIssue("loanAmount", ValidationStatus.WARNING,
						"This leaves about "
								+ facts.figure("monthlyBufferAfterStress").map(ProfileFacts.Figure::value).orElse("")
								+ " per month after living costs and a rate-rise stress test. Lenders usually want to see a larger cushion."));
			}
		});
		facts.figure("debtToIncomePercent").map(f -> (Double) f.value())
				.filter(dti -> dti > AffordabilityCalculator.MAX_DTI_PCT)
				.ifPresent(dti -> issues.add(new ValidationIssue("monthlyDebtPayments", ValidationStatus.WARNING,
						"Loan payments would take " + dti + "% of your net income even at today's rate; paying "
								+ "down other debt before applying would strengthen the application.")));

		facts.figure("creditLimitMonthlyCost").filter(f -> f.concern() != null)
				.ifPresent(f -> issues.add(new ValidationIssue("totalCreditLimits", ValidationStatus.WARNING,
						"Your credit card and overdraft limits count as %,.0f a month of debt, ".formatted(f.value())
								+ "even if you never use them, because you could. Closing limits you don't need "
								+ "would raise what you can borrow.")));
		facts.figure("monthlyLivingCosts").filter(f -> f.concern() != null)
				.ifPresent(f -> issues.add(new ValidationIssue("monthlyHouseholdCosts", ValidationStatus.WARNING,
						"That's less than lenders assume a household like yours needs, so they'll count "
								+ "%,.0f a month instead. Double-check the figure if it seems low.".formatted(f.value()))));

		ValidationStatus overall = issues.stream().anyMatch(i -> i.severity() == ValidationStatus.BLOCKER)
				? ValidationStatus.BLOCKER
				: issues.isEmpty() ? ValidationStatus.OK : ValidationStatus.WARNING;
		return new ValidateResponse(overall, issues, null);
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
				+ "That is a loan-to-value of %.1f%% and an estimated payment of %,.0f per month (%,.0f at the stress-test rate)."
						.formatted(f.ltv(), f.estimatedMonthlyPayment(), f.stressedMonthlyPayment())
				+ " Affordability counts %,.0f a month of living costs%s."
						.formatted(f.monthlyLivingCosts(), f.creditLimitMonthlyCost() > 0
								? " and %,.0f a month for your credit limits".formatted(f.creditLimitMonthlyCost())
								: "");

		return new ReviewResponse(summary, new ArrayList<>(p.riskIndicators()), null);
	}

	@Override
	public Flux<String> chat(UUID applicationId, String message) {
		String reply = "You asked: \"" + message + "\". The stub assistant can only give canned answers "
				+ "right now. Once an OpenAI API key is configured, the assistant will answer using "
				+ "your application details and the mortgage explanation library.";
		String[] words = reply.split(" ");
		return Flux.fromArray(words)
				.delayElements(Duration.ofMillis(25))
				.map(w -> w + " ");
	}
}
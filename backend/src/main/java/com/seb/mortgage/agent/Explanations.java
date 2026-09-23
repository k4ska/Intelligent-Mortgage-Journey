package com.seb.mortgage.agent;

import java.util.List;
import java.util.Map;

/**
 * Plain-language explanation snippets keyed by {@code helpKey} / topic. Used directly by
 * the stub agent and offered to Claude as reference material by the real one, so the
 * vocabulary stays consistent whether or not an API key is configured.
 */
public final class Explanations {

	private Explanations() {
	}

	public record Entry(String text, List<String> related) {}

	public static final Map<String, Entry> BY_KEY = Map.ofEntries(
			Map.entry("loan-to-value", new Entry(
					"Loan-to-value (LTV) is the size of your loan compared with the value of the property, "
							+ "as a percentage. Borrow 180,000 on a 200,000 home and your LTV is 90%. "
							+ "Lenders price risk off this number: a lower LTV (a bigger deposit) usually means "
							+ "a lower interest rate and more lenders willing to say yes.",
					List.of("down-payment", "interest-rate", "stress-test"))),
			Map.entry("down-payment", new Entry(
					"The down payment (or deposit) is the part of the purchase price you pay yourself, "
							+ "up front. The rest is the mortgage. A larger down payment lowers your "
							+ "loan-to-value, which typically unlocks better rates.",
					List.of("loan-to-value"))),
			Map.entry("interest-rate", new Entry(
					"The interest rate is the yearly cost of borrowing, as a percentage of what you owe. "
							+ "A variable rate can move up or down over time; a fixed rate stays the same for "
							+ "an agreed period, trading a slightly higher starting rate for certainty.",
					List.of("rate-type", "stress-test"))),
			Map.entry("rate-type", new Entry(
					"Fixed means your rate - and your monthly payment - is locked for a set number of years. "
							+ "Variable means it tracks a reference rate and can change. Fixed costs a little "
							+ "more now for predictability; variable is cheaper now but exposes you to rate rises.",
					List.of("interest-rate", "stress-test"))),
			Map.entry("stress-test", new Entry(
					"A stress test checks whether you could still afford the payments if interest rates rose. "
							+ "Lenders re-run your budget at a higher rate (here, +3 percentage points) and want "
							+ "to see money left over each month afterwards.",
					List.of("interest-rate", "debt-to-income"))),
			Map.entry("debt-to-income", new Entry(
					"Debt-to-income (DTI) compares your monthly debt payments - including the new mortgage - "
							+ "with your monthly income. Most lenders get uncomfortable above roughly 40%.",
					List.of("stress-test"))),
			Map.entry("amortization", new Entry(
					"Amortization is how the loan is paid down over time. With each monthly payment part goes "
							+ "to interest and part reduces the balance; early on most of it is interest, later "
							+ "most of it is principal.",
					List.of("loan-term", "interest-rate"))),
			Map.entry("loan-term", new Entry(
					"The loan term is how long you take to repay - often 20 to 30 years. A longer term means "
							+ "lower monthly payments but more interest paid overall.",
					List.of("amortization"))),
			Map.entry("employment-type", new Entry(
					"Lenders assess employed and self-employed applicants differently. If you are employed they "
							+ "look at payslips; if you are self-employed they usually want two years of accounts "
							+ "or tax returns to see a stable income.",
					List.of("proof-of-income"))),
			Map.entry("proof-of-income", new Entry(
					"Proof of income is the evidence a lender uses to confirm what you earn: recent payslips "
							+ "and bank statements if employed, or tax returns and business accounts if self-employed.",
					List.of("employment-type"))),
			Map.entry("what-is-a-loan", new Entry(
					"A mortgage loan lets you buy a property now by borrowing most of its price from a lender, "
							+ "then repaying it - plus interest - in monthly instalments over many years, commonly "
							+ "20 to 30. You put down a portion yourself up front (the down payment); the property "
							+ "itself is the lender's security until the loan is paid off. Two things chiefly set "
							+ "your price: the loan-to-value and the interest rate you're offered.",
					List.of("loan-to-value", "interest-rate", "down-payment"))),
			Map.entry("top-mortgage-mistakes", new Entry(
					"""
					1. Shopping for a home before knowing what you can actually afford.
					2. Ignoring what happens if interest rates rise - lenders stress-test for this, so should you.
					3. Underestimating debt-to-income; existing debt quietly eats into what you can borrow.
					4. Making a big purchase or opening new credit right before applying.
					5. Only checking one lender's rate instead of comparing a few.
					6. Choosing the lowest monthly payment without checking total interest over the term.
					7. Skipping the fine print on fixed vs. variable rate terms.
					8. Not keeping a buffer for closing costs and moving expenses.
					9. Assuming pre-approval is a guaranteed loan.
					10. Leaving proof-of-income documents until the last minute.""",
					List.of("stress-test", "debt-to-income", "interest-rate")))
		);

	public static Entry lookup(String key) {
		if (key == null) {
			return null;
		}
		return BY_KEY.get(key.toLowerCase());
	}
}

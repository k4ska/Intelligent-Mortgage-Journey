package com.seb.mortgage.agent;

import java.util.List;
import java.util.Map;

/**
 * Plain-language explanation snippets keyed by {@code helpKey} / topic. Used directly by
 * the stub agent and offered to the LLM as reference material by the real one, so the
 * vocabulary stays consistent whether or not an API key is configured.
 */
public final class Explanations {

	private Explanations() {
	}

	public record Entry(String text, List<String> related) {}

	public static final Map<String, Entry> BY_KEY = Map.ofEntries(
			Map.entry("loan-purpose", new Entry(
					"The loan purpose tells the lender whether you are buying a property or refinancing a mortgage you already have. "
							+ "The process and documents can differ between those two cases because a purchase involves a new property transaction, "
							+ "while a refinance replaces or changes existing mortgage borrowing.",
					List.of("what-is-a-loan"))),
			Map.entry("property-address", new Entry(
					"The property address identifies the home connected to the mortgage. Lenders use it to match the application to the correct property "
							+ "and, later in the process, to support valuation and property checks.",
					List.of("property-type", "property-value"))),
			Map.entry("property-type", new Entry(
					"Property type describes what kind of home you are financing, such as an apartment, house, or terraced home. "
							+ "Lenders may use the property type when assessing the property and the mortgage because different types can have different valuation or lending considerations.",
					List.of("property-address", "property-value"))),
			Map.entry("property-value", new Entry(
					"The purchase price is what you expect to pay for the property; for a refinance, use its current value instead. "
							+ "The lender compares this value with the amount you want to borrow to calculate your loan-to-value ratio.",
					List.of("loan-to-value", "down-payment", "loan-amount"))),
			Map.entry("co-applicant", new Entry(
					"A co-applicant is a second person applying for the mortgage with you. Their income can increase the household income considered by the lender, "
							+ "but their existing debts and other commitments are considered as well.",
					List.of("net-income", "debt-payments"))),
			Map.entry("co-applicant-name", new Entry(
					"Enter the name of the second person applying for the mortgage with you. "
							+ "Their financial information will be considered together with yours when the joint application is assessed.",
					List.of("co-applicant"))),
			Map.entry("net-income", new Entry(
					"Monthly net income is what actually lands in your bank account each month, after income tax and other deductions. "
							+ "Estonian lenders assess affordability on net income: all your loan payments together may take at most half of it. "
							+ "They normally ask for documents that prove it, such as bank statements.",
					List.of("proof-of-income", "debt-to-income"))),
			Map.entry("debt-payments", new Entry(
					"Existing monthly debt payments are the regular payments you already make on borrowing such as personal loans, car finance, or credit cards. "
							+ "They reduce the income available for a new mortgage and are used when assessing debt-to-income and affordability.",
					List.of("debt-to-income", "stress-test"))),
			Map.entry("years-trading", new Entry(
					"Years trading means how long your business or self-employed activity has been operating. "
							+ "A longer trading history gives a lender more evidence to judge whether the income is stable and sustainable.",
					List.of("employment-type", "proof-of-income", "net-profit"))),
			Map.entry("net-profit", new Entry(
					"Net profit is what the business earned after its expenses and taxes were deducted from its revenue. "
							+ "For self-employed applicants it stands in for a salary: a twelfth of last year's net profit counts as monthly net income. "
							+ "Lenders check it against tax returns or accounts to judge whether the income is sustainable.",
					List.of("years-trading", "proof-of-income", "net-income"))),
			Map.entry("loan-amount", new Entry(
					"The loan amount is the amount you want to borrow from the lender. It is normally the part of the property price that is not covered by your down payment, "
							+ "and together with the property value it determines your loan-to-value ratio.",
					List.of("down-payment", "loan-to-value", "property-value"))),
			Map.entry("fixed-period", new Entry(
					"The fixed period is how long an agreed fixed interest rate stays unchanged, for example 2, 5, or 10 years. "
							+ "During that period your rate is protected from market rate changes; after it ends, the loan moves to the rate arrangement defined by your mortgage terms.",
					List.of("rate-type", "interest-rate"))),
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
					List.of("interest-rate", "stress-test", "fixed-period"))),
			Map.entry("stress-test", new Entry(
					"A stress test checks whether you could still afford the payments if interest rates rose. "
							+ "Estonian lenders recalculate your payment at your rate plus 2 percentage points (and at least 6%), "
							+ "and at that rate all your loan payments together must stay within half of your net income.",
					List.of("interest-rate", "debt-to-income"))),
			Map.entry("debt-to-income", new Entry(
					"Debt-to-income (DTI) compares your monthly loan payments - including the new mortgage - "
							+ "with your monthly net income. Under Eesti Pank's rules it may be at most 50%, "
							+ "worked out at the higher stress-test rate rather than today's rate.",
					List.of("stress-test", "debt-payments", "net-income"))),
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
					List.of("employment-type", "net-income"))),
			Map.entry("what-is-a-loan", new Entry(
					"""
					A mortgage lets you buy a home now by borrowing most of the price from a lender, then paying it back - plus interest - in monthly instalments over many years, usually 20 to 30.

					You pay part of the price yourself up front: the down payment. The home is the lender's security, so if the loan isn't repaid the lender can sell it.

					What the loan costs you mostly comes down to the interest rate. That rate depends partly on your loan-to-value - how much you borrow compared with the home's value - and the lender also checks that your income can carry the payments.""",
					List.of("loan-to-value", "interest-rate", "down-payment"))),
			Map.entry("top-mortgage-mistakes", new Entry(
					"""
					1. Shopping for a home before knowing how much you can borrow.
					2. Ignoring what happens if interest rates rise - lenders stress-test for this, and so should you.
					3. Forgetting existing debts: car loans, leasing and credit cards all reduce what you can borrow.
					4. Taking on a new loan or a big purchase on credit right before applying.
					5. Taking the first offer instead of comparing a few lenders.
					6. Picking the lowest monthly payment without checking the total interest over the term.
					7. Not understanding how a fixed rate differs from a variable one, and what happens when a fixed period ends.
					8. Spending all your savings on the down payment, with nothing left for fees, moving or repairs.
					9. Treating a pre-approval as a guaranteed loan.
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
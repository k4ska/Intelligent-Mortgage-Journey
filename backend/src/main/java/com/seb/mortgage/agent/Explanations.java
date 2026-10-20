package com.seb.mortgage.agent;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;

/**
 * Plain-language explanation snippets keyed by topic. This is the glossary behind the
 * advisor's {@code explainTerm} tool (see {@code docs/architecture.md}), so definitions
 * come from here rather than from the model's own memory.
 */
public final class Explanations {

	private Explanations() {
	}

	public record Entry(String text, List<String> related) {}

	/** A glossary entry and the key it was found under. */
	public record Found(String key, Entry entry) {}

	public static final Map<String, Entry> BY_KEY = Map.ofEntries(
			Map.entry("property-address", new Entry(
					"The property address identifies the home connected to the mortgage. Lenders use it to match the application to the correct property "
							+ "and, later in the process, to support valuation and property checks.",
					List.of("property-type", "property-value"))),
			Map.entry("property-type", new Entry(
					"Property type describes what kind of home you are buying: an apartment, a house, a plot of land or a new build. "
							+ "Lenders look at the type because it changes how the property is valued and how much they are willing to lend against it.",
					List.of("property-address", "property-value", "new-build"))),
			Map.entry("property-value", new Entry(
					"The purchase price is what you expect to pay for the property. "
							+ "The lender compares it with the amount you want to borrow to calculate your loan-to-value ratio, "
							+ "and checks it against an independent valuation.",
					List.of("loan-to-value", "down-payment", "loan-amount", "valuation"))),
			Map.entry("price-per-square-metre", new Entry(
					"The price per square metre is the price of a home divided by its floor area. It makes homes of different sizes comparable: "
							+ "at the same price per square metre, a smaller home in the same area costs proportionally less. "
							+ "It varies a lot between areas, so a home of the same size can cost much less further from the city centre.",
					List.of("property-value", "affordability"))),
			Map.entry("new-build", new Entry(
					"A new build is a home that is newly built or still being built, usually bought from a developer. "
							+ "If it isn't finished yet, the bank may pay the price out in stages as construction progresses, "
							+ "and it will want the developer's documents and a valuation of the finished home.",
					List.of("property-type", "valuation"))),
			Map.entry("valuation", new Entry(
					"A valuation is an independent estimate of what the property is worth, made by a valuer the bank accepts. "
							+ "The bank lends based on the lower of the purchase price and the valuation, so a low valuation can mean you need a bigger down payment. "
							+ "You usually order and pay for it yourself.",
					List.of("property-value", "loan-to-value", "down-payment"))),
			Map.entry("purchase-agreement", new Entry(
					"In Estonia, buying real estate is done with a purchase agreement signed in front of a notary, who also checks the land register. "
							+ "The bank wants to see the draft agreement before it pays out the loan, and the money is often paid to the seller through the notary.",
					List.of("valuation", "down-payment-proof"))),
			Map.entry("down-payment-proof", new Entry(
					"Down-payment proof shows the bank that you really have the money you are putting in yourself, "
							+ "usually a recent bank statement from the account that holds your savings.",
					List.of("down-payment", "proof-of-income"))),
			Map.entry("co-applicant", new Entry(
					"A co-applicant is a second person applying for the mortgage with you. Their income can increase the household income considered by the lender, "
							+ "but their existing debts and other commitments are considered as well.",
					List.of("net-income", "debt-payments"))),
			Map.entry("net-income", new Entry(
					"Monthly net income is what actually lands in your bank account each month, after income tax and other deductions. "
							+ "Estonian lenders assess affordability on net income: all your loan payments together may take at most half of it. "
							+ "They normally ask for documents that prove it, such as bank statements.",
					List.of("proof-of-income", "debt-to-income"))),
			Map.entry("debt-payments", new Entry(
					"Existing monthly debt payments are the regular payments you already make on borrowing such as personal loans, car finance, or leasing. "
							+ "They reduce the income available for a new mortgage and are used when assessing debt-to-income and affordability.",
					List.of("debt-to-income", "stress-test", "credit-limits"))),
			Map.entry("dependants", new Entry(
					"Dependants are people in your household who rely on your income, usually children under 18. "
							+ "Each one adds to the living costs a lender assumes your household has, which leaves less room for loan payments.",
					List.of("household-costs", "stress-test"))),
			Map.entry("household-costs", new Entry(
					"Household living costs are what your household spends each month on everyday life: utilities, food, transport, "
							+ "childcare, insurance and the like, without rent and loan payments (the new mortgage replaces your rent, and loans are counted separately). "
							+ "Lenders count at least a minimum per adult and per dependant, even if you spend less.",
					List.of("dependants", "stress-test", "net-income"))),
			Map.entry("credit-limits", new Entry(
					"Credit limits are the most you could borrow on your credit cards and overdrafts, whether you use them or not. "
							+ "Lenders count a small share of the total limit (here 3%) as a monthly debt payment, because you could draw on it at any time. "
							+ "So closing a card you don't use can raise what you can borrow.",
					List.of("debt-payments", "debt-to-income"))),
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
			Map.entry("affordability", new Entry(
					"How much you can borrow depends on three checks. At the stress-test rate, all your loan payments together may take at most half of your net income. "
							+ "After those payments and your living costs, enough money must still be left each month (here at least 300 euros). "
							+ "And your down payment must cover at least 15% of the price, because lenders typically lend at most 85% of it. "
							+ "Whichever check is strictest sets your limit; a longer loan term lowers the monthly payment and so can raise it.",
					List.of("stress-test", "debt-to-income", "loan-to-value", "household-costs"))),
			Map.entry("euribor", new Entry(
					"Euribor is the reference interest rate at which banks in the euro area lend to each other. "
							+ "Most Estonian mortgages have a variable rate made of two parts: Euribor (usually the 6-month rate) plus the bank's margin. "
							+ "Euribor is reset every few months, typically every 6, so your monthly payment can go up or down with it.",
					List.of("margin", "interest-rate", "rate-type"))),
			Map.entry("margin", new Entry(
					"The margin is the bank's own part of a variable interest rate, added on top of Euribor. "
							+ "It is agreed in your contract and usually stays the same for the whole loan, so it is the part worth comparing and negotiating between banks.",
					List.of("euribor", "interest-rate"))),
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
					List.of("loan-to-value", "down-payment-proof"))),
			Map.entry("interest-rate", new Entry(
					"The interest rate is the yearly cost of borrowing, as a percentage of what you owe. "
							+ "A variable rate can move up or down over time; a fixed rate stays the same for "
							+ "an agreed period, trading a slightly higher starting rate for certainty.",
					List.of("rate-type", "euribor", "margin", "stress-test"))),
			Map.entry("rate-type", new Entry(
					"Fixed means your rate - and your monthly payment - is locked for a set number of years. "
							+ "Variable means it tracks a reference rate and can change. Fixed costs a little "
							+ "more now for predictability; variable is cheaper now but exposes you to rate rises.",
					List.of("interest-rate", "stress-test", "fixed-period", "euribor"))),
			Map.entry("stress-test", new Entry(
					"A stress test checks whether you could still afford the payments if interest rates rose. "
							+ "Estonian lenders recalculate your payment at your rate plus 2 percentage points (and at least 6%), "
							+ "and at that rate all your loan payments together must stay within half of your net income, "
							+ "with money still left over after your living costs.",
					List.of("interest-rate", "debt-to-income", "affordability"))),
			Map.entry("debt-to-income", new Entry(
					"Debt-to-income (DTI) compares your monthly loan payments - including the new mortgage - "
							+ "with your monthly net income. Under Eesti Pank's rules it may be at most 50%, "
							+ "worked out at the higher stress-test rate rather than today's rate.",
					List.of("stress-test", "debt-payments", "net-income"))),
			Map.entry("amortization", new Entry(
					"Amortization is how the loan is paid down over time. With each monthly payment part goes "
							+ "to interest and part reduces the balance; early on most of it is interest, later "
							+ "most of it is principal. With the usual annuity schedule every monthly payment is the same size.",
					List.of("loan-term", "interest-rate"))),
			Map.entry("loan-term", new Entry(
					"The loan term is how long you take to repay - often 20 to 30 years, and at most 30 under Eesti Pank's rules. "
							+ "A longer term means lower monthly payments but more interest paid overall.",
					List.of("amortization", "affordability"))),
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

	/** Other names people use for a topic, already in the form {@link #find} compares. */
	private static final Map<String, String> ALIASES = Map.ofEntries(
			Map.entry("ltv", "loan-to-value"),
			Map.entry("dti", "debt-to-income"),
			Map.entry("deposit", "down-payment"),
			Map.entry("downpayment", "down-payment"),
			Map.entry("self-financing", "down-payment"),
			Map.entry("omafinantseering", "down-payment"),
			Map.entry("bank-margin", "margin"),
			Map.entry("interest-margin", "margin"),
			Map.entry("variable-rate", "rate-type"),
			Map.entry("fixed-rate", "rate-type"),
			Map.entry("interest", "interest-rate"),
			Map.entry("stress-test-rate", "stress-test"),
			Map.entry("stressed-rate", "stress-test"),
			Map.entry("50-rule", "debt-to-income"),
			Map.entry("debt-to-income-ratio", "debt-to-income"),
			Map.entry("living-costs", "household-costs"),
			Map.entry("monthly-living-costs", "household-costs"),
			Map.entry("credit-card-limits", "credit-limits"),
			Map.entry("credit-card-limit", "credit-limits"),
			Map.entry("overdraft", "credit-limits"),
			Map.entry("monthly-debt-payments", "debt-payments"),
			Map.entry("other-loans", "debt-payments"),
			Map.entry("net-salary", "net-income"),
			Map.entry("take-home-pay", "net-income"),
			Map.entry("how-much-can-i-borrow", "affordability"),
			Map.entry("max-loan", "affordability"),
			Map.entry("maximum-loan", "affordability"),
			Map.entry("borrowing-limit", "affordability"),
			Map.entry("appraisal", "valuation"),
			Map.entry("valuation-report", "valuation"),
			Map.entry("notary", "purchase-agreement"),
			Map.entry("purchase-contract", "purchase-agreement"),
			Map.entry("sales-contract", "purchase-agreement"),
			Map.entry("proof-of-savings", "down-payment-proof"),
			Map.entry("payslip", "proof-of-income"),
			Map.entry("bank-statement", "proof-of-income"),
			Map.entry("price-per-m2", "price-per-square-metre"),
			Map.entry("price-per-sqm", "price-per-square-metre"),
			Map.entry("price-per-square-meter", "price-per-square-metre"),
			Map.entry("square-metre-price", "price-per-square-metre"),
			Map.entry("new-development", "new-build"),
			Map.entry("annuity", "amortization"),
			Map.entry("amortisation", "amortization"),
			Map.entry("term", "loan-term"),
			Map.entry("maturity", "loan-term"),
			Map.entry("mortgage", "what-is-a-loan"),
			Map.entry("home-loan", "what-is-a-loan"),
			Map.entry("principal", "loan-amount"),
			Map.entry("joint-application", "co-applicant"));

	public static Entry lookup(String key) {
		if (key == null) {
			return null;
		}
		return BY_KEY.get(key.toLowerCase(Locale.ROOT));
	}

	/**
	 * The entry for a topic however the advisor writes it: a key ("loan-to-value"), its
	 * name ("Loan to value"), a common other name ("LTV", "deposit") or a plural.
	 */
	public static Optional<Found> find(String topic) {
		String key = normalize(topic);
		if (key.isEmpty()) {
			return Optional.empty();
		}
		for (String candidate : List.of(key, key.endsWith("s") ? key.substring(0, key.length() - 1) : key)) {
			String resolved = ALIASES.getOrDefault(candidate, candidate);
			Entry entry = BY_KEY.get(resolved);
			if (entry != null) {
				return Optional.of(new Found(resolved, entry));
			}
		}
		return Optional.empty();
	}

	/** Every topic the glossary has, sorted. */
	public static List<String> topics() {
		return List.copyOf(new TreeSet<>(BY_KEY.keySet()));
	}

	/** "Price per m²!" becomes "price-per-m2", "LTV" becomes "ltv". */
	private static String normalize(String topic) {
		if (topic == null) {
			return "";
		}
		return topic.toLowerCase(Locale.ROOT)
				.replace("²", "2")
				.replace("%", "")
				.replace("’", "'")
				.replace("'", "")
				.replaceAll("^((what is|whats|what are|explain|the|a|an)\\s+)+", "")
				.replaceAll("[^\\p{L}\\p{N}]+", "-")
				.replaceAll("^-|-$", "");
	}
}

package com.seb.mortgage.advisor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.seb.mortgage.bank.BankCatalogService;
import com.seb.mortgage.mockdata.AffordabilityCalculator;
import com.seb.mortgage.profile.LoanProfileAssembler;
import com.seb.mortgage.profile.ProfileFieldView;
import com.seb.mortgage.profile.ProfileService;
import com.seb.mortgage.property.PropertyService;
import com.seb.mortgage.property.PropertyView;

/**
 * The charts a button in the chat can show straight away, without asking the model (#160,
 * point 3): instant, and always the calculator's numbers. Each one needs the loan, so they
 * are there once the affordability check can run.
 */
@Service
public class ChartService {

	/** Every chart a button can show, in the order the buttons appear. */
	static final List<String> TYPES = List.of(RateChart.TYPE, CostBreakdown.TYPE);

	private final ProfileService profile;
	private final PropertyService property;
	private final LoanProfileAssembler loans;
	private final BankCatalogService banks;
	private final AffordabilityCalculator calculator = new AffordabilityCalculator();

	public ChartService(ProfileService profile, PropertyService property, LoanProfileAssembler loans,
			BankCatalogService banks) {
		this.profile = profile;
		this.property = property;
		this.loans = loans;
		this.banks = banks;
	}

	/** The charts the user can see now: all of them once the loan is known, otherwise none. */
	public List<String> available(UUID userId) {
		return checked(userId).isPresent() ? TYPES : List.of();
	}

	/** The chart of this type for the user, or empty when the type is unknown or the loan isn't known yet. */
	public Optional<ChartView> chart(UUID userId, String type) {
		return checked(userId).map(checked -> switch (type) {
			case RateChart.TYPE -> RateChart.of(checked, calculator).view();
			case CostBreakdown.TYPE -> CostBreakdown.of(checked, calculator).view();
			default -> null;
		});
	}

	private Optional<LoanProfileAssembler.Checked> checked(UUID userId) {
		List<ProfileFieldView> values = property.active(userId).map(PropertyView::fields).orElse(List.of());
		// The same rate the advisor's checks assume: the variable rate plus the banks' middle margin.
		return loans.check(profile.fields(userId), values, loans.terms(banks.typicalBaseMarginPct())) instanceof
				LoanProfileAssembler.Checked checked ? Optional.of(checked) : Optional.empty();
	}
}

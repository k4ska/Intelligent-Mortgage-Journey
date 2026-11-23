package com.seb.mortgage.advisor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.seb.mortgage.bank.BankCatalogService;
import com.seb.mortgage.bank.OfferCalculator;
import com.seb.mortgage.mockdata.AffordabilityCalculator;
import com.seb.mortgage.profile.LoanProfileAssembler;
import com.seb.mortgage.profile.ProfileFieldView;
import com.seb.mortgage.profile.ProfileService;
import com.seb.mortgage.property.PropertyService;
import com.seb.mortgage.property.PropertyView;

/**
 * The charts a button in the chat can show straight away, without asking the model (#160,
 * point 3): instant, and always the calculator's numbers. Each one needs the loan, so they
 * are there once the affordability check can run. Once the user has accepted a bank's offer,
 * they show that offer instead of the advisor's estimate with a typical margin.
 */
@Service
public class ChartService {

	/** Every chart a button can show, in the order the buttons appear. */
	static final List<String> TYPES = List.of(RateChart.TYPE, CostBreakdown.TYPE);

	private final ProfileService profile;
	private final PropertyService property;
	private final LoanProfileAssembler loans;
	private final BankCatalogService banks;
	private final BankThreads threads;
	private final AffordabilityCalculator calculator = new AffordabilityCalculator();

	public ChartService(ProfileService profile, PropertyService property, LoanProfileAssembler loans,
			BankCatalogService banks, BankThreads threads) {
		this.profile = profile;
		this.property = property;
		this.loans = loans;
		this.banks = banks;
		this.threads = threads;
	}

	/** The charts the user can see now: all of them once the loan is known or an offer accepted, otherwise none. */
	public List<String> available(UUID userId) {
		return threads.accepted(userId).isPresent() || checked(userId).isPresent() ? TYPES : List.of();
	}

	/**
	 * The chart of this type for the user: for the offer they accepted, if any, otherwise the
	 * advisor's estimate. Empty when the type is unknown or there is nothing to chart yet.
	 */
	public Optional<ChartView> chart(UUID userId, String type) {
		if (!TYPES.contains(type)) {
			return Optional.empty();
		}
		Optional<BankThreads.AcceptedOffer> accepted = threads.accepted(userId);
		if (accepted.isPresent()) {
			OfferCalculator.Offer offer = accepted.get().offer();
			String bank = accepted.get().bank();
			return Optional.of(RateChart.TYPE.equals(type)
					? RateChart.of(offer, calculator).view(bank)
					: CostBreakdown.of(offer, price(userId).orElse(offer.amount())).view(bank));
		}
		return checked(userId).map(checked -> RateChart.TYPE.equals(type)
				? RateChart.of(checked, calculator).view()
				: CostBreakdown.of(checked, calculator).view());
	}

	private Optional<Double> price(UUID userId) {
		return property.active(userId).flatMap(home -> home.fields().stream()
				.filter(field -> "price".equals(field.key()))
				.findFirst())
				.flatMap(field -> {
					try {
						return Optional.of(Double.parseDouble(field.value()));
					} catch (NumberFormatException e) {
						return Optional.empty();
					}
				});
	}

	private Optional<LoanProfileAssembler.Checked> checked(UUID userId) {
		List<ProfileFieldView> values = property.active(userId).map(PropertyView::fields).orElse(List.of());
		// The same rate the advisor's checks assume: the variable rate plus the banks' middle margin.
		return loans.check(profile.fields(userId), values, loans.terms(banks.typicalBaseMarginPct())) instanceof
				LoanProfileAssembler.Checked checked ? Optional.of(checked) : Optional.empty();
	}
}

package com.seb.mortgage.bank;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.seb.mortgage.auth.CurrentUser;
import com.seb.mortgage.profile.ProfileFieldView;
import com.seb.mortgage.profile.ProfileSnapshotProvider;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Sends the user's application to the chosen banks: freezes the profile as it is now, opens
 * one email thread per bank and lets each bank agent write its first email. Later profile
 * changes don't touch an application that was already sent.
 */
@Service
public class ApplicationService {

	public record ThreadResult(UUID threadId, String bankId, String status) {}

	public record ApplicationResult(UUID applicationId, List<ThreadResult> threads) {}

	private final CurrentUser currentUser;
	private final ProfileSnapshotProvider profiles;
	private final ApplicationRepository applications;
	private final BankCatalogService banks;
	private final EmailGateway emailGateway;
	private final BankAgent agent;
	private final ObjectMapper mapper;

	public ApplicationService(CurrentUser currentUser, ProfileSnapshotProvider profiles,
			ApplicationRepository applications, BankCatalogService banks, EmailGateway emailGateway,
			BankAgent agent, ObjectMapper mapper) {
		this.currentUser = currentUser;
		this.profiles = profiles;
		this.applications = applications;
		this.banks = banks;
		this.emailGateway = emailGateway;
		this.agent = agent;
		this.mapper = mapper;
	}

	/** @throws IllegalArgumentException if no bank is chosen or a bank id is unknown */
	@Transactional
	public ApplicationResult submit(List<String> bankIds) {
		if (bankIds == null || bankIds.isEmpty()) {
			throw new IllegalArgumentException("Choose at least one bank");
		}
		List<BankCatalogService.Bank> selected = new LinkedHashSet<>(bankIds).stream().map(banks::find).toList();
		UUID userId = currentUser.id();
		List<ProfileFieldView> snapshot = profiles.snapshot(userId);
		Application application = applications.save(new Application(userId, toJson(snapshot),
				toJson(selected.stream().map(BankCatalogService.Bank::id).toList())));

		List<ThreadResult> results = new ArrayList<>();
		for (BankCatalogService.Bank bank : selected) {
			EmailThread thread = emailGateway.createThread(application.getId(), userId, bank.id());
			emailGateway.addBankEmail(thread.getId(), agent.initialReply(bank, application.getSnapshotJson()));
			results.add(new ThreadResult(thread.getId(), bank.id(), thread.getStatus().name()));
		}
		return new ApplicationResult(application.getId(), results);
	}

	private String toJson(Object value) {
		try {
			return mapper.writeValueAsString(value);
		} catch (JacksonException e) {
			throw new IllegalStateException("Could not freeze the application snapshot", e);
		}
	}
}

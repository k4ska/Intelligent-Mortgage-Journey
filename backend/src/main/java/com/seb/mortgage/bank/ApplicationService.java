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
		this.currentUser = currentUser; this.profiles = profiles; this.applications = applications;
		this.banks = banks; this.emailGateway = emailGateway; this.agent = agent; this.mapper = mapper;
	}

	@Transactional
	public ApplicationResult submit(List<String> bankIds) {
		if (bankIds == null || bankIds.isEmpty()) throw new IllegalArgumentException("choose at least one bank");
		List<String> selected = new ArrayList<>(new LinkedHashSet<>(bankIds));
		selected.forEach(banks::find);
		UUID userId = currentUser.id();
		List<ProfileFieldView> snapshot = profiles.snapshot(userId);
		try {
			Application application = applications.save(new Application(userId,
					mapper.writeValueAsString(snapshot), mapper.writeValueAsString(selected)));
			List<ThreadResult> results = new ArrayList<>();
			for (String bankId : selected) {
				BankCatalogService.Bank bank = banks.find(bankId);
				EmailThread thread = emailGateway.createThread(application.getId(), userId, bankId);
				emailGateway.addBankEmail(thread.getId(), agent.initialReply(bank, application.getSnapshotJson()));
				results.add(new ThreadResult(thread.getId(), bankId, thread.getStatus().name()));
			}
			return new ApplicationResult(application.getId(), results);
		} catch (JacksonException e) {
			throw new IllegalStateException("Could not freeze application snapshot", e);
		}
	}
}
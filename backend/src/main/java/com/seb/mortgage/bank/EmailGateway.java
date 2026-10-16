package com.seb.mortgage.bank;

import java.util.UUID;

public interface EmailGateway {
	EmailThread createThread(UUID applicationId, UUID userId, String bankId);
	Email addBankEmail(UUID threadId, BankAgent.Draft draft);
}
package com.seb.mortgage.bank;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class BankController {

	public record BankOption(String id, String name) {}

	public record ApplicationRequest(List<String> bankIds) {}

	public record ErrorView(String error) {}

	private final BankCatalogService banks;
	private final ApplicationService applications;

	public BankController(BankCatalogService banks, ApplicationService applications) {
		this.banks = banks;
		this.applications = applications;
	}

	@GetMapping("/banks")
	public List<BankOption> banks() {
		return banks.banks().stream().map(bank -> new BankOption(bank.id(), bank.name())).toList();
	}

	@PostMapping("/applications")
	public ApplicationService.ApplicationResult submit(@RequestBody ApplicationRequest request) {
		return applications.submit(request.bankIds());
	}

	/** No banks chosen, or an unknown bank id. */
	@ExceptionHandler(IllegalArgumentException.class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public ErrorView invalidApplication(IllegalArgumentException e) {
		return new ErrorView(e.getMessage());
	}
}

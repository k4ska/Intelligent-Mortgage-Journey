package com.seb.mortgage.mockdata;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Mock "what documents will I need" service. Adapts the checklist to a few answers.
 */
@Service
public class DocumentChecklistService {

	public List<String> requiredDocuments(Map<String, Object> answers) {
		List<String> docs = new ArrayList<>(List.of(
				"Photo ID (passport or national ID)",
				"Last 3 months of bank statements",
				"Proof of the down payment funds"
		));

		String employment = String.valueOf(answers.getOrDefault("employmentType", "")).toUpperCase();
		if (employment.contains("SELF") || employment.contains("BUSINESS")) {
			docs.add("Last 2 years of business accounts");
			docs.add("Last 2 years of tax returns");
		} else {
			docs.add("Last 3 payslips");
			docs.add("Employer confirmation letter");
		}

		if (Boolean.TRUE.equals(answers.get("hasCoApplicant"))) {
			docs.add("The same income and ID documents for the co-applicant");
		}

		Object purpose = answers.get("loanPurpose");
		if (purpose != null && purpose.toString().equalsIgnoreCase("REFINANCE")) {
			docs.add("Current mortgage statement");
			docs.add("Latest property tax assessment");
		} else {
			docs.add("Signed purchase agreement (once available)");
		}

		return docs;
	}
}

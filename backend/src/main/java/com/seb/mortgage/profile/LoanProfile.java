package com.seb.mortgage.profile;

import java.util.List;

/**
 * Contract 3 of 3 (see README): the standardized loan profile.
 *
 * <p>Assembled from a completed (or in-progress) application plus derived financials.
 * This is the single structured object that feeds the mock multi-bank comparison screen,
 * and conceptually the object that could be shared across banks.
 */
public record LoanProfile(
        List<Applicant> applicants,
        Household household,
        Property property,
        LoanRequest loanRequest,
        Financials financials,
        List<String> riskIndicators,
        List<String> documents
) {

    public record Applicant(
            /** Not collected for the primary applicant; present for the co-applicant. */
            String name,
            /** Not collected for the co-applicant. */
            String employment,
            double monthlyNetIncome,
            double monthlyDebtPayments,
            /** Total credit card / overdraft limits, used or not. */
            double creditLimits
    ) {}

    /** Asked once per household, not per applicant. */
    public record Household(
            int adults,
            int dependants,
            /** What the applicant entered; null when left blank. */
            Double enteredMonthlyCosts,
            /** The least a lender assumes for this household size. */
            double minimumMonthlyCosts
    ) {}

    public record Property(
            String type,
            String location,
            double purchasePrice,
            double estimatedValue
    ) {}

    public record LoanRequest(
            double amount,
            int termYears,
            String rateType,
            String purpose
    ) {}

    public record Financials(
            double downPayment,
            /** Loan-to-value, %. */
            double ltv,
            /** All loan payments incl. the new mortgage as % of net income, at today's rate. */
            double dti,
            /** The same at the stress-test rate - the figure the 50% limit applies to. */
            double stressedDti,
            double estimatedMonthlyPayment,
            /** Monthly payment if rates rose by the stress-test delta. */
            double stressedMonthlyPayment,
            /** Living costs counted: the entered figure, but never below the household minimum. */
            double monthlyLivingCosts,
            /** The monthly debt payment the credit limits count as; included in dti and stressedDti. */
            double creditLimitMonthlyCost,
            /** Net income minus debts, credit-limit cost, living costs and the stressed payment. */
            double monthlyBufferAfterStress,
            String stressTestResult
    ) {}
}
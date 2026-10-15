package com.seb.mortgage.profile;

import com.seb.mortgage.application.LoanApplication;
import com.seb.mortgage.mockdata.AffordabilityCalculator;
import com.seb.mortgage.mockdata.DocumentChecklistService;
import com.seb.mortgage.mockdata.PropertyValuationService;
import com.seb.mortgage.mockdata.RateService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Turns the free-form answers on a {@link LoanApplication} into the standardized
 * {@link LoanProfile}, filling gaps with mock-data services and derived financials.
 *
 * <p>Missing answers are filled with defaults so the profile is always complete. Agents
 * get {@link #facts} instead, which leaves those defaults out.
 */
@Service
public class LoanProfileAssembler {

    /**
     * Answers that {@link #assemble} replaces with a default when they are missing. For a
     * self-employed applicant {@code lastYearNetProfit} takes the place of {@code monthlyNetIncome}.
     */
    static final List<String> DEFAULTED_KEYS = List.of(
            "loanPurpose", "propertyAddress", "propertyType", "purchasePrice", "employmentType",
            "monthlyNetIncome", "monthlyDebtPayments", "downPayment", "loanAmount", "termYears",
            "rateType", "dependants", "totalCreditLimits");

    /** Monthly net income assumed when none is given yet, so the profile is always complete. */
    private static final double DEFAULT_MONTHLY_NET_INCOME = 2_500;

    private static final List<String> LOAN_INPUTS = List.of("purchasePrice", "downPayment", "loanAmount");
    private static final List<String> PAYMENT_INPUTS = List.of(
            "purchasePrice", "downPayment", "loanAmount", "termYears", "rateType", "fixedYears");
    private static final List<String> CO_APPLICANT_INPUTS = List.of(
            "coApplicantMonthlyIncome", "coApplicantMonthlyDebtPayments", "coApplicantCreditLimits");
    private static final List<String> CO_APPLICANT_REQUIRED = List.of(
            "coApplicantName", "coApplicantMonthlyIncome", "coApplicantMonthlyDebtPayments",
            "coApplicantCreditLimits");
    /** monthlyHouseholdCosts is optional: left blank, the household minimum is used. */
    private static final List<String> LIVING_COST_INPUTS = List.of(
            "hasCoApplicant", "dependants", "monthlyHouseholdCosts");

    private final AffordabilityCalculator calculator;
    private final PropertyValuationService valuationService;
    private final RateService rateService;
    private final DocumentChecklistService documentChecklistService;

    public LoanProfileAssembler(AffordabilityCalculator calculator,
            PropertyValuationService valuationService,
            RateService rateService,
            DocumentChecklistService documentChecklistService) {
        this.calculator = calculator;
        this.valuationService = valuationService;
        this.rateService = rateService;
        this.documentChecklistService = documentChecklistService;
    }

    public LoanProfile assemble(LoanApplication application) {
        Map<String, Object> a = application.getAnswers();

        double purchasePrice = num(a.get("purchasePrice"), 250_000);
        double downPayment = num(a.get("downPayment"), 25_000);
        double loanAmount = Math.max(0, num(a.get("loanAmount"), purchasePrice - downPayment));
        int termYears = (int) num(a.get("termYears"), 25);
        String rateType = str(a.get("rateType"), "VARIABLE");
        Integer fixedYears = a.get("fixedYears") == null ? null : (int) num(a.get("fixedYears"), 0);

        double estimatedValue = valuationService
                .estimate(str(a.get("propertyAddress"), ""), purchasePrice)
                .estimatedValue();

        double monthlyIncome = monthlyNetIncome(a);
        double monthlyDebt = num(a.get("monthlyDebtPayments"), 0);
        boolean hasCoApplicant = Boolean.TRUE.equals(a.get("hasCoApplicant"));
        double coIncome = hasCoApplicant ? num(a.get("coApplicantMonthlyIncome"), 0) : 0;
        double coDebt = hasCoApplicant ? num(a.get("coApplicantMonthlyDebtPayments"), 0) : 0;
        double creditLimits = num(a.get("totalCreditLimits"), 0);
        double coCreditLimits = hasCoApplicant ? num(a.get("coApplicantCreditLimits"), 0) : 0;
        double creditLimitCost = calculator.creditLimitMonthlyCost(creditLimits + coCreditLimits);
        double householdIncome = monthlyIncome + coIncome;
        // Unused credit limits count as debt: the money could be drawn at any time.
        double householdDebt = monthlyDebt + coDebt + creditLimitCost;

        int adults = hasCoApplicant ? 2 : 1;
        int dependants = (int) num(a.get("dependants"), 0);
        Double enteredCosts = optionalNum(a.get("monthlyHouseholdCosts"));
        LoanProfile.Household household = new LoanProfile.Household(adults, dependants, enteredCosts,
                calculator.minimumLivingCosts(adults, dependants));
        double livingCosts = calculator.livingCosts(enteredCosts, adults, dependants);

        double rate = rateService.rateFor(rateType, fixedYears);
        double payment = calculator.monthlyPayment(loanAmount, rate, termYears);
        double stressed = calculator.stressedMonthlyPayment(loanAmount, rate, termYears);
        double buffer = round2(householdIncome - householdDebt - livingCosts - stressed);

        // LTV at origination is measured against the purchase price (what you agreed to pay).
        double ltv = calculator.loanToValue(loanAmount, purchasePrice);
        double dti = calculator.debtToIncome(householdDebt + payment, householdIncome);
        double stressedDti = calculator.debtToIncome(householdDebt + stressed, householdIncome);

        String stressResult = stressedDti > AffordabilityCalculator.MAX_DTI_PCT || buffer < 0 ? "FAIL"
                : buffer < AffordabilityCalculator.MIN_STRESS_BUFFER ? "TIGHT" : "PASS";

        LoanProfile.Financials financials = new LoanProfile.Financials(
                downPayment, ltv, dti, stressedDti, round2(payment), round2(stressed), livingCosts,
                creditLimitCost, buffer, stressResult);

        List<LoanProfile.Applicant> applicants = new ArrayList<>();
        applicants.add(new LoanProfile.Applicant(
                null, str(a.get("employmentType"), "EMPLOYED"), monthlyIncome, monthlyDebt, creditLimits));
        if (hasCoApplicant) {
            applicants.add(new LoanProfile.Applicant(
                    str(a.get("coApplicantName"), null), null, coIncome, coDebt, coCreditLimits));
        }

        LoanProfile.Property property = new LoanProfile.Property(
                str(a.get("propertyType"), "APARTMENT"),
                str(a.get("propertyLocation"), str(a.get("propertyAddress"), "Unknown")),
                purchasePrice, estimatedValue);

        LoanProfile.LoanRequest loanRequest = new LoanProfile.LoanRequest(
                loanAmount, termYears, rateType, str(a.get("loanPurpose"), "PURCHASE"));

        return new LoanProfile(
                List.copyOf(applicants),
                household,
                property,
                loanRequest,
                financials,
                riskIndicators(ltv, dti, financials, household, purchasePrice, estimatedValue),
                documentChecklistService.requiredDocuments(a));
    }

    /**
     * The application as the agents may see it: only the answers the user really gave, and
     * only the derived figures whose inputs are all real. See {@link ProfileFacts}.
     */
    public ProfileFacts facts(LoanApplication application) {
        Map<String, Object> entered = new LinkedHashMap<>();
        application.getAnswers().forEach((key, value) -> {
            if (value != null && !value.toString().isBlank()) {
                entered.put(key, value);
            }
        });
        boolean hasCoApplicant = Boolean.TRUE.equals(entered.get("hasCoApplicant"));
        String incomeKey = incomeKey(entered);
        List<String> requiredKeys = Stream.concat(
                        DEFAULTED_KEYS.stream().map(k -> k.equals("monthlyNetIncome") ? incomeKey : k),
                        hasCoApplicant ? CO_APPLICANT_REQUIRED.stream() : Stream.empty())
                .toList();
        List<String> notAnsweredYet = requiredKeys.stream().filter(k -> !entered.containsKey(k)).toList();

        LoanProfile p = assemble(application);
        LoanProfile.Financials f = p.financials();
        double purchasePrice = p.property().purchasePrice();
        double estimatedValue = p.property().estimatedValue();

        boolean hasPrice = entered.containsKey("purchasePrice");
        boolean hasLoan = entered.containsKey("loanAmount") || (hasPrice && entered.containsKey("downPayment"));
        boolean hasPayment = hasLoan && entered.containsKey("termYears") && entered.containsKey("rateType");
        boolean hasLivingCosts = entered.containsKey("dependants");
        boolean hasCreditLimits = entered.containsKey("totalCreditLimits")
                && (!hasCoApplicant || entered.containsKey("coApplicantCreditLimits"));
        boolean hasAffordability = hasPayment && entered.containsKey(incomeKey)
                && entered.containsKey("monthlyDebtPayments") && hasLivingCosts && hasCreditLimits
                && (!hasCoApplicant || CO_APPLICANT_INPUTS.stream().allMatch(entered::containsKey));
        List<String> creditLimitInputs = hasCoApplicant
                ? List.of("totalCreditLimits", "coApplicantCreditLimits")
                : List.of("totalCreditLimits");
        List<String> affordabilityInputs = Stream.of(PAYMENT_INPUTS.stream(),
                        Stream.of("employmentType", incomeKey, "monthlyDebtPayments", "totalCreditLimits"),
                        LIVING_COST_INPUTS.stream(),
                        hasCoApplicant ? CO_APPLICANT_INPUTS.stream() : Stream.<String>empty())
                .flatMap(keys -> keys).distinct().toList();

        List<ProfileFacts.Figure> figures = new ArrayList<>();
        if (hasPrice) {
            figures.add(new ProfileFacts.Figure("estimatedPropertyValue", estimatedValue,
                    List.of("purchasePrice", "propertyAddress"), valuationRisk(purchasePrice, estimatedValue)));
        }
        if (hasLoan && !entered.containsKey("loanAmount")) {
            figures.add(new ProfileFacts.Figure("loanAmountFromPriceMinusDownPayment",
                    p.loanRequest().amount(), LOAN_INPUTS, null));
        }
        if (hasPrice && hasLoan) {
            figures.add(new ProfileFacts.Figure("loanToValuePercent", f.ltv(), LOAN_INPUTS, ltvRisk(f.ltv())));
        }
        if (hasPayment) {
            figures.add(new ProfileFacts.Figure("estimatedMonthlyPayment", f.estimatedMonthlyPayment(),
                    PAYMENT_INPUTS, null));
            figures.add(new ProfileFacts.Figure("stressedMonthlyPayment", f.stressedMonthlyPayment(),
                    PAYMENT_INPUTS, null));
        }
        if (hasLivingCosts) {
            figures.add(new ProfileFacts.Figure("monthlyLivingCosts", f.monthlyLivingCosts(),
                    LIVING_COST_INPUTS, livingCostRisk(p.household())));
        }
        if (hasCreditLimits) {
            figures.add(new ProfileFacts.Figure("creditLimitMonthlyCost", f.creditLimitMonthlyCost(),
                    creditLimitInputs, creditLimitRisk(f.creditLimitMonthlyCost())));
        }
        if (hasAffordability) {
            figures.add(new ProfileFacts.Figure("debtToIncomePercent", f.dti(), affordabilityInputs,
                    dtiRisk(f.dti())));
            figures.add(new ProfileFacts.Figure("stressedDebtToIncomePercent", f.stressedDti(),
                    affordabilityInputs, null));
            figures.add(new ProfileFacts.Figure("monthlyBufferAfterStress", f.monthlyBufferAfterStress(),
                    affordabilityInputs, null));
            figures.add(new ProfileFacts.Figure("stressTestResult", f.stressTestResult(),
                    affordabilityInputs, stressRisk(f)));
        }
        return new ProfileFacts(entered, notAnsweredYet, figures, p.documents());
    }

    private static List<String> riskIndicators(double ltv, double dti, LoanProfile.Financials f,
            LoanProfile.Household household, double purchasePrice, double estimatedValue) {
        List<String> risks = Stream.of(valuationRisk(purchasePrice, estimatedValue), ltvRisk(ltv),
                dtiRisk(dti), stressRisk(f), creditLimitRisk(f.creditLimitMonthlyCost()),
                livingCostRisk(household)).filter(Objects::nonNull).toList();
        return risks.isEmpty()
                ? List.of("No major risk indicators from the information provided so far")
                : risks;
    }

    private static String valuationRisk(double purchasePrice, double estimatedValue) {
        return estimatedValue < purchasePrice * 0.97
                ? "The automated valuation (" + (long) estimatedValue + ") is below the price you "
                        + "agreed (" + (long) purchasePrice + ") - a lender may lend against the lower figure"
                : null;
    }

    private static String ltvRisk(double ltv) {
        return ltv > 90 ? "High loan-to-value (" + ltv + "%) - limited lender choice and a higher rate" : null;
    }

    private static String dtiRisk(double dti) {
        return dti > AffordabilityCalculator.MAX_DTI_PCT
                ? "Loan payments already take " + dti + "% of net income at today's rate - above the "
                        + (int) AffordabilityCalculator.MAX_DTI_PCT + "% lenders may allow"
                : null;
    }

    private static String stressRisk(LoanProfile.Financials f) {
        if ("FAIL".equals(f.stressTestResult())) {
            return "Fails the stress test - at the stress-test rate, loan payments would take "
                    + f.stressedDti() + "% of net income, above the "
                    + (int) AffordabilityCalculator.MAX_DTI_PCT + "% limit";
        }
        if ("TIGHT".equals(f.stressTestResult())) {
            return "Thin buffer (" + f.monthlyBufferAfterStress() + "/month) after a rate-rise stress test";
        }
        return null;
    }

    private static String creditLimitRisk(double creditLimitMonthlyCost) {
        return creditLimitMonthlyCost > 0
                ? "Credit card and overdraft limits count as " + Math.round(creditLimitMonthlyCost) + "/month of debt even if "
                        + "unused - closing limits you don't need would raise what you can borrow"
                : null;
    }

    private static String livingCostRisk(LoanProfile.Household h) {
        Double entered = h.enteredMonthlyCosts();
        return entered != null && entered < h.minimumMonthlyCosts()
                ? "Household costs of " + Math.round(entered) + "/month are below the " + Math.round(h.minimumMonthlyCosts())
                        + "/month lenders assume for " + h.adults() + (h.adults() == 1 ? " adult" : " adults")
                        + " and " + h.dependants() + (h.dependants() == 1 ? " dependant" : " dependants")
                        + ", so the minimum is used"
                : null;
    }

    private static boolean selfEmployed(Map<String, Object> answers) {
        return "SELF_EMPLOYED".equals(str(answers.get("employmentType"), "EMPLOYED"));
    }

    /** The answer income comes from: last year's net profit when self-employed, else monthly net income. */
    private static String incomeKey(Map<String, Object> answers) {
        return selfEmployed(answers) ? "lastYearNetProfit" : "monthlyNetIncome";
    }

    /** Self-employed applicants count a twelfth of last year's net profit. */
    private static double monthlyNetIncome(Map<String, Object> answers) {
        return selfEmployed(answers)
                ? round2(num(answers.get("lastYearNetProfit"), DEFAULT_MONTHLY_NET_INCOME * 12) / 12)
                : num(answers.get("monthlyNetIncome"), DEFAULT_MONTHLY_NET_INCOME);
    }

    private static double num(Object o, double fallback) {
        if (o instanceof Number n) {
            return n.doubleValue();
        }
        if (o != null) {
            try {
                return Double.parseDouble(o.toString().replace(",", "").trim());
            } catch (NumberFormatException ignored) {
                // fall through
            }
        }
        return fallback;
    }

    /** A number, or null when the answer is missing or blank (an optional field left empty). */
    private static Double optionalNum(Object o) {
        if (o == null || o.toString().isBlank()) {
            return null;
        }
        double value = num(o, Double.NaN);
        return Double.isNaN(value) ? null : value;
    }

    private static String str(Object o, String fallback) {
        return o == null || o.toString().isBlank() ? fallback : o.toString();
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
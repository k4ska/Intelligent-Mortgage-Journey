// Mirrors the backend's three contracts. Keep in sync with:
//   backend/src/main/java/com/seb/mortgage/flow/FlowDefinition.java
//   backend/src/main/java/com/seb/mortgage/agent/AgentDtos.java
//   backend/src/main/java/com/seb/mortgage/profile/LoanProfile.java

// ---- Contract 1: flow definition ----

export type FieldType =
  | 'TEXT' | 'MONEY' | 'NUMBER' | 'PERCENT' | 'DATE' | 'SELECT' | 'RADIO' | 'BOOLEAN'

export interface FieldOption { value: string, label: string }

export interface FieldValidation {
  required?: boolean | null
  min?: number | null
  max?: number | null
  pattern?: string | null
}

export interface Field {
  key: string
  type: FieldType
  label: string
  helpKey?: string | null
  placeholder?: string | null
  options?: FieldOption[] | null
  validation?: FieldValidation | null
}

export interface Step {
  id: string
  title: string
  subtitle?: string | null
  fields: Field[]
  next?: unknown[]
}

export interface FlowDefinition {
  version: string
  steps: Step[]
}

// ---- Application ----

export type Answers = Record<string, unknown>

export interface ApplicationView {
  id: string
  currentStepId: string
  complete: boolean
  answers: Answers
  createdAt: string
  updatedAt: string
}

// ---- Contract 2: agent ----

export type ValidationStatus = 'OK' | 'WARNING' | 'BLOCKER'

export interface ValidationIssue {
  fieldKey: string | null
  severity: ValidationStatus
  message: string
}

// `followUpQuestion` is set when the agent couldn't do its job well with what it was
// given and wants to ask the applicant something before it can answer better. Show it as
// a small inline prompt; send the applicant's reply back as extra context on a normal
// follow-up call to the same endpoint (not as open-ended chat).

export interface ValidateResponse {
  status: ValidationStatus
  issues: ValidationIssue[]
  followUpQuestion: string | null
}

export interface ExplainResponse {
  explanation: string
  relatedTopics: string[]
  followUpQuestion: string | null
}

export interface ReviewResponse {
  summary: string
  concerns: string[]
  followUpQuestion: string | null
}

export interface AgentStatus {
  mode: 'stub' | 'claude'
}

// ---- Contract 3: loan profile ----

export interface LoanProfile {
  applicants: Array<{
    employment: string
    monthlyGrossIncome: number
    monthlyDebtPayments: number
  }>
  property: {
    type: string
    location: string
    purchasePrice: number
    estimatedValue: number
  }
  loanRequest: {
    amount: number
    termYears: number
    rateType: string
    purpose: string
  }
  financials: {
    downPayment: number
    ltv: number
    dti: number
    estimatedMonthlyPayment: number
    stressedMonthlyPayment: number
    monthlyBufferAfterStress: number
    stressTestResult: string
  }
  riskIndicators: string[]
  documents: string[]
}

export interface BankOffer {
  bankId: string
  bankName: string
  preApproved: boolean
  offeredRatePct: number
  maxLoanAmount: number
  estimatedMonthlyPayment: number
  conditions: string[]
}

// ---- Standalone calculator ----

export interface CalculatorEstimateRequest {
  propertyPrice: number | null
  downPayment: number | null
  annualRatePct?: number | null
  termYears?: number | null
}

export interface CalculatorEstimateResponse {
  loanAmount: number
  monthlyPayment: number
  ltv: number
  annualRatePct: number
  termYears: number
}

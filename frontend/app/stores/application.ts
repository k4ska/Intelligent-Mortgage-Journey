import { defineStore } from 'pinia'
import type {
  AgentMode,
  ApplicationView,
  FlowDefinition,
  Step,
  ValidateResponse,
} from '~/types/api'

/**
 * The whole journey lives here: the flow definition, the current application, the
 * answers being edited, and the agent's latest validation for the current step.
 */
export const useApplicationStore = defineStore('application', {
  state: () => ({
    flow: null as FlowDefinition | null,
    agentMode: 'stub' as AgentMode,
    application: null as ApplicationView | null,
    /** Draft answers for the step on screen, before they are submitted. */
    draft: {} as Record<string, unknown>,
    validation: null as ValidateResponse | null,
    loading: false,
    /** True while the agent is checking the current step's answers. */
    validating: false,
    error: null as string | null,
    assistantOpen: true,
  }),

  getters: {
    currentStep(state): Step | null {
      if (!state.flow || !state.application) return null
      return state.flow.steps.find(s => s.id === state.application!.currentStepId) ?? null
    },
    /** The steps this user goes through, given their answers (branches they skip are left out). */
    pathSteps(state): Step[] {
      if (!state.flow || !state.application) return []
      const steps = state.flow.steps
      return state.application.path
        .map(id => steps.find(s => s.id === id))
        .filter((s): s is Step => !!s)
    },
    stepIndex(state): number {
      return state.application?.path.indexOf(state.application.currentStepId) ?? 0
    },
    totalSteps(state): number {
      return state.application?.path.length ?? 0
    },
    /** Share of the path the user has completed, 0-100. */
    progress(state): number {
      const app = state.application
      return app?.path.length ? Math.round((app.completedSteps.length / app.path.length) * 100) : 0
    },
    previousStepId(state): string | null {
      const app = state.application
      if (!app) return null
      const index = app.path.indexOf(app.currentStepId)
      return index > 0 ? app.path[index - 1] ?? null : null
    },
    isLastStep(state): boolean {
      const path = state.application?.path ?? []
      return path.length > 0 && path[path.length - 1] === state.application?.currentStepId
    },
    /** Every step on the path is done. */
    isComplete(state): boolean {
      return state.application?.complete ?? false
    },
    /** Show the final summary: everything done and the user is on the last step. */
    showSummary(): boolean {
      return this.isComplete && this.isLastStep
    },
    isStepCompleted(state) {
      return (stepId: string) => state.application?.completedSteps.includes(stepId) ?? false
    },
    isStepUnlocked(state) {
      return (stepId: string) => state.application?.unlockedSteps.includes(stepId) ?? false
    },
    /** True when a real LLM (Claude or OpenAI) backs the agent, not the stub. */
    agentLive(state): boolean {
      return state.agentMode !== 'stub'
    },
    /** Display name of whoever answers: 'Claude', 'OpenAI' or 'demo stub'. */
    assistantName(state): string {
      return { claude: 'Claude', openai: 'OpenAI', stub: 'demo stub' }[state.agentMode]
    },
  },

  actions: {
    async bootstrap() {
      const api = useApi()
      const [flow, status] = await Promise.all([api.getFlow(), api.getAgentStatus()])
      this.flow = flow
      this.agentMode = status.mode
    },

    async startNewApplication() {
      const api = useApi()
      this.loading = true
      try {
        this.application = await api.createApplication()
        this.resetDraftFromAnswers()
      } finally {
        this.loading = false
      }
    },

    async loadApplication(id: string) {
      const api = useApi()
      this.loading = true
      try {
        if (!this.flow) await this.bootstrap()
        this.application = await api.getApplication(id)
        this.resetDraftFromAnswers()
      } catch (e) {
        this.error = (e as Error).message
      } finally {
        this.loading = false
      }
    },

    resetDraftFromAnswers() {
      const answers = this.application?.answers ?? {}
      const step = this.currentStep
      this.draft = {}
      if (step) {
        for (const field of step.fields) {
          this.draft[field.key] = answers[field.key] ?? defaultFor(field.type)
        }
      }
      this.validation = null
    },

    setField(key: string, value: unknown) {
      this.draft[key] = value
    },

    /**
     * Open another step the user has already reached. The answers on screen are saved
     * first so nothing typed is lost. If saving them changes the route (e.g. a new
     * co-applicant step appears) and the target is no longer open, the user stays put.
     */
    async goToStep(stepId: string) {
      const api = useApi()
      if (!this.application || stepId === this.application.currentStepId) return
      this.loading = true
      try {
        if (this.currentStep?.fields.length && !this.showSummary) {
          this.application = await api.submitStep(this.application.id, this.currentStep.id, this.draft, false)
        }
        if (!this.application.unlockedSteps.includes(stepId)) return
        this.application = await api.goToStep(this.application.id, stepId)
        this.resetDraftFromAnswers()
      } finally {
        this.loading = false
      }
    },

    async goBack() {
      if (this.previousStepId) await this.goToStep(this.previousStepId)
    },

    async runValidation() {
      const api = useApi()
      if (!this.application || !this.currentStep) return
      this.validating = true
      try {
        // Persist the draft first so the agent validates against real state.
        await this.submitDraft({ advance: false })
        this.validation = await api.validate(this.application.id, this.currentStep.id)
      } finally {
        this.validating = false
      }
    },

    /**
     * Send the current draft to the backend. When advance is true the flow moves to
     * the next step; when false the answers are just saved (used before validation).
     */
    async submitDraft({ advance = true }: { advance?: boolean } = {}) {
      const api = useApi()
      if (!this.application || !this.currentStep) return
      const stepId = this.currentStep.id
      this.loading = true
      try {
        this.application = await api.submitStep(this.application.id, stepId, this.draft, advance)
        if (advance) this.resetDraftFromAnswers()
      } finally {
        this.loading = false
      }
    },
  },
})

function defaultFor(type: string): unknown {
  if (type === 'BOOLEAN') return false
  if (type === 'MONEY' || type === 'NUMBER' || type === 'PERCENT') return null
  return ''
}

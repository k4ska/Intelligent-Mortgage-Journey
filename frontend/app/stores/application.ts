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
    error: null as string | null,
    assistantOpen: true,
  }),

  getters: {
    currentStep(state): Step | null {
      if (!state.flow || !state.application) return null
      return state.flow.steps.find(s => s.id === state.application!.currentStepId) ?? null
    },
    stepIndex(state): number {
      if (!state.flow || !state.application) return 0
      return state.flow.steps.findIndex(s => s.id === state.application!.currentStepId)
    },
    totalSteps(state): number {
      return state.flow?.steps.length ?? 0
    },
    isComplete(state): boolean {
      return state.application?.complete ?? false
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

    async runValidation() {
      const api = useApi()
      if (!this.application || !this.currentStep) return
      // Persist the draft first so the agent validates against real state.
      await this.submitDraft({ advance: false })
      this.validation = await api.validate(this.application.id, this.currentStep.id)
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
        const updated = await api.submitStep(this.application.id, stepId, this.draft)
        if (advance) {
          this.application = updated
          this.resetDraftFromAnswers()
        } else {
          // keep the user on the same step; just refresh stored answers
          this.application = { ...updated, currentStepId: stepId }
        }
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

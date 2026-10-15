<script setup lang="ts">
import { useAuthStore } from '~/stores/auth'
import { copy } from '~/utils/copy'

const auth = useAuthStore()
auth.error = ''
const email = ref('')
const password = ref('')
const { isMock } = useApi()

async function submit() { await auth.login(email.value, password.value) }
</script>

<template>
  <div class="flex h-full items-center justify-center overflow-y-auto p-4">
    <div class="w-full max-w-md rounded-2xl border border-[var(--ui-border)] bg-[var(--ui-bg)] p-7 shadow-sm">
      <div class="mb-6 flex size-11 items-center justify-center rounded-xl bg-[var(--ui-primary)] text-white">
        <UIcon name="i-lucide-house" class="text-2xl" />
      </div>
      <h1 class="text-2xl">{{ copy.loginTitle }}</h1>
      <p class="mt-2 text-sm text-[var(--ui-text-muted)]">{{ copy.loginSubtitle }}</p>
      <form class="mt-7 space-y-4" @submit.prevent="submit">
        <label class="block text-sm font-medium">
          {{ copy.email }}
          <input v-model="email" class="app-input mt-2" type="email" autocomplete="email" required />
        </label>
        <label class="block text-sm font-medium">
          {{ copy.password }}
          <input v-model="password" class="app-input mt-2" type="password" autocomplete="current-password" required minlength="1" />
        </label>
        <p v-if="auth.error" role="alert" class="text-sm text-red-600">{{ auth.error }}</p>
        <UButton type="submit" block size="lg" :loading="auth.busy">{{ copy.login }}</UButton>
      </form>
      <p class="mt-6 text-center text-sm text-[var(--ui-text-muted)]">
        {{ copy.noAccount }} <NuxtLink to="/register" class="font-semibold text-[var(--ui-primary)] hover:underline">{{ copy.register }}</NuxtLink>
      </p>
      <p v-if="isMock" class="mt-5 text-xs text-[var(--ui-text-dimmed)]">{{ copy.mockNotice }}</p>
    </div>
  </div>
</template>
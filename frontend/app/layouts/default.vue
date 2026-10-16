<script setup lang="ts">
import { useAuthStore } from '~/stores/auth'
import { useInboxStore } from '~/stores/inbox'
import { copy } from '~/utils/copy'

const auth = useAuthStore()
const inbox = useInboxStore()

watch(() => auth.isAuthenticated, (authenticated) => {
  if (authenticated) void inbox.load()
}, { immediate: true })
</script>

<template>
  <div class="flex h-dvh min-h-0 flex-col overflow-hidden bg-[var(--ui-bg-muted)]">
    <header class="shrink-0 border-b border-[var(--ui-border)] bg-[var(--ui-bg)]">
      <div class="mx-auto flex h-16 w-full max-w-[1800px] items-center justify-between gap-3 px-4 lg:px-6">
        <NuxtLink to="/" class="flex min-w-0 items-center gap-2.5">
          <span class="flex size-9 shrink-0 items-center justify-center rounded-xl bg-[var(--ui-primary)]">
            <UIcon name="i-lucide-house" class="text-lg text-white" />
          </span>
          <span class="hidden truncate font-semibold sm:block">{{ copy.appName }}</span>
        </NuxtLink>

        <nav v-if="auth.isAuthenticated" :aria-label="copy.appName" class="flex items-center gap-1 rounded-xl bg-[var(--ui-bg-muted)] p-1">
          <NuxtLink to="/" class="app-nav-link" active-class="app-nav-link-active" exact-active-class="app-nav-link-active">
            <UIcon name="i-lucide-messages-square" />{{ copy.chatTab }}
          </NuxtLink>
          <NuxtLink to="/inbox" class="app-nav-link" active-class="app-nav-link-active">
            <UIcon name="i-lucide-mail" />{{ copy.inboxTab }}
            <span v-if="inbox.unreadCount" class="rounded-full bg-[var(--ui-primary)] px-1.5 text-xs font-semibold text-white">{{ inbox.unreadCount }}<span class="sr-only"> {{ copy.unread.toLowerCase() }}</span></span>
          </NuxtLink>
        </nav>

        <div class="flex items-center gap-2">
          <UColorModeButton :aria-label="copy.theme" />
          <UDropdownMenu
            v-if="auth.isAuthenticated"
            :items="[[{ label: auth.user?.email || '', disabled: true }], [{ label: copy.logout, icon: 'i-lucide-log-out', onSelect: () => auth.logout() }]]"
          >
            <UButton color="neutral" variant="ghost" trailing-icon="i-lucide-chevron-down" :aria-label="auth.user?.email || copy.logout">
              <UIcon name="i-lucide-circle-user-round" class="text-xl" />
              <span class="hidden max-w-36 truncate xl:inline">{{ auth.user?.email }}</span>
            </UButton>
          </UDropdownMenu>
        </div>
      </div>
    </header>

    <main class="min-h-0 flex-1"><slot /></main>

    <footer class="shrink-0 border-t border-[var(--ui-border)] bg-[var(--ui-bg)] px-2 py-2 text-center text-xs text-[var(--ui-text-dimmed)]">
      {{ copy.prototype }}
    </footer>
  </div>
</template>

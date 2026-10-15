<template>
  <div class="flex h-screen flex-col overflow-hidden bg-[var(--ui-bg-muted)]">
    <header class="shrink-0 border-b border-[var(--ui-border)] bg-[var(--ui-bg)]">
      <UContainer class="flex items-center justify-between py-2.5">
        <NuxtLink to="/" class="flex items-center gap-2.5">
          <span class="flex h-8 w-8 items-center justify-center rounded-md bg-[var(--ui-primary)]">
            <UIcon name="i-lucide-key-round" class="text-white text-base" />
          </span>
          <span class="font-serif text-[15px] font-semibold tracking-tight" style="font-family: 'Source Serif 4', serif;">
            Intelligent Mortgage Journey
          </span>
        </NuxtLink>
        <UColorModeButton />
      </UContainer>
    </header>

    <main class="min-h-0 flex-1">
      <slot />
    </main>

    <footer class="shrink-0 py-1.5 text-center text-[11px] text-[var(--ui-text-dimmed)]">
      Prototype. Not affiliated with any bank. Offers are simulated.
    </footer>
  </div>
</template>

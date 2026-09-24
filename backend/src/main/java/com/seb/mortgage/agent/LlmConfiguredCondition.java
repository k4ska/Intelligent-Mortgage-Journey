package com.seb.mortgage.agent;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.StringUtils;

/**
 * Matches when the LLM provider selected by {@code spring.ai.model.chat} (set from
 * {@code AI_PROVIDER}, default {@code anthropic}) has a non-blank API key, i.e. when
 * {@code spring.ai.<provider>.api-key} has text.
 *
 * <p>This checks properties rather than using {@code @ConditionalOnBean(ChatClient.Builder)},
 * because our configuration is processed before Spring AI's auto-configuration, so that
 * bean never exists yet when the condition is evaluated.
 */
class LlmConfiguredCondition implements Condition {

	@Override
	public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
		Environment env = context.getEnvironment();
		String provider = env.getProperty("spring.ai.model.chat", "anthropic").trim().toLowerCase();
		if (provider.isEmpty() || provider.equals("none")) {
			return false;
		}
		return StringUtils.hasText(env.getProperty("spring.ai." + provider + ".api-key"));
	}
}

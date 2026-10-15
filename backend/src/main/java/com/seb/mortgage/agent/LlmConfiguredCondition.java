package com.seb.mortgage.agent;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.StringUtils;

/**
 * Matches when an OpenAI API key is configured, i.e. when {@code spring.ai.openai.api-key}
 * (set from {@code OPENAI_API_KEY}) has text.
 *
 * <p>This checks properties rather than using {@code @ConditionalOnBean(ChatClient.Builder)},
 * because our configuration is processed before Spring AI's auto-configuration, so that
 * bean never exists yet when the condition is evaluated.
 */
public class LlmConfiguredCondition implements Condition {

	@Override
	public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
		return StringUtils.hasText(context.getEnvironment().getProperty("spring.ai.openai.api-key"));
	}
}

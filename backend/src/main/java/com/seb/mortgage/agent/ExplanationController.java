package com.seb.mortgage.agent;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Serves the built-in {@link Explanations} so the frontend can show a field's definition
 * instantly, without an LLM call. The same text is the reference wording ExplainAgent
 * uses, so the popover and the AI never disagree on what a term means.
 */
@RestController
@RequestMapping("/api/explanations")
public class ExplanationController {

	/** Every definition, keyed by help key. Small enough to load once up front. */
	@GetMapping
	public Map<String, Explanations.Entry> all() {
		return Explanations.BY_KEY;
	}

	@GetMapping("/{key}")
	public ResponseEntity<Explanations.Entry> one(@PathVariable String key) {
		Explanations.Entry entry = Explanations.lookup(key);
		return entry == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(entry);
	}
}

package com.seb.mortgage.property;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * kv.ee listing pages saved from a browser and shipped with the app
 * ({@code src/main/resources/saved-listings/}), for the demo: kv.ee's bot check stops
 * every request we make, so when the user pastes one of these links, the advisor reads
 * the saved copy instead and kv.ee isn't contacted at all. Any other link is fetched as
 * before. To add a listing, save its page from the browser (the canonical link and the
 * schema.org block are all the parser needs) into that folder.
 */
@Component
public class SavedListings {

	private static final Logger log = LoggerFactory.getLogger(SavedListings.class);

	private static final Pattern CANONICAL = Pattern.compile(
			"<link[^>]+rel=\"canonical\"[^>]+href=\"([^\"]+)\"", Pattern.CASE_INSENSITIVE);
	/** The listing number at the end of the path: it stays the same across slugs, languages and www. */
	private static final Pattern NUMBER = Pattern.compile("(\\d{4,})(?:\\.html?)?/?$");

	private final Map<String, String> pages;

	public SavedListings() {
		this("classpath*:saved-listings/*.html");
	}

	SavedListings(String locationPattern) {
		this.pages = load(locationPattern);
	}

	/** The saved page for this listing link, if there is one. */
	public Optional<String> page(URI listing) {
		return number(listing.getPath()).map(pages::get);
	}

	private static Map<String, String> load(String locationPattern) {
		Map<String, String> pages = new HashMap<>();
		try {
			for (Resource resource : new PathMatchingResourcePatternResolver().getResources(locationPattern)) {
				String html = resource.getContentAsString(StandardCharsets.UTF_8);
				Matcher canonical = CANONICAL.matcher(html);
				Optional<String> number = canonical.find()
						? Optional.ofNullable(ListingReader.listingUri(canonical.group(1))).flatMap(uri -> number(uri.getPath()))
						: Optional.empty();
				if (number.isEmpty()) {
					log.warn("Saved listing {} has no kv.ee canonical link, so it is ignored", resource.getFilename());
					continue;
				}
				pages.put(number.get(), html);
			}
		} catch (IOException e) {
			throw new UncheckedIOException("Could not read the saved listings", e);
		}
		if (!pages.isEmpty()) {
			log.info("Saved kv.ee listings for the demo: {}", pages.keySet());
		}
		return Map.copyOf(pages);
	}

	private static Optional<String> number(String path) {
		if (path == null) {
			return Optional.empty();
		}
		Matcher matcher = NUMBER.matcher(path);
		return matcher.find() ? Optional.of(matcher.group(1)) : Optional.empty();
	}
}

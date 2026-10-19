package com.seb.mortgage.property;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;

/**
 * Looks up a kv.ee listing the user pasted (see {@code docs/architecture.md} §6). We play
 * fair: one request per link with our own user agent, a 5-second limit, only kv.ee listing
 * pages, and the result cached so the same link is never fetched twice. When kv.ee's bot
 * check stops us, we say so and the advisor asks the user instead. We never try to get
 * around it.
 */
@Component
public class ListingReader {

	/** What a lookup came to: the values read, or why there are none. */
	public sealed interface Lookup {
		record Found(String url, Map<PropertyKey, String> values) implements Lookup {}
		record NotRead(String reason) implements Lookup {}
	}

	/** One page download; replaced in tests so they never touch the network. */
	@FunctionalInterface
	public interface PageFetcher {
		Page fetch(URI uri) throws IOException, InterruptedException, TimeoutException;
	}

	public record Page(int status, String body) {}

	static final String USER_AGENT = "IntelligentMortgageJourney/0.1 (student project; one request per link a user pastes)";
	static final Duration TIMEOUT = Duration.ofSeconds(5);
	private static final int CACHE_SIZE = 500;

	/** A listing page: its path ends in the listing's number, e.g. /3-toaline-korter-...-3612345.html. */
	private static final Pattern LISTING_PATH = Pattern.compile("^/(?:[\\w%.-]+/)*[\\w%.-]*\\d{5,}(?:\\.html?)?/?$");

	/** Marks of kv.ee's (Cloudflare) bot check page. */
	private static final Pattern BOT_CHECK = Pattern.compile(
			"challenge-error-text|cdn-cgi/challenge-platform|Enable JavaScript and cookies to continue");

	private static final Logger log = LoggerFactory.getLogger(ListingReader.class);

	private final PageFetcher fetcher;
	private final Map<String, Lookup> cache = new LinkedHashMap<>(16, 0.75f, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<String, Lookup> eldest) {
			return size() > CACHE_SIZE;
		}
	};

	@Autowired
	public ListingReader() {
		this(httpFetcher());
	}

	/** For tests: reads pages from {@code fetcher} instead of kv.ee. */
	public ListingReader(PageFetcher fetcher) {
		this.fetcher = fetcher;
	}

	public Lookup read(String link) {
		URI uri = listingUri(link);
		if (uri == null) {
			return new Lookup.NotRead("that isn't a kv.ee listing link, so it wasn't opened");
		}
		String key = uri.toString();
		synchronized (cache) {
			Lookup cached = cache.get(key);
			if (cached != null) {
				return cached;
			}
		}
		Lookup lookup = fetchAndParse(uri);
		synchronized (cache) {
			cache.put(key, lookup);
		}
		return lookup;
	}

	private Lookup fetchAndParse(URI uri) {
		Page page;
		try {
			page = fetcher.fetch(uri);
		} catch (TimeoutException e) {
			return new Lookup.NotRead("kv.ee didn't answer within 5 seconds");
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return new Lookup.NotRead("the lookup was interrupted");
		} catch (IOException e) {
			log.info("Listing lookup failed for {}: {}", uri, e.toString());
			return new Lookup.NotRead("kv.ee couldn't be reached");
		}

		String body = page.body() == null ? "" : page.body();
		if (page.status() == 403 || page.status() == 429 || page.status() == 503 || BOT_CHECK.matcher(body).find()) {
			return new Lookup.NotRead("kv.ee's bot check didn't let the request through");
		}
		if (page.status() != 200) {
			return new Lookup.NotRead("kv.ee answered with status " + page.status());
		}
		Map<PropertyKey, String> values = ListingParser.parse(body, uri.toString());
		if (values.isEmpty()) {
			return new Lookup.NotRead("the page has no listing details we can read");
		}
		return new Lookup.Found(uri.toString(), values);
	}

	/**
	 * The link as an https kv.ee listing address without query or fragment (so tracking
	 * parameters don't cause a second fetch), or null if it isn't one.
	 */
	static URI listingUri(String link) {
		if (link == null || link.isBlank()) {
			return null;
		}
		String text = link.trim();
		if (!text.contains("://")) {
			text = "https://" + text;
		}
		try {
			URI uri = new URI(text);
			String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
			String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
			boolean kvEe = host.equals("kv.ee") || host.endsWith(".kv.ee");
			if (!(scheme.equals("http") || scheme.equals("https")) || !kvEe || uri.getPort() != -1
					|| uri.getRawPath() == null || !LISTING_PATH.matcher(uri.getRawPath()).matches()) {
				return null;
			}
			return new URI("https", host, uri.getPath(), null);
		} catch (URISyntaxException e) {
			return null;
		}
	}

	private static PageFetcher httpFetcher() {
		HttpClient client = HttpClient.newBuilder()
				.connectTimeout(TIMEOUT)
				// One request: a redirect would be a second one, possibly somewhere else.
				.followRedirects(HttpClient.Redirect.NEVER)
				.build();
		return uri -> {
			HttpRequest request = HttpRequest.newBuilder(uri)
					.timeout(TIMEOUT)
					.header("User-Agent", USER_AGENT)
					.header("Accept", "text/html")
					.GET()
					.build();
			CompletableFuture<HttpResponse<String>> response =
					client.sendAsync(request, HttpResponse.BodyHandlers.ofString());
			try {
				// The whole download, not just the headers, has to fit in the limit.
				HttpResponse<String> done = response.get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
				return new Page(done.statusCode(), done.body());
			} catch (TimeoutException e) {
				response.cancel(true);
				throw e;
			} catch (ExecutionException e) {
				if (e.getCause() instanceof java.net.http.HttpTimeoutException timeout) {
					throw new TimeoutException(timeout.getMessage());
				}
				throw e.getCause() instanceof IOException io ? io : new IOException(e.getCause());
			}
		};
	}
}

package dev.logfilter;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.fabricmc.loader.api.FabricLoader;

public final class LogFilterManager {
	private static final Logger LOGGER = LogManager.getLogger("LogFilter");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final ModLogFilter FILTER = new ModLogFilter();
	private static final ConcurrentHashMap<String, LongAdder> BLOCKED = new ConcurrentHashMap<>();

	private static volatile boolean enabled = true;
	private static volatile Set<String> active = Set.of();
	private static volatile ModIndex index;

	private static FilterConfig config = new FilterConfig();
	private static Path file;
	private static boolean installed;
	private static Configuration attachedTo;

	private LogFilterManager() {}

	static synchronized void init() {
		if (installed) return;
		try {
			file = FabricLoader.getInstance().getConfigDir().resolve("logfilter.json");
			config = load();
			index = new ModIndex(config.manualLoggers);
			applyConfig();
			save();

			FILTER.start();
			LoggerContext ctx = LoggerContext.getContext(false);
			attach(ctx);
			// Re-attach if something reconfigures log4j later.
			ctx.addPropertyChangeListener(evt -> {
				if (LoggerContext.PROPERTY_CONFIG.equals(evt.getPropertyName())) attach(ctx);
			});
			installed = true;
			LOGGER.info("Log Filter active, hiding {} mod(s)", active.size());
		} catch (Throwable t) {
			LOGGER.error("Log Filter failed to install", t);
		}
	}

	private static synchronized void attach(LoggerContext ctx) {
		Configuration cfg = ctx.getConfiguration();
		if (cfg == attachedTo) return;
		attachedTo = cfg;
		LoggerConfig root = cfg.getRootLogger();
		root.addFilter(FILTER);
		ctx.updateLoggers();
	}

	static Filter.Result decide(String loggerName) {
		if (!enabled) return Filter.Result.NEUTRAL;
		Set<String> a = active;
		ModIndex idx = index;
		if (a.isEmpty() || idx == null) return Filter.Result.NEUTRAL;
		String owner = idx.ownerOf(loggerName);
		if (owner.isEmpty() || !a.contains(owner)) return Filter.Result.NEUTRAL;
		BLOCKED.computeIfAbsent(owner, k -> new LongAdder()).increment();
		return Filter.Result.DENY;
	}

	// ---- API for the screen --------------------------------------------------

	public static List<ModIndex.Entry> entries() {
		ModIndex idx = index;
		return idx == null ? List.of() : idx.entries();
	}

	public static boolean isEnabled() {
		return enabled;
	}

	public static synchronized void setEnabled(boolean value) {
		config.enabled = value;
		applyConfig();
		save();
	}

	public static boolean isFiltered(String modId) {
		return active.contains(modId);
	}

	public static synchronized void setFiltered(String modId, boolean value) {
		if (value) config.filteredMods.add(modId);
		else config.filteredMods.remove(modId);
		applyConfig();
		save();
	}

	public static long blockedCount(String modId) {
		LongAdder a = BLOCKED.get(modId);
		return a == null ? 0 : a.sum();
	}

	// ---- persistence ---------------------------------------------------------

	private static void applyConfig() {
		enabled = config.enabled;
		active = Set.copyOf(new HashSet<>(config.filteredMods));
	}

	private static FilterConfig load() {
		try {
			if (Files.exists(file)) {
				try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
					FilterConfig c = GSON.fromJson(r, FilterConfig.class);
					if (c != null) {
						c.sanitize();
						return c;
					}
				}
			}
		} catch (Exception e) {
			LOGGER.warn("Could not read {}, using defaults", file, e);
		}
		return new FilterConfig();
	}

	private static void save() {
		try {
			Files.writeString(file, GSON.toJson(config), StandardCharsets.UTF_8);
		} catch (Exception e) {
			LOGGER.warn("Could not save {}", file, e);
		}
	}
}

package dev.logfilter;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Serialized to config/logfilter.json */
final class FilterConfig {
	/** Master switch. */
	boolean enabled = true;
	/** Mod ids whose log lines are hidden. Edited from the in-game screen. */
	Set<String> filteredMods = new LinkedHashSet<>();
	/**
	 * Optional manual mapping: mod id -> logger names/prefixes that belong to it.
	 * Use when a mod's lines are not detected automatically.
	 * Example: { "somemod": ["Some Weird Logger"] }
	 */
	Map<String, List<String>> manualLoggers = new LinkedHashMap<>();

	void sanitize() {
		if (filteredMods == null) filteredMods = new LinkedHashSet<>();
		if (manualLoggers == null) manualLoggers = new LinkedHashMap<>();
	}
}

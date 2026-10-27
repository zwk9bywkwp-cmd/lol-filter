package dev.logfilter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.ModMetadata;

/** Maps a log4j logger name to the (top-level) mod that owns it. */
public final class ModIndex {
	public record Entry(String id, String name) {}

	private record Prefix(String prefix, String ownerId) {}

	private final List<Prefix> prefixes = new ArrayList<>();
	private final Map<String, String> nameKeys = new HashMap<>();
	private final List<Entry> entries = new ArrayList<>();
	private final Map<String, String> cache = new ConcurrentHashMap<>();

	ModIndex(Map<String, List<String>> manual) {
		for (ModContainer mod : FabricLoader.getInstance().getAllMods()) {
			ModMetadata md = mod.getMetadata();
			String id = md.getId();
			if (id.equals("java")) continue;

			ModContainer owner = mod;
			while (owner.getContainingMod().isPresent()) owner = owner.getContainingMod().get();
			String ownerId = owner.getMetadata().getId();

			if (owner == mod) entries.add(new Entry(id, md.getName()));
			addName(normalize(id), ownerId);
			addName(normalize(md.getName()), ownerId);
			for (String p : findPackages(mod)) prefixes.add(new Prefix(p, ownerId));
		}
		manual.forEach((modId, list) -> {
			if (list != null) for (String p : list) if (p != null && !p.isBlank()) prefixes.add(new Prefix(p, modId));
		});
		prefixes.sort(Comparator.comparingInt((Prefix p) -> p.prefix().length()).reversed());
		entries.sort(Comparator.comparing(e -> e.name().toLowerCase(Locale.ROOT)));
	}

	public List<Entry> entries() {
		return entries;
	}

	/** @return owning mod id, or "" if the logger is not attributable to a mod. */
	String ownerOf(String loggerName) {
		if (loggerName == null || loggerName.isEmpty()) return "";
		String cached = cache.get(loggerName);
		if (cached != null) return cached;
		String result = compute(loggerName);
		if (cache.size() > 5000) cache.clear();
		cache.put(loggerName, result);
		return result;
	}

	private String compute(String name) {
		for (Prefix p : prefixes) {
			if (matches(name, p.prefix())) return p.ownerId();
		}
		if (name.indexOf('.') < 0) {
			String n = normalize(name);
			if (n.length() >= 3) {
				String exact = nameKeys.get(n);
				if (exact != null) return exact;
				String best = null;
				int bestLen = 0;
				for (Map.Entry<String, String> e : nameKeys.entrySet()) {
					int len = e.getKey().length();
					if (len >= 5 && len > bestLen && n.startsWith(e.getKey())) {
						best = e.getValue();
						bestLen = len;
					}
				}
				if (best != null) return best;
			}
		}
		return "";
	}

	private static boolean matches(String name, String prefix) {
		if (!name.startsWith(prefix)) return false;
		return name.length() == prefix.length() || !Character.isLetterOrDigit(name.charAt(prefix.length()));
	}

	private void addName(String key, String ownerId) {
		if (key.length() >= 3) nameKeys.putIfAbsent(key, ownerId);
	}

	private static String normalize(String s) {
		StringBuilder sb = new StringBuilder();
		for (char c : s.toCharArray()) if (Character.isLetterOrDigit(c)) sb.append(Character.toLowerCase(c));
		return sb.toString();
	}

	// ---- package discovery -------------------------------------------------

	private static List<String> findPackages(ModContainer mod) {
		List<String> out = new ArrayList<>();
		try {
			for (Path root : mod.getRootPaths()) scan(root, "", 0, out);
		} catch (Throwable ignored) {
			// best effort
		}
		return out;
	}

	private static void scan(Path dir, String pkg, int depth, List<String> out) throws IOException {
		List<Path> subdirs = new ArrayList<>();
		boolean hasClasses = false;
		try (Stream<Path> s = Files.list(dir)) {
			for (Path p : s.toList()) {
				String n = fileName(p);
				if (Files.isDirectory(p)) {
					if (validDir(n, depth)) subdirs.add(p);
				} else if (n.endsWith(".class")) {
					hasClasses = true;
				}
			}
		}
		if (depth > 0 && hasClasses) {
			out.add(pkg);
			return;
		}
		if (subdirs.size() == 1) {
			Path only = subdirs.get(0);
			scan(only, join(pkg, fileName(only)), depth + 1, out);
		} else if (subdirs.size() > 1) {
			if (depth >= 2) {
				out.add(pkg);
			} else {
				for (Path sub : subdirs) scan(sub, join(pkg, fileName(sub)), depth + 1, out);
			}
		}
	}

	private static boolean validDir(String n, int depth) {
		if (!n.matches("[A-Za-z_][A-Za-z0-9_$]*")) return false;
		if (depth == 0 && (n.equals("assets") || n.equals("data"))) return false;
		return true;
	}

	private static String join(String pkg, String n) {
		return pkg.isEmpty() ? n : pkg + "." + n;
	}

	private static String fileName(Path p) {
		Path f = p.getFileName();
		if (f == null) return "";
		String s = f.toString();
		while (s.endsWith("/")) s = s.substring(0, s.length() - 1);
		return s;
	}
}

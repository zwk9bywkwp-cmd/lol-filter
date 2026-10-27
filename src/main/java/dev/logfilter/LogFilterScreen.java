package dev.logfilter;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Simple paged mod list. Deliberately overrides no render/input methods so it
 * survives GUI API changes between Minecraft versions.
 */
public class LogFilterScreen extends Screen {
	private static final int ROW_H = 22;
	private static final int ROW_W = 300;

	private final Screen parent;
	private final List<ModIndex.Entry> all = LogFilterManager.entries();
	private List<ModIndex.Entry> shown = new ArrayList<>();
	private final List<Button> rowButtons = new ArrayList<>();

	private String query = "";
	private int page = 0;
	private int rows = 5;

	private Button master;
	private Button prev;
	private Button next;
	private StringWidget pageLabel;

	public LogFilterScreen(Screen parent) {
		super(Component.literal("Log Filter"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		rowButtons.clear();
		int cx = this.width / 2;

		addRenderableWidget(new StringWidget(cx - 150, 8, 300, 9, getTitle(), this.font).alignCenter());
		addRenderableWidget(new StringWidget(cx - 150, 20, 300, 9,
				Component.literal("[X] = this mod's log lines are hidden"), this.font).alignCenter());

		master = Button.builder(masterLabel(), b -> {
			LogFilterManager.setEnabled(!LogFilterManager.isEnabled());
			b.setMessage(masterLabel());
		}).bounds(cx - 150, 34, ROW_W, 20).build();
		addRenderableWidget(master);

		EditBox search = new EditBox(this.font, cx - 150, 58, ROW_W, 20, Component.literal("Search"));
		search.setHint(Component.literal("Search mods..."));
		search.setValue(query);
		search.setResponder(s -> {
			query = s;
			page = 0;
			applyQuery();
			refresh();
		});
		addRenderableWidget(search);

		int top = 84;
		int bottom = this.height - 58;
		rows = Math.max(1, (bottom - top) / ROW_H);
		for (int i = 0; i < rows; i++) {
			final int idx = i;
			Button b = Button.builder(Component.empty(), btn -> onRow(idx))
					.bounds(cx - 150, top + i * ROW_H, ROW_W, 20).build();
			rowButtons.add(b);
			addRenderableWidget(b);
		}

		prev = Button.builder(Component.literal("< Prev"), b -> {
			page--;
			refresh();
		}).bounds(cx - 150, this.height - 54, 80, 20).build();
		next = Button.builder(Component.literal("Next >"), b -> {
			page++;
			refresh();
		}).bounds(cx + 70, this.height - 54, 80, 20).build();
		pageLabel = new StringWidget(cx - 60, this.height - 49, 120, 9, Component.empty(), this.font).alignCenter();
		addRenderableWidget(prev);
		addRenderableWidget(next);
		addRenderableWidget(pageLabel);

		addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
				.bounds(cx - 100, this.height - 28, 200, 20).build());

		applyQuery();
		refresh();
	}

	private Component masterLabel() {
		return Component.literal("Filtering: " + (LogFilterManager.isEnabled() ? "ON" : "OFF"));
	}

	private void applyQuery() {
		String q = query.toLowerCase(Locale.ROOT).trim();
		shown = new ArrayList<>();
		for (ModIndex.Entry e : all) {
			if (q.isEmpty() || e.name().toLowerCase(Locale.ROOT).contains(q) || e.id().toLowerCase(Locale.ROOT).contains(q)) {
				shown.add(e);
			}
		}
	}

	private void onRow(int i) {
		int idx = page * rows + i;
		if (idx < 0 || idx >= shown.size()) return;
		ModIndex.Entry e = shown.get(idx);
		LogFilterManager.setFiltered(e.id(), !LogFilterManager.isFiltered(e.id()));
		refresh();
	}

	private void refresh() {
		int pages = Math.max(1, (shown.size() + rows - 1) / rows);
		page = Math.max(0, Math.min(page, pages - 1));
		for (int i = 0; i < rowButtons.size(); i++) {
			Button b = rowButtons.get(i);
			int idx = page * rows + i;
			if (idx < shown.size()) {
				ModIndex.Entry e = shown.get(idx);
				boolean on = LogFilterManager.isFiltered(e.id());
				long n = LogFilterManager.blockedCount(e.id());
				String text = (on ? "[X] " : "[   ] ") + e.name() + (n > 0 ? "  (" + n + " hidden)" : "");
				b.setMessage(Component.literal(text));
				b.visible = true;
			} else {
				b.visible = false;
			}
		}
		prev.active = page > 0;
		next.active = page < pages - 1;
		pageLabel.setMessage(Component.literal("Page " + (page + 1) + " / " + pages));
	}

	@Override
	public void onClose() {
		Minecraft.getInstance().setScreen(parent);
	}
}

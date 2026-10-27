package dev.logfilter;

import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;

/** Runs before Minecraft starts so the filter is active as early as possible. */
public class LogFilterMod implements PreLaunchEntrypoint {
	@Override
	public void onPreLaunch() {
		LogFilterManager.init();
	}
}

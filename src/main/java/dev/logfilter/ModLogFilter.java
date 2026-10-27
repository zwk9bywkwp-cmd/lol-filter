package dev.logfilter;

import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.filter.AbstractFilter;

/** Log4j filter attached to the root logger config; DENY drops the line from every appender. */
final class ModLogFilter extends AbstractFilter {
	@Override
	public Result filter(LogEvent event) {
		return LogFilterManager.decide(event.getLoggerName());
	}
}

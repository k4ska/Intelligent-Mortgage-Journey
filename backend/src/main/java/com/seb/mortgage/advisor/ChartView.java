package com.seb.mortgage.advisor;

/**
 * A chart as the chat shows it: the {@code chart} event's data and a saved message's chart
 * (#130). Saved as JSON with the message, so a reload shows it again.
 *
 * @param chartType which chart it is, e.g. {@link RateChart#TYPE} or {@link CostBreakdown#TYPE}
 * @param data      the chart's figures, all computed in code
 */
public record ChartView(String chartType, String title, Object data) {}

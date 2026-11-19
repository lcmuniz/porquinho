package com.porquinho.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Story 10.5: the Alertas tab. The 100 most recent alerts and how many are open (not resolved by an admin). The labels
 * are the texts of the e-mail and Slack, so the three say the same thing. Aggregates and the bank only.
 */
public record AdminAlertsResponse(long openCount, boolean emailConfigured, boolean slackConfigured, List<Alert> alerts) {

    /** {@code tab} is the /admin tab of the metric and {@code link} the full URL to it. */
    public record Alert(long id, String key, String type, String metric, String bank, String bankLabel,
                        BigDecimal value, BigDecimal threshold, String valueLabel, String thresholdLabel,
                        String action, String tab, String link, OffsetDateTime triggeredAt,
                        OffsetDateTime normalizedAt, OffsetDateTime resolvedAt) {
    }
}

package com.porquinho.service;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/** Layer 3 cache field (Story 7.7); the list and the CSV are in {@link DashboardDetailsIntegrationTest}. */
class DashboardDetailsServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-12-01T01:00:00Z"), ZoneOffset.UTC);

    private final DashboardDetailsService service = new DashboardDetailsService(null, null, null, null, null, null, null, CLOCK);

    // Story 7.7: one cache entry per filter, page and month
    @Test
    void theLayer3CacheFieldChangesWithEveryFilterPartThePageAndTheMonth() {
        UUID id = UUID.randomUUID();
        DashboardDetailsService.DetailsFilter base = new DashboardDetailsService.DetailsFilter(null, null, List.of(id), false,
            List.of(), List.of(), "feira", null);
        String hash = service.filtersHash(base, 0);

        assertEquals(hash, service.filtersHash(new DashboardDetailsService.DetailsFilter(null, null, List.of(id), false,
            List.of(), List.of(), "feira", null), 0));
        assertNotEquals(hash, service.filtersHash(base, 1));
        assertNotEquals(hash, service.filtersHash(new DashboardDetailsService.DetailsFilter(null, null, List.of(id), false,
            List.of(), List.of(), "mercado", null), 0));
        assertNotEquals(hash, service.filtersHash(new DashboardDetailsService.DetailsFilter(null, null, List.of(id), false,
            List.of(), List.of(), "feira", "amount,asc"), 0));
        assertNotEquals(hash, service.filtersHash(new DashboardDetailsService.DetailsFilter(null, null, List.of(), false,
            List.of(id), List.of(), "feira", null), 0));
        // free text cannot pass for the next field
        assertNotEquals(service.filtersHash(new DashboardDetailsService.DetailsFilter(null, null, List.of(), false, List.of(),
            List.of(), "a5:null", "x"), 0), service.filtersHash(new DashboardDetailsService.DetailsFilter(null, null,
            List.of(), false, List.of(), List.of(), "a", "null5:x"), 0));
        // the literal text "null" is not an absent q
        assertNotEquals(service.filtersHash(new DashboardDetailsService.DetailsFilter(null, null, List.of(), false, List.of(),
            List.of(), null, null), 0), service.filtersHash(new DashboardDetailsService.DetailsFilter(null, null, List.of(),
            false, List.of(), List.of(), "null", null), 0));
        DashboardDetailsService nextMonth = new DashboardDetailsService(null, null, null, null, null, null, null,
            Clock.offset(CLOCK, java.time.Duration.ofDays(1)));
        assertNotEquals(hash, nextMonth.filtersHash(base, 0));
    }
}

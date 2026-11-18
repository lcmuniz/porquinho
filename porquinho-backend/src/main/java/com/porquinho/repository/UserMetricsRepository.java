package com.porquinho.repository;

import com.porquinho.entity.User;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Story 10.4: aggregates of the users tab of the admin panel. The base is every user with {@code deleted_at IS NULL}
 * (admins included); nothing here returns a user id, e-mail or name. Active in N days: a session with
 * {@code created_at <= t} and {@code last_activity >= t - N} (the session dies after 7 idle days, so the interval
 * [created_at, last_activity] is the activity). Windows in hours, so a day-light saving change never moves them.
 * No index for these: they scan, fine at the current volume.
 */
public interface UserMetricsRepository extends Repository<User, UUID> {

    String BASE = "WITH base AS (SELECT id FROM users WHERE deleted_at IS NULL) ";

    String NOW = "CAST(:now AS timestamptz)";
    /** Distinct users of the base with a session alive in [t - window, t]: append "t AND s.last_activity >= t - window)". */
    String SESSIONS = "(SELECT count(DISTINCT s.user_id) FROM user_sessions s JOIN base b ON b.id = s.user_id "
        + "WHERE s.created_at <= ";
    /**
     * A trial that really ran and is over. A returning e-mail without a right to a trial gets a TRIAL EXPIRED row with
     * trial_start_date = trial_end_date (SubscriptionService.startTrial): it never had a trial, so it is out.
     */
    String TRIAL_ENDED = "s.trial_end_date <= " + NOW + " AND s.trial_end_date > s.trial_start_date";
    String SUBS = "(SELECT count(*) FROM subscriptions s JOIN base b ON b.id = s.user_id WHERE ";

    interface Summary {
        long getTotal();
        long getActive24h();
        long getActive7d();
        long getActive30d();
        /** TRIAL, ACTIVE and trial_end_date still ahead. */
        long getTrials();
        /** MONTHLY and ACTIVE. */
        long getPaid();
        /** MONTHLY, any status. */
        long getMonthly();
        /** MONTHLY and CANCELLED. */
        long getCancelled();
        /** TRIAL with trial_end_date already past (and after trial_start_date), any status. */
        long getEndedTrials();
        long getOnboarded();
        /**
         * Distinct (user, São Paulo week) pairs with a COMPLETED import (by updated_at) in [weeksFrom, weeksTo), of
         * the users active in 30 days.
         */
        long getCheckins();
    }

    @Query(value = BASE + "SELECT (SELECT count(*) FROM base) AS \"total\", "
        + SESSIONS + NOW + " AND s.last_activity >= " + NOW + " - interval '24 hours')" + " AS \"active24h\", "
        + SESSIONS + NOW + " AND s.last_activity >= " + NOW + " - interval '168 hours')" + " AS \"active7d\", "
        + SESSIONS + NOW + " AND s.last_activity >= " + NOW + " - interval '720 hours')" + " AS \"active30d\", "
        + SUBS + "s.plan_type = 'TRIAL' AND s.status = 'ACTIVE' AND s.trial_end_date > " + NOW + ") AS \"trials\", "
        + SUBS + "s.plan_type = 'MONTHLY' AND s.status = 'ACTIVE') AS \"paid\", "
        + SUBS + "s.plan_type = 'MONTHLY') AS \"monthly\", "
        + SUBS + "s.plan_type = 'MONTHLY' AND s.status = 'CANCELLED') AS \"cancelled\", "
        + SUBS + "s.plan_type = 'TRIAL' AND " + TRIAL_ENDED + ") AS \"endedTrials\", "
        + "(SELECT count(*) FROM onboarding_progress o JOIN base b ON b.id = o.user_id WHERE o.is_completed) "
        + "AS \"onboarded\", "
        + "(SELECT count(*) FROM (SELECT DISTINCT i.user_id, "
        + "date_trunc('week', i.updated_at AT TIME ZONE 'America/Sao_Paulo') "
        + "FROM transaction_imports i JOIN base b ON b.id = i.user_id WHERE i.status = 'COMPLETED' "
        // only users active in 30 days, the denominator of the average: an import 31-34 days ago would inflate it
        + "AND EXISTS (SELECT 1 FROM user_sessions us WHERE us.user_id = i.user_id AND us.created_at <= " + NOW
        + " AND us.last_activity >= " + NOW + " - interval '720 hours') "
        + "AND i.updated_at >= :weeksFrom AND i.updated_at < :weeksTo) w) AS \"checkins\"",
        nativeQuery = true)
    Summary summary(@Param("now") OffsetDateTime now, @Param("weeksFrom") OffsetDateTime weeksFrom,
                    @Param("weeksTo") OffsetDateTime weeksTo);

    interface Adoption {
        long getAccounts();
        long getCreditCards();
        long getImports();
        long getBudgets();
        long getGoals();
        long getTransfers();
        long getReconciliations();
    }

    @Query(value = BASE + "SELECT "
        + "count(*) FILTER (WHERE EXISTS (SELECT 1 FROM accounts x WHERE x.user_id = b.id AND x.deleted_at IS NULL)) "
        + "AS \"accounts\", "
        + "count(*) FILTER (WHERE EXISTS (SELECT 1 FROM credit_cards x WHERE x.user_id = b.id "
        + "AND x.deleted_at IS NULL)) AS \"creditCards\", "
        + "count(*) FILTER (WHERE EXISTS (SELECT 1 FROM transaction_imports x WHERE x.user_id = b.id "
        + "AND x.status = 'COMPLETED')) AS \"imports\", "
        + "count(*) FILTER (WHERE EXISTS (SELECT 1 FROM budget_allocations x WHERE x.user_id = b.id)) AS \"budgets\", "
        + "count(*) FILTER (WHERE EXISTS (SELECT 1 FROM goals x WHERE x.user_id = b.id AND x.deleted_at IS NULL)) "
        + "AS \"goals\", "
        + "count(*) FILTER (WHERE EXISTS (SELECT 1 FROM transfers x WHERE x.user_id = b.id AND x.deleted_at IS NULL)) "
        + "AS \"transfers\", "
        + "count(*) FILTER (WHERE EXISTS (SELECT 1 FROM reconciliation_history x WHERE x.user_id = b.id)) "
        + "AS \"reconciliations\" FROM base b", nativeQuery = true)
    Adoption adoption();

    interface MonthCounts {
        /** YYYY-MM of users.created_at in America/Sao_Paulo. */
        String getMonth();
        long getUsers();
        /** Users of the month with a MONTHLY subscription, any status. */
        long getPaid();
        /** MONTHLY plus TRIAL that ran and is over: the denominator of the conversion. */
        long getEligible();
    }

    // users.created_at is a timestamp without time zone written with the JVM clock (LocalDateTime.now()); the cast
    // reads it in the session time zone, which the JDBC driver sets to the JVM one
    @Query(value = "SELECT to_char(CAST(u.created_at AS timestamptz) AT TIME ZONE 'America/Sao_Paulo', 'YYYY-MM') "
        + "AS \"month\", count(*) AS \"users\", count(*) FILTER (WHERE s.plan_type = 'MONTHLY') AS \"paid\", "
        + "count(*) FILTER (WHERE s.plan_type = 'MONTHLY' OR (s.plan_type = 'TRIAL' AND " + TRIAL_ENDED + ")) "
        + "AS \"eligible\" "
        + "FROM users u LEFT JOIN subscriptions s ON s.user_id = u.id WHERE u.deleted_at IS NULL GROUP BY 1",
        nativeQuery = true)
    List<MonthCounts> countByMonth(@Param("now") OffsetDateTime now);

    interface WeekCounts {
        /** 0 for the oldest of the 12 weeks. */
        int getWeek();
        long getActive7d();
        long getActive30d();
    }

    /** Active users at the end of each of 12 weeks: {@code firstEnd} + i × 7 days, the last one capped at now. */
    @Query(value = BASE + "SELECT w.i AS \"week\", " + SESSIONS + "w.t AND s.last_activity >= w.t - interval '168 hours')" + " AS \"active7d\", "
        + SESSIONS + "w.t AND s.last_activity >= w.t - interval '720 hours')" + " AS \"active30d\" "
        + "FROM (SELECT i, LEAST(CAST(:firstEnd AS timestamptz) + i * interval '168 hours', " + NOW + ") AS t "
        + "FROM generate_series(0, 11) AS i) w ORDER BY w.i", nativeQuery = true)
    List<WeekCounts> activeByWeek(@Param("firstEnd") OffsetDateTime firstEnd, @Param("now") OffsetDateTime now);
}

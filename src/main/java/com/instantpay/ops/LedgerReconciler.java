// com.instantpay.ops.LedgerReconciler
package com.instantpay.ops;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Component
public class LedgerReconciler {
    private static final Logger log = LoggerFactory.getLogger(LedgerReconciler.class);

    private final JdbcTemplate jdbc;

    public LedgerReconciler(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // Run daily at 02:05 (server time). Only one node will execute (ShedLock).
    @Scheduled(cron = "0 5 2 * * *")
    @SchedulerLock(name = "reconciliation-nightly", lockAtLeastFor = "PT1M", lockAtMostFor = "PT15M")
    @Transactional(readOnly = true)
    public void nightlyRecon() {
        checkCompletedPaymentsHaveTwoTx();
        checkFailedPaymentsHaveNoTx();
        checkOutboxNotStuck();
    }

    // COMPLETED => exactly 2 entries (DEBIT + CREDIT) with equal amounts
    void checkCompletedPaymentsHaveTwoTx() {
        String sql = """
      SELECT p.id
      FROM payments p
      LEFT JOIN transactions t ON t.payment_id = p.id
      WHERE p.status = 'COMPLETED'
      GROUP BY p.id
      HAVING COUNT(t.*) <> 2
         OR MIN(t.type) = MAX(t.type) -- both DEBIT or both CREDIT
         OR ABS(SUM(CASE WHEN t.type = 'DEBIT' THEN t.amount ELSE -t.amount END)) <> 0
    """;
        List<UUID> bad = jdbc.query(sql, (rs, i) -> (UUID) rs.getObject(1));
        if (!bad.isEmpty()) {
            log.error("RECON FAIL: completed payments with invalid transaction pairs: {}", bad);
        } else {
            log.info("Recon ✓ COMPLETED payments: OK");
        }
    }

    // FAILED => 0 ledger entries
    void checkFailedPaymentsHaveNoTx() {
        String sql = """
      SELECT p.id
      FROM payments p
      JOIN transactions t ON t.payment_id = p.id
      WHERE p.status = 'FAILED'
      GROUP BY p.id
    """;
        List<UUID> viol = jdbc.query(sql, (rs, i) -> (UUID) rs.getObject(1));
        if (!viol.isEmpty()) {
            log.error("RECON FAIL: FAILED payments with ledger entries present: {}", viol);
        } else {
            log.info("Recon ✓ FAILED payments: OK");
        }
    }

    // Modulith outbox (event_publication) shouldn't have old, incomplete rows
    void checkOutboxNotStuck() {
        String sql = """
      SELECT COUNT(*)
      FROM event_publication
      WHERE completion_date IS NULL
        AND publication_date < (now() - INTERVAL '15 minutes')
    """;
        Long stuck = jdbc.queryForObject(sql, Long.class);
        if (stuck != null && stuck > 0) {
            log.warn("RECON WARN: {} event publications stuck > 15 minutes", stuck);
        } else {
            log.info("Recon ✓ Event publication backlog: OK");
        }
    }
}

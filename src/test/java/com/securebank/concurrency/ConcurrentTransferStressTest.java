package com.securebank.concurrency;

import com.securebank.domain.Account;
import com.securebank.domain.Role;
import com.securebank.domain.User;
import com.securebank.dto.TransferRequest;
import com.securebank.exception.InsufficientFundsException;
import com.securebank.repository.AccountRepository;
import com.securebank.repository.AuditLogRepository;
import com.securebank.repository.TransactionRepository;
import com.securebank.repository.UserRepository;
import com.securebank.service.TransferService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Hammers {@link TransferService} with many concurrent, randomized transfers and asserts the
 * non-negotiable banking invariants under contention:
 *
 * <ul>
 *   <li><b>Conservation</b> — the sum of all balances never changes.</li>
 *   <li><b>No oversell</b> — no balance ever goes negative; you can't spend money twice.</li>
 *   <li><b>Ledger consistency</b> — exactly one transaction + one audit row per completed transfer.</li>
 *   <li><b>No corruption</b> — no deadlocks, lost updates, or unexpected exceptions.</li>
 * </ul>
 *
 * Runs on in-memory H2 (PostgreSQL mode, {@code SELECT ... FOR UPDATE}) so it executes anywhere.
 */
@SpringBootTest
@ActiveProfiles("test")
class ConcurrentTransferStressTest {

    private static final Logger log = LoggerFactory.getLogger(ConcurrentTransferStressTest.class);

    @Autowired
    private TransferService transferService;
    @Autowired
    private AccountRepository accountRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private TransactionRepository transactionRepository;
    @Autowired
    private AuditLogRepository auditLogRepository;

    private User owner;

    @BeforeEach
    void clean() {
        transactionRepository.deleteAll();
        auditLogRepository.deleteAll();
        accountRepository.deleteAll();
        userRepository.deleteAll();
        owner = userRepository.save(new User("stress", "stress@example.com", "hash", Role.USER));
    }

    @Test
    @DisplayName("1000 concurrent randomized transfers conserve money and corrupt nothing")
    void thousandConcurrentRandomTransfers() throws Exception {
        int accounts = 10;
        BigDecimal perAccount = new BigDecimal("1000.00");
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < accounts; i++) {
            Account a = new Account("SB-STRESS-" + i, owner, "USD");
            a.setBalance(perAccount);
            ids.add(accountRepository.save(a).getId());
        }
        BigDecimal expectedTotal = perAccount.multiply(BigDecimal.valueOf(accounts)); // 10,000.00

        int tasks = 1000;
        AtomicInteger ok = new AtomicInteger();
        AtomicInteger insufficient = new AtomicInteger();
        AtomicInteger contended = new AtomicInteger();
        ConcurrentLinkedQueue<Throwable> unexpected = new ConcurrentLinkedQueue<>();
        Map<String, Integer> amountVariations = new ConcurrentHashMap<>();

        ExecutorService pool = Executors.newFixedThreadPool(32);
        CountDownLatch ready = new CountDownLatch(tasks);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();

        for (int t = 0; t < tasks; t++) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                try {
                    go.await();
                    ThreadLocalRandom rnd = ThreadLocalRandom.current();
                    int from = rnd.nextInt(accounts);
                    int to;
                    do {
                        to = rnd.nextInt(accounts);
                    } while (to == from);
                    // 5,000 distinct possible amounts: 0.01 .. 50.00 -> the "output variations"
                    BigDecimal amount = BigDecimal.valueOf(rnd.nextInt(1, 5001), 2);
                    amountVariations.merge(amount.toPlainString(), 1, Integer::sum);

                    transferService.transfer(owner.getId(), owner.getUsername(),
                            new TransferRequest(ids.get(from), ids.get(to), amount, "stress"));
                    ok.incrementAndGet();
                } catch (InsufficientFundsException e) {
                    insufficient.incrementAndGet();
                } catch (ConcurrencyFailureException e) {
                    // a transfer that lost a lock race and rolled back cleanly; money still conserved
                    contended.incrementAndGet();
                } catch (Throwable e) {
                    unexpected.add(e);
                }
                return null;
            }));
        }

        ready.await(30, TimeUnit.SECONDS);
        long startNanos = System.nanoTime();
        go.countDown(); // release all tasks as simultaneously as possible
        for (Future<?> f : futures) {
            f.get(120, TimeUnit.SECONDS);
        }
        pool.shutdown();
        long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000;

        // --- read the final committed state ---
        BigDecimal finalTotal = accountRepository.findAll().stream()
                .map(Account::getBalance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        boolean anyNegative = accountRepository.findAll().stream()
                .anyMatch(a -> a.getBalance().signum() < 0);

        log.info("Stress result: ok={} insufficient={} contended={} unexpected={} distinctAmounts={} in {}ms",
                ok.get(), insufficient.get(), contended.get(), unexpected.size(),
                amountVariations.size(), elapsedMs);

        // --- invariants ---
        assertThat(unexpected)
                .as("no deadlocks, lost updates, or unexpected exceptions: %s", describe(unexpected))
                .isEmpty();
        assertThat(finalTotal)
                .as("money is conserved across all accounts")
                .isEqualByComparingTo(expectedTotal);
        assertThat(anyNegative).as("no account went negative").isFalse();
        assertThat(ok.get() + insufficient.get() + contended.get())
                .as("every task produced a well-defined outcome").isEqualTo(tasks);
        assertThat(transactionRepository.count())
                .as("one ledger row per completed transfer").isEqualTo(ok.get());
        assertThat(auditLogRepository.count())
                .as("one audit row per completed transfer").isEqualTo(ok.get());
    }

    @Test
    @DisplayName("concurrent over-draw of one account never spends the same money twice")
    void concurrentDrainNeverOverdraws() throws Exception {
        Account source = new Account("SB-SRC", owner, "USD");
        source.setBalance(new BigDecimal("100.00"));
        Long sourceId = accountRepository.save(source).getId();
        Account dest = new Account("SB-DST", owner, "USD");
        dest.setBalance(BigDecimal.ZERO);
        Long destId = accountRepository.save(dest).getId();

        int tasks = 200; // twice as many $1 transfers as there are dollars
        AtomicInteger ok = new AtomicInteger();
        AtomicInteger insufficient = new AtomicInteger();
        ConcurrentLinkedQueue<Throwable> unexpected = new ConcurrentLinkedQueue<>();

        ExecutorService pool = Executors.newFixedThreadPool(16);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        for (int t = 0; t < tasks; t++) {
            futures.add(pool.submit(() -> {
                try {
                    go.await();
                    transferService.transfer(owner.getId(), owner.getUsername(),
                            new TransferRequest(sourceId, destId, new BigDecimal("1.00"), "drain"));
                    ok.incrementAndGet();
                } catch (InsufficientFundsException e) {
                    insufficient.incrementAndGet();
                } catch (Throwable e) {
                    unexpected.add(e);
                }
                return null;
            }));
        }
        go.countDown();
        for (Future<?> f : futures) {
            f.get(120, TimeUnit.SECONDS);
        }
        pool.shutdown();

        BigDecimal sourceFinal = accountRepository.findById(sourceId).orElseThrow().getBalance();
        BigDecimal destFinal = accountRepository.findById(destId).orElseThrow().getBalance();

        assertThat(unexpected).as("no unexpected exceptions: %s", describe(unexpected)).isEmpty();
        assertThat(ok.get()).as("exactly 100 of the 200 $1 transfers succeed").isEqualTo(100);
        assertThat(insufficient.get()).isEqualTo(100);
        assertThat(sourceFinal).as("source fully drained, never negative").isEqualByComparingTo("0.00");
        assertThat(destFinal).as("destination received exactly $100").isEqualByComparingTo("100.00");
        assertThat(sourceFinal.add(destFinal)).isEqualByComparingTo("100.00");
    }

    private static String describe(ConcurrentLinkedQueue<Throwable> errors) {
        if (errors.isEmpty()) {
            return "none";
        }
        Throwable first = errors.peek();
        return errors.size() + " error(s), first = " + first.getClass().getName() + ": " + first.getMessage();
    }
}

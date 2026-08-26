package kn.jdb.config;

import com.zaxxer.hikari.HikariDataSource;
import org.crac.Context;
import org.crac.Core;
import org.crac.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

@Component
@Profile("lambda")
public class LambdaSnapStartHooks implements Resource {

    private static final Logger log = LoggerFactory.getLogger(LambdaSnapStartHooks.class);
    private final DataSource dataSource;

    public LambdaSnapStartHooks(DataSource dataSource) {
        this.dataSource = dataSource;
        // Register this class as a CRaC resource so Lambda knows to trigger it
        Core.getGlobalContext().register(this);
    }

    @Override
    public void beforeCheckpoint(Context<? extends Resource> context) throws Exception {
        // Implement any logic needed before a snapshot is taken
        log.info("beforeCheckpoint: ...");
        refreshDataSource(dataSource);
    }

    @Override
    public void afterRestore(Context<? extends Resource> context) throws Exception {
        // Implement any logic needed after a snapshot is restored
        log.info("afterRestore: ...");
        refreshDataSource(dataSource);
    }

    /**
     * ## Might not be needed if HikariDataSource is configured with allow-pool-suspension=true
     * +
     * 2026-08-26T10:52:39.949 INFO 2 --- [db-dora-jsb] [           main] o.s.web.servlet.DispatcherServlet        : Completed initialization in 1 ms
     * 2026-08-26T10:52:39.953 INFO 2 --- [db-dora-jsb] [           main] o.s.b.j.HikariCheckpointRestoreLifecycle : Suspending Hikari pool
     * 2026-08-26T10:52:39.953 INFO 2 --- [db-dora-jsb] [           main] o.s.b.j.HikariCheckpointRestoreLifecycle : Evicting Hikari connections
     * 2026-08-26T10:52:39.955 INFO 2 --- [db-dora-jsb] [           main] kn.jdb.config.LambdaSnapStartHooks       : beforeCheckpoint: ...
     * 2026-08-26T10:52:40.072 INIT_REPORT Init Duration: 13316.04 ms
     * 2026-08-26T10:55:30.960 RESTORE_START Runtime Version: java:21.mainline.v82	Runtime Version ARN: arn:aws:lambda:ap-southeast-2::runtime:1a7a66b2ea919f8f9034670f834e0512201a5998cd8767785a8d17f2ca7e204e
     * 2026-08-26T10:55:31.843 INFO 2 --- [db-dora-jsb] [           main] kn.jdb.config.LambdaSnapStartHooks       : afterRestore: ...
     * 2026-08-26T10:55:31.848 INFO 2 --- [db-dora-jsb] [           main] o.s.c.support.DefaultLifecycleProcessor  : Restarting Spring-managed lifecycle beans after JVM restore
     * 2026-08-26T10:55:31.877 INFO 2 --- [db-dora-jsb] [           main] o.s.b.j.HikariCheckpointRestoreLifecycle : Resuming Hikari pool
     * 2026-08-26T10:55:32.253 INFO 2 --- [db-dora-jsb] [           main] o.s.c.support.DefaultLifecycleProcessor  : Spring-managed lifecycle restart completed (restored JVM running for -1 ms)
     * 2026-08-26T10:55:32.261 RESTORE_REPORT Restore Duration: 1326.77 ms
     * 2026-08-26T10:55:32.266 START RequestId: 88a9b9a0-6a94-44a1-9942-4fd6ab25d598 Version: 40
     * +
     * Because HikariDataSource itself doesn’t expose “refresh all current connections” directly, and the pool management API does.
     * getHikariPoolMXBean() gives you access to Hikari’s runtime pool controls, especially:
     * •
     * softEvictConnections() — mark existing pooled connections for replacement
     * •
     * pool metrics/state methods
     * That’s useful for SnapStart because connections created before checkpoint may be invalid after restore. Calling softEvictConnections() tells Hikari to retire those old connections and open fresh ones on next use, without tearing down the whole datasource bean.
     * A few details:
     * •
     * It’s better than closing the whole DataSource, because the bean and config stay intact.
     * •
     * It’s “soft” eviction, so in-use connections aren’t violently killed.
     * •
     * getHikariPoolMXBean() can be null if the pool hasn’t initialised yet, so the null check is there to avoid blowing up early.
     * So the point of getHikariPoolMXBean() is: reach Hikari’s safe runtime refresh mechanism for stale pooled connections after SnapStart restore.
     */
    private static void refreshDataSource(DataSource dataSource) {
        if (dataSource instanceof HikariDataSource hikari && hikari.isRunning()) {
            if (hikari.getHikariPoolMXBean() != null) {
                log.info("HikariDataSource: softEvictConnections");
                hikari.getHikariPoolMXBean().softEvictConnections();
            }
        }
    }
}

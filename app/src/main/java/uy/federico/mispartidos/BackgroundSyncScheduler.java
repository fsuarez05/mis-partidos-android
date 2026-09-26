package uy.federico.mispartidos;

import android.content.Context;
import androidx.work.Constraints;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import java.util.Calendar;
import java.util.concurrent.TimeUnit;

public class BackgroundSyncScheduler {
    private static final String WORK_NAME = "mis_partidos_daily_sync";
    private static final long RETRY_HOURS = 1;

    static void schedule(Context context) {
        AppStore store = new AppStore(context);
        if (store.dailySyncCompletedToday()) {
            scheduleNextDay(context);
            return;
        }
        long now = System.currentTimeMillis();
        long firstAttempt = todayAt0005();
        if (now >= firstAttempt) enqueue(context, 0, ExistingWorkPolicy.KEEP);
        else enqueue(context, firstAttempt - now, ExistingWorkPolicy.REPLACE);
    }

    static void scheduleRetry(Context context) {
        if (new AppStore(context).dailySyncCompletedToday()) {
            scheduleNextDay(context);
            return;
        }
        enqueue(context, TimeUnit.HOURS.toMillis(RETRY_HOURS), ExistingWorkPolicy.REPLACE);
    }

    static void markCompleted(Context context) {
        AppStore store = new AppStore(context);
        store.markDailySyncCompleted();
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME);
        scheduleNextDay(context);
    }

    private static void scheduleNextDay(Context context) {
        long delay = next0005() - System.currentTimeMillis();
        enqueue(context, Math.max(0, delay), ExistingWorkPolicy.REPLACE);
    }

    private static void enqueue(Context context, long delayMs, ExistingWorkPolicy policy) {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(BackgroundSyncWorker.class)
                .setInitialDelay(Math.max(0, delayMs), TimeUnit.MILLISECONDS)
                .setConstraints(constraints)
                .build();
        WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, policy, request);
        new AppStore(context).saveNextBackgroundSync(System.currentTimeMillis() + Math.max(0, delayMs));
    }

    private static long todayAt0005() {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 5); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    private static long next0005() {
        Calendar c = Calendar.getInstance();
        c.add(Calendar.DAY_OF_YEAR, 1);
        c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 5); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }
}

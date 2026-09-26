package uy.federico.mispartidos;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class BackgroundSyncWorker extends Worker {
    public BackgroundSyncWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull @Override public Result doWork() {
        Context context = getApplicationContext();
        AppStore store = new AppStore(context);
        if (store.dailySyncCompletedToday()) {
            BackgroundSyncScheduler.schedule(context);
            return Result.success();
        }
        if (!ApiClient.configured(context)) {
            BackgroundSyncScheduler.scheduleRetry(context);
            return Result.success();
        }
        CountDownLatch finished = new CountDownLatch(1);
        AtomicBoolean success = new AtomicBoolean(false);
        ApiClient.sync(context, true, (ok, message) -> {
            success.set(ok);
            if (ok) {
                AlarmScheduler.scheduleAll(context);
                DailySummaryScheduler.schedule(context);
                MatchWidgetProvider.updateAll(context);
            }
            finished.countDown();
        });
        try {
            if (!finished.await(3, TimeUnit.MINUTES)) {
                BackgroundSyncScheduler.scheduleRetry(context);
                return Result.success();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            BackgroundSyncScheduler.scheduleRetry(context);
            return Result.success();
        }
        if (success.get()) BackgroundSyncScheduler.markCompleted(context);
        else BackgroundSyncScheduler.scheduleRetry(context);
        return Result.success();
    }
}

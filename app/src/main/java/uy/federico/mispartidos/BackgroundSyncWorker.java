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
        if (!ApiClient.configured(context)) {
            BackgroundSyncScheduler.markCompleted(context);
            return Result.success();
        }
        CountDownLatch finished = new CountDownLatch(1);
        AtomicBoolean success = new AtomicBoolean(false);
        ApiClient.sync(context, true, (ok, message) -> {
            success.set(ok);
            if (ok) {AlarmScheduler.scheduleAll(context);DailySummaryScheduler.schedule(context);MatchWidgetProvider.updateAll(context);}
            finished.countDown();
        });
        try {
            if (!finished.await(3, TimeUnit.MINUTES)) return Result.retry();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Result.retry();
        }
        BackgroundSyncScheduler.markCompleted(context);
        return success.get() ? Result.success() : Result.retry();
    }
}

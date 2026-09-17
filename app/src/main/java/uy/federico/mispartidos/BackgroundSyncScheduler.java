package uy.federico.mispartidos;

import android.content.Context;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import java.util.concurrent.TimeUnit;

public class BackgroundSyncScheduler {
    private static final String WORK_NAME = "mis_partidos_background_sync";
    private static final long INTERVAL_HOURS = 12;

    static void schedule(Context context) {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();
        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(
                BackgroundSyncWorker.class, INTERVAL_HOURS, TimeUnit.HOURS,
                1, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build();
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request);
        AppStore store = new AppStore(context);
        if (store.nextBackgroundSync() <= System.currentTimeMillis())
            store.saveNextBackgroundSync(System.currentTimeMillis() + TimeUnit.HOURS.toMillis(INTERVAL_HOURS));
    }

    static void markCompleted(Context context) {
        new AppStore(context).saveNextBackgroundSync(
                System.currentTimeMillis() + TimeUnit.HOURS.toMillis(INTERVAL_HOURS));
    }
}

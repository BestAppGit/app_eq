package com.mwg.hyperoseq;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.media.audiofx.Equalizer;
import android.os.Build;
import android.os.IBinder;

import org.json.JSONException;

public class EqProfileService extends Service {
    private static final String CHANNEL_ID = "eq_profile";
    private static final int NOTIFICATION_ID = 10;

    private Equalizer equalizer;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(NOTIFICATION_ID, createNotification());
        applyStoredProfile();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        if (equalizer != null) {
            equalizer.release();
            equalizer = null;
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void applyStoredProfile() {
        String json = ProfileStore.loadJson(this);
        if (!ProfileStore.isEnabled(this) || json == null) {
            stopSelf();
            return;
        }

        try {
            EqProfile profile = EqProfile.fromJson(json);
            if (equalizer == null) {
                equalizer = new Equalizer(0, 0);
            }
            equalizer.setEnabled(true);

            short[] range = equalizer.getBandLevelRange();
            short minLevel = range[0];
            short maxLevel = range[1];
            short bandCount = equalizer.getNumberOfBands();

            for (short band = 0; band < bandCount; band++) {
                float centerHz = equalizer.getCenterFreq(band) / 1000f;
                float gainDb = approximateGain(profile, centerHz);
                short milliBel = (short) Math.round(gainDb * 100);
                milliBel = (short) Math.max(minLevel, Math.min(maxLevel, milliBel));
                equalizer.setBandLevel(band, milliBel);
            }
        } catch (RuntimeException | JSONException error) {
            ProfileStore.setEnabled(this, false);
            stopSelf();
        }
    }

    private float approximateGain(EqProfile profile, float centerHz) {
        float result = profile.preampDb;
        for (EqProfile.Filter filter : profile.filters) {
            float octaveDistance = Math.abs((float) (Math.log(centerHz / filter.frequencyHz) / Math.log(2)));
            float width = Math.max(0.08f, 1f / filter.q);
            float weight = (float) Math.exp(-(octaveDistance * octaveDistance) / (2f * width * width));
            result += filter.gainDb * weight;
        }
        return Math.max(-24f, Math.min(12f, result));
    }

    private Notification createNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "EQ HyperOS",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }

        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);

        return builder
                .setContentTitle("EQ HyperOS ativo")
                .setContentText("Perfil aplicado por aproximação nas bandas do Android.")
                .setSmallIcon(R.drawable.ic_stat_eq)
                .build();
    }
}

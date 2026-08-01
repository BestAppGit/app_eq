package com.mwg.hyperoseq;

import android.app.Activity;
import android.media.audiofx.Equalizer;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private static final int TARGET_FREQUENCY_HZ = 60;

    private Equalizer equalizer;
    private short band60Hz = -1;
    private short minLevel;
    private short maxLevel;
    private short currentLevel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setupEqualizer();
        setContentView(createUi());
    }

    @Override
    protected void onDestroy() {
        if (equalizer != null) {
            equalizer.release();
        }
        super.onDestroy();
    }

    private void setupEqualizer() {
        try {
            equalizer = new Equalizer(0, 0);
            equalizer.setEnabled(true);

            short[] levelRange = equalizer.getBandLevelRange();
            minLevel = levelRange[0];
            maxLevel = levelRange[1];
            band60Hz = findClosestBand(TARGET_FREQUENCY_HZ);
            currentLevel = equalizer.getBandLevel(band60Hz);
        } catch (RuntimeException error) {
            equalizer = null;
            currentLevel = 0;
            Toast.makeText(this, "Equalizador indisponível neste aparelho.", Toast.LENGTH_LONG).show();
        }
    }

    private LinearLayout createUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(android.view.Gravity.CENTER);
        root.setPadding(32, 32, 32, 32);

        TextView title = new TextView(this);
        title.setText("60 Hz");
        title.setTextSize(28);
        title.setGravity(android.view.Gravity.CENTER);
        root.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setGravity(android.view.Gravity.CENTER);
        LinearLayout.LayoutParams controlsParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        controlsParams.setMargins(0, 40, 0, 0);

        Button decrease = new Button(this);
        decrease.setText("-");
        decrease.setTextSize(24);
        decrease.setOnClickListener(view -> changeLevel(-100));

        Button increase = new Button(this);
        increase.setText("+");
        increase.setTextSize(24);
        increase.setOnClickListener(view -> changeLevel(100));

        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(160, 120);
        buttonParams.setMargins(24, 0, 24, 0);
        controls.addView(decrease, buttonParams);
        controls.addView(increase, buttonParams);

        root.addView(controls, controlsParams);

        return root;
    }

    private short findClosestBand(int frequencyHz) {
        short bestBand = 0;
        int bestDistance = Integer.MAX_VALUE;

        for (short band = 0; band < equalizer.getNumberOfBands(); band++) {
            int centerHz = equalizer.getCenterFreq(band) / 1000;
            int distance = Math.abs(centerHz - frequencyHz);
            if (distance < bestDistance) {
                bestDistance = distance;
                bestBand = band;
            }
        }

        return bestBand;
    }

    private void changeLevel(int deltaMb) {
        if (equalizer == null || band60Hz < 0) {
            Toast.makeText(this, "Equalizador indisponível.", Toast.LENGTH_SHORT).show();
            return;
        }

        int nextLevel = currentLevel + deltaMb;
        nextLevel = Math.max(minLevel, Math.min(maxLevel, nextLevel));
        currentLevel = (short) nextLevel;
        equalizer.setBandLevel(band60Hz, currentLevel);
    }
}

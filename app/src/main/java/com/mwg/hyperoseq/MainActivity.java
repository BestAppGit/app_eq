package com.mwg.hyperoseq;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.audiofx.Equalizer;
import android.media.audiofx.DynamicsProcessing;
import android.media.audiofx.Visualizer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int REQUEST_IMPORT_PROFILE = 1001;
    private static final int TARGET_FREQUENCY_HZ = 60;

    private Equalizer equalizer;
    private short band60Hz = -1;
    private short minLevel;
    private short maxLevel;
    private short currentLevel;
    private TextView profileLabel;
    private Button toggleProfile;
    private DynamicsProcessing dynamicsProcessing;
    private float[] dynamicsBaseline;
    private float[] dynamicsProcessed;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestNotificationPermission();
        setContentView(createUi());
        refreshProfileUi();
    }

    @Override
    protected void onDestroy() {
        if (equalizer != null) {
            equalizer.release();
        }
        releaseDynamicsProcessing();
        super.onDestroy();
    }

    private boolean setupEqualizer() {
        try {
            equalizer = new Equalizer(0, 0);
            equalizer.setEnabled(true);

            short[] levelRange = equalizer.getBandLevelRange();
            minLevel = levelRange[0];
            maxLevel = levelRange[1];
            band60Hz = findClosestBand(TARGET_FREQUENCY_HZ);
            currentLevel = equalizer.getBandLevel(band60Hz);
            return true;
        } catch (RuntimeException error) {
            equalizer = null;
            currentLevel = 0;
            Toast.makeText(this, "Equalizador indisponível neste aparelho.", Toast.LENGTH_LONG).show();
            return false;
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

        profileLabel = new TextView(this);
        profileLabel.setTextSize(16);
        profileLabel.setGravity(android.view.Gravity.CENTER);
        LinearLayout.LayoutParams profileParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        profileParams.setMargins(0, 48, 0, 16);
        root.addView(profileLabel, profileParams);

        Button importProfile = new Button(this);
        importProfile.setText("Importar perfil");
        importProfile.setOnClickListener(view -> openProfilePicker());
        root.addView(importProfile, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        toggleProfile = new Button(this);
        toggleProfile.setOnClickListener(view -> toggleImportedProfile());
        root.addView(toggleProfile, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        Button equalizerDiagnostic = new Button(this);
        equalizerDiagnostic.setText("Diagnóstico Equalizer");
        equalizerDiagnostic.setOnClickListener(view -> showEqualizerDiagnostic());
        root.addView(equalizerDiagnostic, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        Button dynamicsDiagnostic = new Button(this);
        dynamicsDiagnostic.setText("Teste DynamicsProcessing");
        dynamicsDiagnostic.setOnClickListener(view -> showDynamicsDiagnostic());
        root.addView(dynamicsDiagnostic, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        Button dynamicsMeter = new Button(this);
        dynamicsMeter.setText("Medidor DynamicsProcessing");
        dynamicsMeter.setOnClickListener(view -> showDynamicsMeter());
        root.addView(dynamicsMeter, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        return root;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_IMPORT_PROFILE && resultCode == RESULT_OK && data != null) {
            importProfile(data.getData());
        }
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
        if (equalizer == null && !setupEqualizer()) {
            return;
        }

        if (equalizer == null || band60Hz < 0) {
            Toast.makeText(this, "Equalizador indisponível.", Toast.LENGTH_SHORT).show();
            return;
        }

        int nextLevel = currentLevel + deltaMb;
        nextLevel = Math.max(minLevel, Math.min(maxLevel, nextLevel));
        currentLevel = (short) nextLevel;
        equalizer.setBandLevel(band60Hz, currentLevel);
    }

    private void openProfilePicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        startActivityForResult(intent, REQUEST_IMPORT_PROFILE);
    }

    private void importProfile(Uri uri) {
        if (uri == null) {
            return;
        }

        try {
            String json = readText(uri);
            EqProfile.fromJson(json);
            ProfileStore.save(this, json);
            ProfileStore.setEnabled(this, false);
            stopService(new Intent(this, EqProfileService.class));
            Toast.makeText(this, "Perfil importado.", Toast.LENGTH_SHORT).show();
            refreshProfileUi();
        } catch (IOException | JSONException error) {
            Toast.makeText(this, error.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private String readText(Uri uri) throws IOException {
        InputStream input = getContentResolver().openInputStream(uri);
        if (input == null) {
            throw new IOException("Não foi possível abrir o arquivo.");
        }

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int read;
        while ((read = input.read(buffer)) != -1) {
            output.write(buffer, 0, read);
            if (output.size() > 1024 * 1024) {
                input.close();
                throw new IOException("Arquivo grande demais.");
            }
        }
        input.close();
        return output.toString("UTF-8");
    }

    private void toggleImportedProfile() {
        if (!ProfileStore.hasProfile(this)) {
            Toast.makeText(this, "Importe um perfil primeiro.", Toast.LENGTH_SHORT).show();
            return;
        }

        boolean enabled = !ProfileStore.isEnabled(this);
        ProfileStore.setEnabled(this, enabled);
        Intent service = new Intent(this, EqProfileService.class);
        if (enabled) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(service);
            } else {
                startService(service);
            }
        } else {
            stopService(service);
        }
        refreshProfileUi();
    }

    private void refreshProfileUi() {
        if (profileLabel == null || toggleProfile == null) {
            return;
        }

        String json = ProfileStore.loadJson(this);
        if (json == null) {
            profileLabel.setText("Perfil ativo: nenhum");
            toggleProfile.setText("Ativar perfil");
            toggleProfile.setEnabled(false);
            return;
        }

        String state = ProfileStore.isEnabled(this) ? "ativo" : "desativado";
        profileLabel.setText("Perfil: " + EqProfile.nameFromJson(json) + " (" + state + ")");
        toggleProfile.setText(ProfileStore.isEnabled(this) ? "Desativar perfil" : "Ativar perfil");
        toggleProfile.setEnabled(true);
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 2001);
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 2002);
        }
    }

    private void showEqualizerDiagnostic() {
        LinearLayout root = createDiagnosticRoot("Diagnóstico Equalizer");
        TextView report = createReportText(buildEqualizerReport());
        root.addView(report);
        root.addView(createBackButton());
        setContentView(wrapScrollable(root));
    }

    private String buildEqualizerReport() {
        StringBuilder report = new StringBuilder();
        Equalizer diagnosticEqualizer = null;
        try {
            diagnosticEqualizer = new Equalizer(0, 0);
            short[] range = diagnosticEqualizer.getBandLevelRange();
            short bands = diagnosticEqualizer.getNumberOfBands();

            report.append("Sessão testada: global 0\n");
            report.append("Bandas disponíveis: ").append(bands).append("\n");
            report.append("Ganho mínimo/máximo: ")
                    .append(formatMb(range[0])).append(" / ")
                    .append(formatMb(range[1])).append("\n\n");

            for (short band = 0; band < bands; band++) {
                int centerHz = diagnosticEqualizer.getCenterFreq(band) / 1000;
                int[] bandRange = diagnosticEqualizer.getBandFreqRange(band);
                report.append("Banda ").append(band).append(": centro ")
                        .append(centerHz).append(" Hz, faixa ")
                        .append(bandRange[0] / 1000).append("-")
                        .append(bandRange[1] / 1000).append(" Hz\n");
            }

            report.append("\nMapeamento 55-65 Hz:\n");
            for (int hz = 55; hz <= 65; hz++) {
                short band = diagnosticEqualizer.getBand(hz * 1000);
                report.append(hz).append(" Hz -> banda ").append(band)
                        .append(" (centro ")
                        .append(diagnosticEqualizer.getCenterFreq(band) / 1000)
                        .append(" Hz)\n");
            }
        } catch (RuntimeException error) {
            report.append("Falha ao abrir Equalizer: ").append(error.getMessage()).append("\n");
        } finally {
            if (diagnosticEqualizer != null) {
                diagnosticEqualizer.release();
            }
        }
        return report.toString();
    }

    private void showDynamicsDiagnostic() {
        LinearLayout root = createDiagnosticRoot("Teste DynamicsProcessing");
        TextView report = createReportText(buildDynamicsReport());
        root.addView(report);

        Button apply = new Button(this);
        apply.setText("Aplicar teste 55-65 Hz");
        apply.setOnClickListener(view -> {
            String result = applyDynamicsProcessingTest();
            report.setText(buildDynamicsReport() + "\n\n" + result);
        });
        root.addView(apply, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        Button remove = new Button(this);
        remove.setText("Remover DynamicsProcessing");
        remove.setOnClickListener(view -> {
            releaseDynamicsProcessing();
            report.setText(buildDynamicsReport() + "\n\nTeste removido.");
        });
        root.addView(remove, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        root.addView(createBackButton());
        setContentView(wrapScrollable(root));
    }

    private String buildDynamicsReport() {
        StringBuilder report = new StringBuilder();
        report.append("API do aparelho: ").append(Build.VERSION.SDK_INT).append("\n");
        report.append("DynamicsProcessing requer Android 9/API 28 ou superior.\n");
        report.append("Sessão testada: global 0\n\n");
        report.append("Bandas propostas para o teste:\n");
        for (int hz = 55; hz <= 65; hz++) {
            float gain = hz == 59 ? -12f : 0f;
            report.append(hz).append(" Hz cutoff, ganho ").append(gain).append(" dB\n");
        }
        report.append("\nAtenção: nesta API, a frequência é cutoff/topo de banda, não centro paramétrico com Q.");
        report.append("\nO teste não aplica preamp para não abaixar a música inteira.");
        return report.toString();
    }

    private String applyDynamicsProcessingTest() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            return "DynamicsProcessing indisponível nesta versão do Android.";
        }

        try {
            releaseDynamicsProcessing();
            int bandCount = 11;
            DynamicsProcessing.Eq preEq = new DynamicsProcessing.Eq(true, true, bandCount);
            for (int i = 0; i < bandCount; i++) {
                int cutoffHz = 55 + i;
                float gainDb = cutoffHz == 59 ? -12f : 0f;
                preEq.setBand(i, new DynamicsProcessing.EqBand(true, cutoffHz, gainDb));
            }

            DynamicsProcessing.Config config = new DynamicsProcessing.Config.Builder(
                    DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
                    2,
                    true,
                    bandCount,
                    false,
                    0,
                    false,
                    0,
                    false
            ).setPreEqAllChannelsTo(preEq).setInputGainAllChannelsTo(0f).build();

            dynamicsProcessing = new DynamicsProcessing(0, 0, config);
            dynamicsProcessing.setEnabled(true);

            StringBuilder result = new StringBuilder();
            result.append("DynamicsProcessing criado e habilitado.\n");
            result.append("Canais reportados: ").append(dynamicsProcessing.getChannelCount()).append("\n");
            result.append("Configuração lida de volta:\n");
            DynamicsProcessing.Eq activeEq = dynamicsProcessing
                    .getChannelByChannelIndex(0)
                    .getPreEq();
            for (int i = 0; i < activeEq.getBandCount(); i++) {
                DynamicsProcessing.EqBand band = activeEq.getBand(i);
                result.append("Banda ").append(i)
                        .append(": cutoff ").append(band.getCutoffFrequency())
                        .append(" Hz, ganho ").append(band.getGain())
                        .append(" dB\n");
            }
            return result.toString();
        } catch (RuntimeException error) {
            releaseDynamicsProcessing();
            return "Falha no DynamicsProcessing: " + error.getMessage();
        }
    }

    private void showDynamicsMeter() {
        LinearLayout root = createDiagnosticRoot("Medidor DynamicsProcessing");
        TextView report = createReportText(buildMeterReport("Toque um WAV externo, por exemplo 59 Hz, no YouTube Music."));
        root.addView(report);

        Button baseline = new Button(this);
        baseline.setText("Medir sem efeito");
        baseline.setOnClickListener(view -> measureVisualizerAsync(false, report));
        root.addView(baseline, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        Button apply = new Button(this);
        apply.setText("Aplicar teste 59 Hz");
        apply.setOnClickListener(view -> {
            String result = applyDynamicsProcessingTest();
            report.setText(buildMeterReport(result));
        });
        root.addView(apply, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        Button processed = new Button(this);
        processed.setText("Medir com efeito");
        processed.setOnClickListener(view -> measureVisualizerAsync(true, report));
        root.addView(processed, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        Button remove = new Button(this);
        remove.setText("Remover efeito");
        remove.setOnClickListener(view -> {
            releaseDynamicsProcessing();
            report.setText(buildMeterReport("Efeito removido."));
        });
        root.addView(remove, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        root.addView(createBackButton());
        setContentView(wrapScrollable(root));
    }

    private void measureVisualizerAsync(boolean processed, TextView report) {
        report.setText(buildMeterReport("Medindo áudio global por 1 segundo..."));
        new Thread(() -> {
            String message;
            try {
                float[] levels = measureVisualizerLevels();
                if (processed) {
                    dynamicsProcessed = levels;
                } else {
                    dynamicsBaseline = levels;
                }
                message = processed ? "Medição com efeito concluída." : "Medição sem efeito concluída.";
            } catch (RuntimeException error) {
                message = "Falha ao medir com Visualizer: " + error.getMessage();
            }
            String finalMessage = message;
            runOnUiThread(() -> report.setText(buildMeterReport(finalMessage)));
        }).start();
    }

    private float[] measureVisualizerLevels() {
        Visualizer visualizer = null;
        try {
            visualizer = new Visualizer(0);
            int captureSize = Visualizer.getCaptureSizeRange()[1];
            visualizer.setCaptureSize(captureSize);
            visualizer.setScalingMode(Visualizer.SCALING_MODE_NORMALIZED);
            visualizer.setEnabled(true);

            byte[] fft = new byte[captureSize];
            float[] sums = new float[11];
            int[] counts = new int[11];
            long deadline = System.currentTimeMillis() + 1000;
            while (System.currentTimeMillis() < deadline) {
                int result = visualizer.getFft(fft);
                if (result == Visualizer.SUCCESS) {
                    float[] sample = extractLowFrequencyLevels(fft, visualizer.getSamplingRate() / 1000f);
                    for (int i = 0; i < sample.length; i++) {
                        sums[i] += sample[i];
                        counts[i]++;
                    }
                }
                try {
                    Thread.sleep(60);
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }

            float[] levels = new float[11];
            for (int i = 0; i < levels.length; i++) {
                levels[i] = counts[i] == 0 ? -120f : sums[i] / counts[i];
            }
            return levels;
        } finally {
            if (visualizer != null) {
                visualizer.release();
            }
        }
    }

    private float[] extractLowFrequencyLevels(byte[] fft, float sampleRateHz) {
        float[] levels = new float[11];
        int captureSize = fft.length;
        for (int i = 0; i < levels.length; i++) {
            int targetHz = 55 + i;
            int bin = Math.max(1, Math.round(targetHz * captureSize / sampleRateHz));
            bin = Math.min(bin, captureSize / 2 - 1);
            int real = fft[2 * bin];
            int imag = fft[2 * bin + 1];
            double magnitude = Math.sqrt(real * real + imag * imag);
            levels[i] = (float) (20.0 * Math.log10(Math.max(1.0, magnitude)));
        }
        return levels;
    }

    private String buildMeterReport(String message) {
        StringBuilder report = new StringBuilder();
        report.append(message).append("\n\n");
        report.append("Fluxo recomendado:\n");
        report.append("1. Toque um WAV externo no YouTube Music.\n");
        report.append("2. Clique em Medir sem efeito.\n");
        report.append("3. Clique em Aplicar teste 59 Hz.\n");
        report.append("4. Clique em Medir com efeito.\n\n");
        report.append("Frequência | sem | com | delta\n");
        for (int i = 0; i < 11; i++) {
            int hz = 55 + i;
            String base = dynamicsBaseline == null ? "--" : formatDbValue(dynamicsBaseline[i]);
            String with = dynamicsProcessed == null ? "--" : formatDbValue(dynamicsProcessed[i]);
            String delta = dynamicsBaseline == null || dynamicsProcessed == null
                    ? "--"
                    : formatDbValue(dynamicsProcessed[i] - dynamicsBaseline[i]);
            report.append(hz).append(" Hz | ")
                    .append(base).append(" | ")
                    .append(with).append(" | ")
                    .append(delta).append("\n");
        }
        report.append("\nInterpretação: se 59 Hz cair forte e 58/60 Hz ficarem próximos de 0 dB de delta, o corte é estreito. Se vários vizinhos caírem juntos, a banda é ampla.");
        return report.toString();
    }

    private LinearLayout createDiagnosticRoot(String titleText) {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(32, 32, 32, 32);

        TextView title = new TextView(this);
        title.setText(titleText);
        title.setTextSize(24);
        root.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        return root;
    }

    private TextView createReportText(String text) {
        TextView report = new TextView(this);
        report.setText(text);
        report.setTextSize(15);
        report.setPadding(0, 24, 0, 24);
        return report;
    }

    private Button createBackButton() {
        Button back = new Button(this);
        back.setText("Voltar");
        back.setOnClickListener(view -> {
            releaseDynamicsProcessing();
            setContentView(createUi());
            refreshProfileUi();
        });
        return back;
    }

    private ScrollView wrapScrollable(LinearLayout content) {
        ScrollView scrollView = new ScrollView(this);
        scrollView.addView(content);
        return scrollView;
    }

    private String formatMb(short milliBel) {
        return String.format(Locale.US, "%.1f dB", milliBel / 100f);
    }

    private String formatDbValue(float value) {
        return String.format(Locale.US, "%+.1f dB", value);
    }

    private void releaseDynamicsProcessing() {
        if (dynamicsProcessing != null) {
            dynamicsProcessing.release();
            dynamicsProcessing = null;
        }
    }
}

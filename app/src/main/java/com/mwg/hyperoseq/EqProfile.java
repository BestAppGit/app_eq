package com.mwg.hyperoseq;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class EqProfile {
    public static class Filter {
        public final String type;
        public final float frequencyHz;
        public final float gainDb;
        public final float q;

        public Filter(String type, float frequencyHz, float gainDb, float q) {
            this.type = type;
            this.frequencyHz = frequencyHz;
            this.gainDb = gainDb;
            this.q = q;
        }
    }

    public final int version;
    public final String name;
    public final float preampDb;
    public final List<Filter> filters;

    public EqProfile(int version, String name, float preampDb, List<Filter> filters) {
        this.version = version;
        this.name = name;
        this.preampDb = preampDb;
        this.filters = filters;
    }

    public static EqProfile fromJson(String json) throws JSONException {
        JSONObject root = new JSONObject(json);
        int version = root.getInt("version");
        if (version != 1) {
            throw new JSONException("Versão de perfil não suportada.");
        }

        String name = root.optString("name", "Perfil personalizado").trim();
        if (name.length() == 0 || name.length() > 80) {
            throw new JSONException("Nome do perfil inválido.");
        }

        float preampDb = (float) root.getDouble("preampDb");
        if (preampDb < -24 || preampDb > 0) {
            throw new JSONException("preampDb deve ficar entre -24 e 0 dB.");
        }

        JSONArray items = root.getJSONArray("filters");
        if (items.length() > 32) {
            throw new JSONException("O perfil pode ter no máximo 32 filtros.");
        }
        validateCalibrationPoints(root);

        List<Filter> filters = new ArrayList<>();
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.getJSONObject(i);
            String type = item.optString("type", "");
            float frequencyHz = (float) item.getDouble("frequencyHz");
            float gainDb = (float) item.getDouble("gainDb");
            float q = (float) item.getDouble("q");

            if (!"peaking".equals(type)) {
                throw new JSONException("Apenas filtros peaking são suportados.");
            }
            if (frequencyHz < 20 || frequencyHz > 20000) {
                throw new JSONException("frequencyHz fora do limite.");
            }
            if (gainDb < -24 || gainDb > 12) {
                throw new JSONException("gainDb fora do limite.");
            }
            if (q < 0.1f || q > 20f) {
                throw new JSONException("q fora do limite.");
            }
            filters.add(new Filter(type, frequencyHz, gainDb, q));
        }

        return new EqProfile(version, name, preampDb, filters);
    }

    private static void validateCalibrationPoints(JSONObject root) throws JSONException {
        if (!root.has("calibrationPoints")) {
            return;
        }

        JSONArray points = root.getJSONArray("calibrationPoints");
        if (points.length() == 0 || points.length() > 20000) {
            throw new JSONException("calibrationPoints inválido.");
        }

        float previousHz = 0;
        for (int i = 0; i < points.length(); i++) {
            JSONObject point = points.getJSONObject(i);
            float frequencyHz = (float) point.getDouble("frequencyHz");
            float gainDb = (float) point.getDouble("gainDb");
            if (frequencyHz < 1 || frequencyHz > 20000 || frequencyHz <= previousHz) {
                throw new JSONException("frequencyHz inválido em calibrationPoints.");
            }
            if (gainDb < -24 || gainDb > 12) {
                throw new JSONException("gainDb inválido em calibrationPoints.");
            }
            previousHz = frequencyHz;
        }
    }

    public static String nameFromJson(String json) {
        try {
            return fromJson(json).name;
        } catch (JSONException error) {
            return "Perfil inválido";
        }
    }
}

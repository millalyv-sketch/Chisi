package it.nunzia.chisi;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;

import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Voce, volume e suoneria muta: condivisi tra schermata e servizio. */
public class Voce {

    static final String VOLUME = "volume";       // 10..100 (% del volume multimediale)
    static final String VOCE = "voce";           // nome tecnico della voce scelta
    static final String TONO = "tono";           // 50..200 (100 = normale)
    static final String VELOCITA = "velocita";   // 50..200 (100 = normale)
    static final String PERSONAGGIO = "personaggio"; // indice in PERSONAGGI

    /** Personaggi: la voce viene trasformata cambiando tono e velocità. */
    static final String[] PERSONAGGI = {"Normale", "Bambino", "Bambina", "Nonno", "Nonna"};
    //                                        tono  velocità  genere ("" = qualsiasi)
    private static final float[] P_TONO =    {0f,   1.75f,    1.95f,   0.62f,  0.80f};
    private static final float[] P_VEL =     {0f,   1.12f,    1.10f,   0.78f,  0.80f};
    private static final String[] P_GENERE = {"",   "maschile", "femminile", "maschile", "femminile"};

    private static final java.util.Random caso = new java.util.Random();

    static final AudioAttributes ATTRIBUTI = new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build();

    /** Applica voce, personaggio, tono e velocità scelti. */
    static void prepara(Context c, TextToSpeech tts) {
        SharedPreferences p = Impostazioni.pref(c);
        tts.setLanguage(Locale.ITALIAN);
        tts.setAudioAttributes(ATTRIBUTI);
        int pers = p.getInt(PERSONAGGIO, 0);
        if (pers < 0 || pers >= PERSONAGGI.length) pers = 0;
        String nome = p.getString(VOCE, null);
        Voice scelta = null;
        if (nome != null) {
            for (Voice v : vociItaliane(tts)) if (v.getName().equals(nome)) scelta = v;
        }
        // Un personaggio maschile/femminile cerca una voce del genere giusto
        if (pers > 0 && (scelta == null || !P_GENERE[pers].equals(genere(scelta)))) {
            Voice g = vocePerGenere(tts, P_GENERE[pers], false);
            if (g != null) scelta = g;
        }
        if (scelta != null) tts.setVoice(scelta);
        applicaTono(c, tts, pers);
    }

    /** Modalità sorpresa: voce e personaggio a caso. */
    static void preparaACaso(Context c, TextToSpeech tts) {
        tts.setLanguage(Locale.ITALIAN);
        tts.setAudioAttributes(ATTRIBUTI);
        int pers = caso.nextInt(PERSONAGGI.length);
        Voice v = pers > 0 ? vocePerGenere(tts, P_GENERE[pers], true) : null;
        if (v == null) {
            List<Voice> tutte = vociItaliane(tts);
            if (!tutte.isEmpty()) v = tutte.get(caso.nextInt(tutte.size()));
        }
        if (v != null) tts.setVoice(v);
        applicaTono(c, tts, pers);
    }

    private static void applicaTono(Context c, TextToSpeech tts, int pers) {
        SharedPreferences p = Impostazioni.pref(c);
        float tono = p.getInt(TONO, 100) / 100f;
        float vel = p.getInt(VELOCITA, 100) / 100f;
        if (pers > 0) {
            tono = P_TONO[pers];
            vel = P_VEL[pers] * vel; // la velocità scelta da te resta valida
        }
        tts.setPitch(tono);
        tts.setSpeechRate(vel);
    }

    private static Voice vocePerGenere(TextToSpeech tts, String g, boolean aCaso) {
        List<Voice> adatte = new ArrayList<>();
        for (Voice v : vociItaliane(tts)) if (g.equals(genere(v))) adatte.add(v);
        if (adatte.isEmpty()) return null;
        return aCaso ? adatte.get(caso.nextInt(adatte.size())) : adatte.get(0);
    }

    static String genere(Voice v) {
        String n = v.getName().toLowerCase(Locale.ROOT);
        // Voci Google italiane: itb e kda femminili, itc e itd maschili
        if (n.contains("itc") || n.contains("itd") || (n.contains("male") && !n.contains("female"))) return "maschile";
        if (n.contains("itb") || n.contains("kda") || n.contains("female")) return "femminile";
        return "";
    }

    /** Voci italiane installate sul telefono, quelle senza internet prima. */
    static List<Voice> vociItaliane(TextToSpeech tts) {
        List<Voice> out = new ArrayList<>();
        try {
            for (Voice v : tts.getVoices()) {
                Locale l = v.getLocale();
                if (l != null && "it".equals(l.getLanguage()) && !v.getFeatures().contains("notInstalled")) {
                    out.add(v);
                }
            }
        } catch (Exception ignored) { }
        Collections.sort(out, (a, b) -> {
            if (a.isNetworkConnectionRequired() != b.isNetworkConnectionRequired())
                return a.isNetworkConnectionRequired() ? 1 : -1;
            return a.getName().compareTo(b.getName());
        });
        return out;
    }

    /** Nome comprensibile: "Voce maschile 1", "Voce femminile 2"... */
    static String descrivi(Voice v, int numero) {
        String genere = genere(v);
        String s = "Voce " + (genere.isEmpty() ? "" : genere + " ") + numero;
        if (v.isNetworkConnectionRequired()) s += " (più naturale, serve internet)";
        return s;
    }

    // ---------- Volume ----------

    /** Porta il volume multimediale al livello scelto. Restituisce il volume di prima (o -1). */
    static int alza(Context c, AudioManager audio) {
        if (audio == null) return -1;
        // Se stai ascoltando musica non tocchiamo il volume: la musica viene solo abbassata
        if (audio.isMusicActive()) return -1;
        int max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        int scelto = Math.max(1, Math.round(max * Impostazioni.pref(c).getInt(VOLUME, 100) / 100f));
        int prima = audio.getStreamVolume(AudioManager.STREAM_MUSIC);
        try {
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, scelto, 0);
        } catch (Exception e) {
            return -1;
        }
        return prima;
    }

    static void ripristina(AudioManager audio, int prima) {
        if (audio == null || prima < 0) return;
        try { audio.setStreamVolume(AudioManager.STREAM_MUSIC, prima, 0); } catch (Exception ignored) { }
    }

    // ---------- Suoneria muta ----------

    static final String NOME_MUTO = "ChiSì muto";

    /** Mette nel telefono una suoneria di notifica silenziosa. Restituisce true se c'è. */
    static boolean creaSuoneriaMuta(Context c) {
        if (Build.VERSION.SDK_INT < 29) return false;
        Uri dove = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
        try (Cursor cur = c.getContentResolver().query(dove, new String[]{MediaStore.Audio.Media._ID},
                MediaStore.Audio.Media.DISPLAY_NAME + "=?", new String[]{"ChiSi_muto.wav"}, null)) {
            if (cur != null && cur.getCount() > 0) return true;
        } catch (Exception ignored) { }

        ContentValues v = new ContentValues();
        v.put(MediaStore.Audio.Media.DISPLAY_NAME, "ChiSi_muto.wav");
        v.put(MediaStore.Audio.Media.TITLE, NOME_MUTO);
        v.put(MediaStore.Audio.Media.MIME_TYPE, "audio/wav");
        v.put(MediaStore.Audio.Media.RELATIVE_PATH, "Notifications/");
        v.put(MediaStore.Audio.Media.IS_NOTIFICATION, 1);
        v.put(MediaStore.Audio.Media.IS_PENDING, 1);
        try {
            Uri uri = c.getContentResolver().insert(dove, v);
            if (uri == null) return false;
            try (OutputStream out = c.getContentResolver().openOutputStream(uri)) {
                out.write(silenzioWav());
            }
            ContentValues fatto = new ContentValues();
            fatto.put(MediaStore.Audio.Media.IS_PENDING, 0);
            c.getContentResolver().update(uri, fatto, null, null);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** Mezzo secondo di silenzio in formato WAV. */
    static byte[] silenzioWav() {
        int campioni = 4000, dati = campioni * 2;
        byte[] b = new byte[44 + dati];
        scrivi(b, 0, "RIFF"); int32(b, 4, 36 + dati); scrivi(b, 8, "WAVE");
        scrivi(b, 12, "fmt "); int32(b, 16, 16); int16(b, 20, 1); int16(b, 22, 1);
        int32(b, 24, 8000); int32(b, 28, 16000); int16(b, 32, 2); int16(b, 34, 16);
        scrivi(b, 36, "data"); int32(b, 40, dati);
        return b;
    }

    private static void scrivi(byte[] b, int o, String s) {
        for (int i = 0; i < 4; i++) b[o + i] = (byte) s.charAt(i);
    }

    private static void int32(byte[] b, int o, int v) {
        for (int i = 0; i < 4; i++) b[o + i] = (byte) (v >> (8 * i));
    }

    private static void int16(byte[] b, int o, int v) {
        b[o] = (byte) v; b[o + 1] = (byte) (v >> 8);
    }
}

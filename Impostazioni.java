package it.nunzia.chisi;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Random;

/** Tutte le impostazioni di ChiSì, salvate sul telefono. */
public class Impostazioni {

    static final String PREF = "impostazioni";
    static final String ATTIVA = "attiva";
    static final String ANCHE_SILENZIOSO = "anche_silenzioso";
    static final String FRASE_MESSAGGIO = "frase_messaggio";
    static final String FRASE_VOCALE = "frase_vocale";
    static final String FRASE_GRUPPO = "frase_gruppo";
    static final String PACE_ATTIVA = "pace_attiva";
    static final String PACE_INIZIO = "pace_inizio"; // minuti dalla mezzanotte
    static final String PACE_FINE = "pace_fine";
    static final String PERSONE = "persone";       // {chiave: {frase, zitto}}
    static final String VISTI = "visti";           // ultimi mittenti ricevuti

    static final String PREFISSO_GRUPPO = "Gruppo ";

    static final String DEF_MESSAGGIO = "Ehi, ti scrive {nome}!";
    static final String DEF_VOCALE = "{nome} ti ha mandato un vocale";
    static final String DEF_GRUPPO = "{nome} scrive nel gruppo {gruppo}";

    static final String[] IDEE_MESSAGGIO = {
            "Ehi, ti scrive {nome}!",
            "Driin! C'è {nome}!",
            "Chi è? È {nome}!",
            "Novità da {nome}!",
            "Corri, ti cerca {nome}!",
            "Messaggio WhatsApp da {nome}",
            "Toc toc! È {nome}!",
            "Attenzione, attenzione: scrive {nome}!",
            "Indovina chi ti scrive? {nome}!",
            "C'è posta per te, da {nome}!",
            "{nome} ha qualcosa da dirti!",
            "Ultim'ora: {nome} ti ha scritto!",
            "Fermi tutti! Messaggio da {nome}!",
            "{nome} si è ricordato di te!",
            "Sorpresa! Ti scrive {nome}!",
            "Bip bip! È {nome}!",
            "Ehi, {nome} vuole parlarti!",
            "Arriva un pensiero da {nome}!",
            "Notizia fresca da {nome}!",
            "Squillo in arrivo: è {nome}!",
            "Chi si'? È {nome}!",
            "Uè, t'ha scritto {nome}!",
            "Guagliò, ce sta {nome}!",
            "Mamma mia, {nome} t'ha scritto!",
            "Jamme, rispunne a {nome}!",
            "Uè uè, è arrivato {nome}!",
            "Siente a me: t'ha scritto {nome}!",
            "Chi te cerca? {nome}!",
            "E chi è chistu ccà? È {nome}!",
            "Oi né, te scrive {nome}!"
    };
    static final String[] IDEE_VOCALE = {
            "{nome} ti ha mandato un vocale",
            "Vocale in arrivo da {nome}!",
            "Apri le orecchie: {nome} ti ha lasciato un vocale",
            "{nome} ha parlato: c'è un vocale per te!",
            "Messaggio vocale da {nome}",
            "Prepara le orecchie: vocale di {nome}!",
            "{nome} ti ha lasciato la sua voce!",
            "Un vocale fresco fresco da {nome}!",
            "Ascolta qua: vocale di {nome}!",
            "{nome} ha registrato qualcosa per te!",
            "C'è da ascoltare: vocale di {nome}!",
            "Driin! Vocale da {nome}!",
            "{nome} ha preferito parlare: vocale per te!",
            "Novità a voce da {nome}!",
            "Uè, {nome} t'ha mannato nu vocale!",
            "Siente, siente: vocale 'e {nome}!",
            "Guagliò, nu vocale 'e {nome}!",
            "Arapite 'e recchie: vocale 'e {nome}!",
            "Chi si'? È {nome}, cu nu vocale!",
            "{nome} ha parlato, mo' tocca a te sentì!"
    };
    static final String[] IDEE_GRUPPO = {
            "{nome} scrive nel gruppo {gruppo}",
            "Novità nel gruppo {gruppo}, da {nome}",
            "Movimento nel gruppo {gruppo}!",
            "{nome} chiacchiera nel gruppo {gruppo}",
            "Nel gruppo {gruppo} parla {nome}!",
            "Si chiacchiera nel gruppo {gruppo}!",
            "Gruppo {gruppo}: ha scritto {nome}!",
            "Riunione nel gruppo {gruppo}: c'è {nome}!",
            "Il gruppo {gruppo} si sveglia: scrive {nome}!",
            "Ultime dal gruppo {gruppo}, firmato {nome}!",
            "{nome} dice la sua nel gruppo {gruppo}!",
            "Toc toc, gruppo {gruppo}: è {nome}!",
            "Che si dice nel gruppo {gruppo}? Lo dice {nome}!",
            "Gruppo {gruppo} in fermento, grazie a {nome}!",
            "Uè, int'o gruppo {gruppo} scrive {nome}!",
            "Guagliò, {nome} parla int'o gruppo {gruppo}!",
            "Int'o gruppo {gruppo} ce sta {nome}!",
            "Che ammuina int'o gruppo {gruppo}! È {nome}!",
            "Chiacchiere int'o gruppo {gruppo}: {nome}!",
            "{nome} fa ammuina int'o gruppo {gruppo}!"
    };

    static final String SORPRESA = "sorpresa";

    /** Modalità sorpresa: una frase a caso tra tutti gli esempi e le frasi scritte da te. */
    static String fraseACaso(String[] idee, String tueRighe, String nome, String gruppo) {
        StringBuilder tutte = new StringBuilder();
        for (String i : idee) tutte.append(i).append("\n");
        if (tueRighe != null) tutte.append(tueRighe);
        return componi(tutte.toString(), nome, gruppo);
    }

    private static final Random caso = new Random();

    static SharedPreferences pref(Context c) {
        return c.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    static String frase(Context c, String chiave, String predefinita) {
        String s = pref(c).getString(chiave, null);
        return s == null ? predefinita : s;
    }

    /** Sceglie a caso una delle righe scritte e sostituisce {nome} e {gruppo}. */
    static String componi(String righe, String nome, String gruppo) {
        List<String> scelte = new ArrayList<>();
        if (righe != null) {
            for (String r : righe.split("\n")) {
                if (!r.trim().isEmpty()) scelte.add(r.trim());
            }
        }
        if (scelte.isEmpty()) return null;
        String s = scelte.get(caso.nextInt(scelte.size()));
        s = s.replace("{nome}", nome == null ? "qualcuno" : nome);
        s = s.replace("{gruppo}", gruppo == null ? "" : gruppo);
        return s;
    }

    // ---------- Persone e gruppi ----------

    static JSONObject persone(Context c) {
        try {
            return new JSONObject(pref(c).getString(PERSONE, "{}"));
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    static JSONObject persona(Context c, String chiave) {
        return persone(c).optJSONObject(chiave);
    }

    static void salvaPersona(Context c, String chiave, String frase, boolean zitto) {
        try {
            JSONObject tutte = persone(c);
            if ((frase == null || frase.trim().isEmpty()) && !zitto) {
                tutte.remove(chiave);
            } else {
                JSONObject p = new JSONObject();
                p.put("frase", frase == null ? "" : frase.trim());
                p.put("zitto", zitto);
                tutte.put(chiave, p);
            }
            pref(c).edit().putString(PERSONE, tutte.toString()).apply();
        } catch (Exception ignored) { }
    }

    static boolean silenziato(Context c, String chiave) {
        JSONObject p = persona(c, chiave);
        return p != null && p.optBoolean("zitto", false);
    }

    static String frasePersona(Context c, String chiave) {
        JSONObject p = persona(c, chiave);
        if (p == null) return null;
        String f = p.optString("frase", "").trim();
        return f.isEmpty() ? null : f;
    }

    /** Ricorda chi ti ha scritto, così compare nell'elenco da personalizzare. */
    static void ricorda(Context c, String chiave) {
        if (chiave == null) return;
        try {
            JSONArray vecchi = new JSONArray(pref(c).getString(VISTI, "[]"));
            JSONArray nuovi = new JSONArray();
            nuovi.put(chiave);
            for (int i = 0; i < vecchi.length() && nuovi.length() < 80; i++) {
                String s = vecchi.optString(i);
                if (!s.equals(chiave)) nuovi.put(s);
            }
            pref(c).edit().putString(VISTI, nuovi.toString()).apply();
        } catch (Exception ignored) { }
    }

    /** Elenco per lo schermo: prima quelli personalizzati, poi gli ultimi ricevuti. */
    static List<String> elencoPersone(Context c) {
        List<String> out = new ArrayList<>();
        JSONObject tutte = persone(c);
        JSONArray nomi = tutte.names();
        if (nomi != null) {
            for (int i = 0; i < nomi.length(); i++) out.add(nomi.optString(i));
        }
        java.util.Collections.sort(out, String.CASE_INSENSITIVE_ORDER);
        try {
            JSONArray visti = new JSONArray(pref(c).getString(VISTI, "[]"));
            for (int i = 0; i < visti.length(); i++) {
                String s = visti.optString(i);
                if (!s.isEmpty() && !out.contains(s)) out.add(s);
            }
        } catch (Exception ignored) { }
        return out;
    }

    // ---------- Orari di pace ----------

    static boolean inOrarioDiPace(Context c) {
        SharedPreferences p = pref(c);
        if (!p.getBoolean(PACE_ATTIVA, false)) return false;
        int inizio = p.getInt(PACE_INIZIO, 22 * 60);
        int fine = p.getInt(PACE_FINE, 7 * 60 + 30);
        Calendar ora = Calendar.getInstance();
        int adesso = ora.get(Calendar.HOUR_OF_DAY) * 60 + ora.get(Calendar.MINUTE);
        if (inizio == fine) return false;
        if (inizio < fine) return adesso >= inizio && adesso < fine;
        return adesso >= inizio || adesso < fine; // scavalca la mezzanotte
    }

    static String orario(int minuti) {
        return String.format(java.util.Locale.ITALY, "%02d:%02d", minuti / 60, minuti % 60);
    }
}

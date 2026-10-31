package it.nunzia.chisi;

import android.app.Notification;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Parcelable;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;

import java.util.Locale;

/**
 * Legge le notifiche di WhatsApp e annuncia a voce chi ha scritto.
 * Non legge MAI il testo dei messaggi (quindi nemmeno i codici di verifica).
 */
public class AscoltaNotifiche extends NotificationListenerService {

    private static final AudioAttributes ATTRIBUTI = Voce.ATTRIBUTI;

    private TextToSpeech voce;
    private boolean vocePronta = false;
    private AudioManager audio;
    private AudioFocusRequest richiestaAudio;
    private int inCoda = 0;
    private int volumePrima = -1;

    private String ultimoAnnuncio = "";
    private long ultimoMomento = 0;

    @Override
    public void onCreate() {
        super.onCreate();
        audio = (AudioManager) getSystemService(AUDIO_SERVICE);
        // Chiede di abbassare (non fermare) la musica mentre parla
        richiestaAudio = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(ATTRIBUTI)
                .build();
        voce = new TextToSpeech(this, stato -> {
            if (stato == TextToSpeech.SUCCESS) {
                Voce.prepara(this, voce);
                voce.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                    @Override public void onStart(String id) { }
                    @Override public void onDone(String id) { finito(); }
                    @Override public void onError(String id) { finito(); }
                });
                vocePronta = true;
            }
        });
    }

    private synchronized void finito() {
        inCoda = Math.max(0, inCoda - 1);
        if (inCoda == 0 && audio != null) {
            audio.abandonAudioFocusRequest(richiestaAudio);
            Voce.ripristina(audio, volumePrima);
            volumePrima = -1;
        }
    }

    @Override
    public void onDestroy() {
        if (voce != null) voce.shutdown();
        super.onDestroy();
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        String app = sbn.getPackageName();
        if (!"com.whatsapp".equals(app) && !"com.whatsapp.w4b".equals(app)) return;
        if (!Impostazioni.pref(this).getBoolean(Impostazioni.ATTIVA, true)) return;

        Notification n = sbn.getNotification();
        if ((n.flags & Notification.FLAG_GROUP_SUMMARY) != 0) return;
        if ((n.flags & Notification.FLAG_ONGOING_EVENT) != 0) return;

        // Durante telefonate (normali o WhatsApp) sta zitta
        if (audio != null) {
            int modo = audio.getMode();
            if (modo == AudioManager.MODE_IN_CALL || modo == AudioManager.MODE_IN_COMMUNICATION
                    || modo == AudioManager.MODE_RINGTONE) return;
            if (audio.getRingerMode() != AudioManager.RINGER_MODE_NORMAL
                    && !Impostazioni.pref(this).getBoolean(Impostazioni.ANCHE_SILENZIOSO, false)) return;
        }
        if (Impostazioni.inOrarioDiPace(this)) return;

        Mittente m = leggiMittente(n.extras);
        if (m == null) return;

        String chiavePersona = m.nome;
        String chiaveGruppo = m.gruppo == null ? null : Impostazioni.PREFISSO_GRUPPO + m.gruppo;
        if (chiaveGruppo != null) Impostazioni.ricorda(this, chiaveGruppo);
        if (chiavePersona != null) Impostazioni.ricorda(this, chiavePersona);

        if (chiaveGruppo != null && Impostazioni.silenziato(this, chiaveGruppo)) return;
        if (chiavePersona != null && Impostazioni.silenziato(this, chiavePersona)) return;

        String frase = scegliFrase(m, chiavePersona, chiaveGruppo);
        if (frase == null || frase.trim().isEmpty()) return;

        // WhatsApp a volte ripubblica la stessa notifica: evita doppioni ravvicinati
        long ora = System.currentTimeMillis();
        String impronta = m.nome + "|" + m.gruppo + "|" + m.vocale;
        if (impronta.equals(ultimoAnnuncio) && ora - ultimoMomento < 4000) return;
        ultimoAnnuncio = impronta;
        ultimoMomento = ora;

        parla(frase);
    }

    private String scegliFrase(Mittente m, String chiavePersona, String chiaveGruppo) {
        boolean sorpresa = Impostazioni.pref(this).getBoolean(Impostazioni.SORPRESA, false);
        if (sorpresa) {
            String propria = Impostazioni.frasePersona(this, m.gruppo != null ? chiaveGruppo : chiavePersona);
            if (propria != null && !m.vocale) return Impostazioni.componi(propria, m.nome, m.gruppo);
            String[] idee; String chiave; String def;
            if (m.vocale) { idee = Impostazioni.IDEE_VOCALE; chiave = Impostazioni.FRASE_VOCALE; def = Impostazioni.DEF_VOCALE; }
            else if (m.gruppo != null) { idee = Impostazioni.IDEE_GRUPPO; chiave = Impostazioni.FRASE_GRUPPO; def = Impostazioni.DEF_GRUPPO; }
            else { idee = Impostazioni.IDEE_MESSAGGIO; chiave = Impostazioni.FRASE_MESSAGGIO; def = Impostazioni.DEF_MESSAGGIO; }
            String f = Impostazioni.fraseACaso(idee, Impostazioni.frase(this, chiave, def), m.nome, m.gruppo);
            if (f != null && m.vocale && m.gruppo != null && !f.contains(m.gruppo)) f += ", nel gruppo " + m.gruppo;
            return f;
        }
        if (m.vocale) {
            String f = Impostazioni.componi(
                    Impostazioni.frase(this, Impostazioni.FRASE_VOCALE, Impostazioni.DEF_VOCALE),
                    m.nome, m.gruppo);
            if (f != null && m.gruppo != null && !f.contains(m.gruppo)) f += ", nel gruppo " + m.gruppo;
            return f;
        }
        if (m.gruppo != null) {
            String propria = Impostazioni.frasePersona(this, chiaveGruppo);
            String righe = propria != null ? propria
                    : Impostazioni.frase(this, Impostazioni.FRASE_GRUPPO, Impostazioni.DEF_GRUPPO);
            return Impostazioni.componi(righe, m.nome, m.gruppo);
        }
        String propria = Impostazioni.frasePersona(this, chiavePersona);
        String righe = propria != null ? propria
                : Impostazioni.frase(this, Impostazioni.FRASE_MESSAGGIO, Impostazioni.DEF_MESSAGGIO);
        return Impostazioni.componi(righe, m.nome, null);
    }

    private synchronized void parla(String frase) {
        if (!vocePronta) return;
        // prende le ultime scelte (voce, tono, velocità), o tutto a caso in modalità sorpresa
        if (Impostazioni.pref(this).getBoolean(Impostazioni.SORPRESA, false)) Voce.preparaACaso(this, voce);
        else Voce.prepara(this, voce);
        if (inCoda == 0) volumePrima = Voce.alza(this, audio);
        if (audio != null) audio.requestAudioFocus(richiestaAudio);
        inCoda++;
        voce.speak(frase, TextToSpeech.QUEUE_ADD, null, "chisi" + System.nanoTime());
    }

    // ---------- Lettura della notifica ----------

    static class Mittente {
        String nome;
        String gruppo;
        boolean vocale;
    }

    private Mittente leggiMittente(Bundle ex) {
        Mittente m = new Mittente();
        String titolo = testo(ex.getCharSequence(Notification.EXTRA_TITLE));
        String conversazione = testo(ex.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE));
        boolean eGruppo = ex.getBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION, false);
        String contenuto = testo(ex.getCharSequence(Notification.EXTRA_TEXT));

        Parcelable[] messaggi = ex.getParcelableArray(Notification.EXTRA_MESSAGES);
        if (messaggi != null && messaggi.length > 0 && messaggi[messaggi.length - 1] instanceof Bundle) {
            Bundle ultimo = (Bundle) messaggi[messaggi.length - 1];
            m.nome = testo(ultimo.getCharSequence("sender"));
            String t = testo(ultimo.getCharSequence("text"));
            if (t != null) contenuto = t;
        }
        // Una tua risposta dalla notifica non va annunciata
        if ("Tu".equalsIgnoreCase(m.nome) || "You".equalsIgnoreCase(m.nome)) return null;

        if (eGruppo && conversazione != null) {
            m.gruppo = pulisci(conversazione);
        } else if (m.nome == null && titolo != null && titolo.indexOf(": ") > 0) {
            // Vecchio formato per i gruppi: "Gruppo: Mittente"
            int p = titolo.indexOf(": ");
            m.gruppo = pulisci(titolo.substring(0, p));
            m.nome = titolo.substring(p + 2);
        }
        if (m.nome == null) m.nome = titolo;
        if (m.nome != null) m.nome = pulisci(m.nome);

        if (m.nome == null || m.nome.isEmpty()) return null;
        if (m.nome.equalsIgnoreCase("WhatsApp") || m.nome.equalsIgnoreCase("WhatsApp Business")) return null;

        m.vocale = eVocale(contenuto);
        return m;
    }

    /** Riconosce i vocali senza leggere il testo ad alta voce. */
    private boolean eVocale(String t) {
        if (t == null) return false;
        String s = t.toLowerCase(Locale.ROOT);
        return t.contains("🎤")          // 🎤
                || s.contains("messaggio vocale")
                || s.contains("voice message")
                || s.startsWith("audio");
    }

    private String pulisci(String s) {
        return s.replaceAll("\\s*\\(\\d+[^)]*\\)\\s*$", "").trim();
    }

    private String testo(CharSequence c) {
        if (c == null) return null;
        String s = c.toString().trim();
        return s.isEmpty() ? null : s;
    }
}

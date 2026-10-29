package it.nunzia.chisi;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.os.Bundle;
import android.provider.Settings;
import android.speech.tts.TextToSpeech;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {

    private static final int COLORE_SEZIONE = 0xFF6A1B9A;

    private SharedPreferences pref;
    private TextView stato;
    private TextToSpeech prova;
    private LinearLayout box;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        pref = Impostazioni.pref(this);

        box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(32), dp(20), dp(40));

        // ---------- Intestazione ----------
        TextView titolo = testo("ChiSì", 34);
        titolo.setTypeface(Typeface.DEFAULT_BOLD);
        titolo.setTextColor(COLORE_SEZIONE);
        box.addView(titolo);
        TextView motto = testo("Chi si'? Te lo dico io chi ti scrive su WhatsApp.", 17);
        motto.setPadding(0, 0, 0, dp(16));
        box.addView(motto);

        stato = testo("", 18);
        stato.setTypeface(Typeface.DEFAULT_BOLD);
        box.addView(stato);
        box.addView(bottone("Concedi l'accesso alle notifiche",
                v -> startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))));
        box.addView(interruttore("ChiSì acceso", Impostazioni.ATTIVA, true));

        // ---------- Frasi ----------
        sezione("Le tue frasi");
        box.addView(nota("Scrivi una frase per riga: ChiSì ne sceglie una a caso. "
                + "Al posto di {nome} dirà chi ti scrive, al posto di {gruppo} il nome del gruppo."));

        campoFrase("Messaggio scritto", Impostazioni.FRASE_MESSAGGIO, Impostazioni.DEF_MESSAGGIO,
                Impostazioni.IDEE_MESSAGGIO, false);
        campoFrase("Messaggio vocale", Impostazioni.FRASE_VOCALE, Impostazioni.DEF_VOCALE,
                Impostazioni.IDEE_VOCALE, false);
        campoFrase("Messaggio in un gruppo", Impostazioni.FRASE_GRUPPO, Impostazioni.DEF_GRUPPO,
                Impostazioni.IDEE_GRUPPO, true);

        // ---------- Persone ----------
        sezione("Persone e gruppi");
        box.addView(nota("Dai una frase speciale a una persona (\"È arrivato Ciro!\") "
                + "oppure falla stare zitta. I nomi compaiono qui man mano che ti scrivono."));
        box.addView(bottone("Scegli una persona o un gruppo", v -> mostraElencoPersone()));
        box.addView(bottone("Aggiungi un nome a mano", v -> aggiungiAMano()));

        // ---------- Orari di pace ----------
        sezione("Orari di pace");
        box.addView(nota("In questi orari ChiSì non parla."));
        box.addView(interruttore("Orari di pace attivi", Impostazioni.PACE_ATTIVA, false));
        LinearLayout riga = new LinearLayout(this);
        riga.setOrientation(LinearLayout.HORIZONTAL);
        Button inizio = bottone("", null);
        Button fine = bottone("", null);
        aggiornaOrari(inizio, fine);
        inizio.setOnClickListener(v -> scegliOra(Impostazioni.PACE_INIZIO, 22 * 60, inizio, fine));
        fine.setOnClickListener(v -> scegliOra(Impostazioni.PACE_FINE, 7 * 60 + 30, inizio, fine));
        riga.addView(inizio, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        riga.addView(spazio());
        riga.addView(fine, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        box.addView(riga);

        // ---------- Altro ----------
        sezione("Altre opzioni");
        box.addView(interruttore("Parla anche col telefono in silenzioso", Impostazioni.ANCHE_SILENZIOSO, false));
        box.addView(nota("✔ Durante le telefonate ChiSì sta zitta.\n"
                + "✔ Se ascolti musica, la abbassa mentre parla.\n"
                + "✔ Il testo dei messaggi non viene mai letto: niente codici ad alta voce."));
        box.addView(bottone("Togli il suono di WhatsApp", v -> {
            Intent i = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
            i.putExtra(Settings.EXTRA_APP_PACKAGE, "com.whatsapp");
            try { startActivity(i); } catch (Exception e) { /* WhatsApp non installato */ }
        }));
        box.addView(nota("Così senti solo la voce di ChiSì. In alternativa, in WhatsApp: "
                + "Impostazioni > Notifiche > Suono notifiche > Nessuno."));

        ScrollView sv = new ScrollView(this);
        sv.addView(box);
        setContentView(sv);

        prova = new TextToSpeech(this, s -> {
            if (s == TextToSpeech.SUCCESS) prova.setLanguage(Locale.ITALIAN);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (accessoConcesso()) {
            stato.setText("✔ Accesso alle notifiche concesso");
            stato.setTextColor(0xFF1B7F2A);
        } else {
            stato.setText("✘ Prima tocca il pulsante qui sotto e attiva ChiSì");
            stato.setTextColor(0xFFB3261E);
        }
    }

    @Override
    protected void onDestroy() {
        if (prova != null) prova.shutdown();
        super.onDestroy();
    }

    // ---------- Frasi ----------

    private void campoFrase(String etichetta, String chiave, String predefinita, String[] idee, boolean conGruppo) {
        TextView t = testo(etichetta, 18);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(0, dp(14), 0, dp(4));
        box.addView(t);

        EditText campo = new EditText(this);
        campo.setTextSize(18);
        campo.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        campo.setMinLines(2);
        campo.setText(Impostazioni.frase(this, chiave, predefinita));
        campo.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            public void onTextChanged(CharSequence s, int a, int b, int c) { }
            public void afterTextChanged(Editable s) { pref.edit().putString(chiave, s.toString()).apply(); }
        });
        box.addView(campo);

        LinearLayout riga = new LinearLayout(this);
        riga.setOrientation(LinearLayout.HORIZONTAL);
        riga.addView(piccolo("+ nome", v -> inserisci(campo, "{nome}")));
        if (conGruppo) riga.addView(piccolo("+ gruppo", v -> inserisci(campo, "{gruppo}")));
        riga.addView(piccolo("Idee", v -> mostraIdee(campo, idee)));
        riga.addView(piccolo("▶ Prova", v -> {
            String f = Impostazioni.componi(campo.getText().toString(), "Peppe", "Famiglia");
            parla(f);
        }));
        box.addView(riga);
    }

    private void inserisci(EditText campo, String pezzo) {
        int pos = Math.max(campo.getSelectionStart(), 0);
        campo.getText().insert(pos, pezzo);
    }

    private void mostraIdee(EditText campo, String[] idee) {
        String[] leggibili = new String[idee.length];
        for (int i = 0; i < idee.length; i++) {
            leggibili[i] = idee[i].replace("{nome}", "Peppe").replace("{gruppo}", "Famiglia");
        }
        new AlertDialog.Builder(this)
                .setTitle("Tocca un'idea per aggiungerla")
                .setItems(leggibili, (d, i) -> {
                    String attuale = campo.getText().toString().trim();
                    campo.setText(attuale.isEmpty() ? idee[i] : attuale + "\n" + idee[i]);
                    parla(leggibili[i]);
                })
                .setNegativeButton("Chiudi", null)
                .show();
    }

    // ---------- Persone e gruppi ----------

    private void mostraElencoPersone() {
        List<String> nomi = Impostazioni.elencoPersone(this);
        if (nomi.isEmpty()) {
            new AlertDialog.Builder(this)
                    .setMessage("Ancora nessun nome. Appena qualcuno ti scrive su WhatsApp, comparirà qui. "
                            + "Puoi anche usare \"Aggiungi un nome a mano\".")
                    .setPositiveButton("OK", null).show();
            return;
        }
        String[] voci = new String[nomi.size()];
        for (int i = 0; i < voci.length; i++) {
            String n = nomi.get(i);
            String extra = "";
            if (Impostazioni.silenziato(this, n)) extra = "   🔇 zitto";
            else if (Impostazioni.frasePersona(this, n) != null) extra = "   ★ frase sua";
            voci[i] = n + extra;
        }
        new AlertDialog.Builder(this)
                .setTitle("Chi vuoi personalizzare?")
                .setItems(voci, (d, i) -> modificaPersona(nomi.get(i)))
                .setNegativeButton("Chiudi", null)
                .show();
    }

    private void aggiungiAMano() {
        EditText nome = new EditText(this);
        nome.setTextSize(18);
        nome.setHint("Nome come appare su WhatsApp");
        LinearLayout c = contenitore();
        c.addView(nome);
        CheckBox gruppo = new CheckBox(this);
        gruppo.setText("È un gruppo");
        gruppo.setTextSize(17);
        c.addView(gruppo);
        new AlertDialog.Builder(this)
                .setTitle("Aggiungi un nome")
                .setView(c)
                .setPositiveButton("Avanti", (d, w) -> {
                    String n = nome.getText().toString().trim();
                    if (n.isEmpty()) return;
                    modificaPersona(gruppo.isChecked() ? Impostazioni.PREFISSO_GRUPPO + n : n);
                })
                .setNegativeButton("Annulla", null)
                .show();
    }

    private void modificaPersona(String chiave) {
        boolean eGruppo = chiave.startsWith(Impostazioni.PREFISSO_GRUPPO);
        String nome = eGruppo ? chiave.substring(Impostazioni.PREFISSO_GRUPPO.length()) : chiave;

        LinearLayout c = contenitore();
        c.addView(testo(eGruppo
                ? "Frase speciale per questo gruppo (una per riga). Vuota = frase normale."
                : "Frase speciale per " + nome + " (una per riga). Vuota = frase normale.", 16));
        EditText frase = new EditText(this);
        frase.setTextSize(18);
        frase.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        frase.setMinLines(2);
        String attuale = Impostazioni.frasePersona(this, chiave);
        frase.setText(attuale == null ? "" : attuale);
        frase.setHint(eGruppo ? "Es: Novità nel gruppo {gruppo}!" : "Es: È arrivato " + nome + "!");
        c.addView(frase);

        LinearLayout riga = new LinearLayout(this);
        riga.addView(piccolo("+ nome", v -> inserisci(frase, "{nome}")));
        if (eGruppo) riga.addView(piccolo("+ gruppo", v -> inserisci(frase, "{gruppo}")));
        riga.addView(piccolo("▶ Prova", v -> parla(Impostazioni.componi(frase.getText().toString(),
                eGruppo ? "Peppe" : nome, eGruppo ? nome : null))));
        c.addView(riga);

        CheckBox zitto = new CheckBox(this);
        zitto.setText(eGruppo ? "Non annunciare mai questo gruppo" : "Non annunciare mai " + nome);
        zitto.setTextSize(17);
        zitto.setChecked(Impostazioni.silenziato(this, chiave));
        zitto.setPadding(0, dp(10), 0, 0);
        c.addView(zitto);

        ScrollView sv = new ScrollView(this);
        sv.addView(c);
        new AlertDialog.Builder(this)
                .setTitle(eGruppo ? "Gruppo " + nome : nome)
                .setView(sv)
                .setPositiveButton("Salva", (d, w) ->
                        Impostazioni.salvaPersona(this, chiave, frase.getText().toString(), zitto.isChecked()))
                .setNeutralButton("Azzera", (d, w) ->
                        Impostazioni.salvaPersona(this, chiave, "", false))
                .setNegativeButton("Annulla", null)
                .show();
    }

    // ---------- Orari ----------

    private void scegliOra(String chiave, int predefinito, Button inizio, Button fine) {
        int m = pref.getInt(chiave, predefinito);
        new TimePickerDialog(this, (v, ore, minuti) -> {
            pref.edit().putInt(chiave, ore * 60 + minuti).apply();
            aggiornaOrari(inizio, fine);
        }, m / 60, m % 60, true).show();
    }

    private void aggiornaOrari(Button inizio, Button fine) {
        inizio.setText("Dalle " + Impostazioni.orario(pref.getInt(Impostazioni.PACE_INIZIO, 22 * 60)));
        fine.setText("Alle " + Impostazioni.orario(pref.getInt(Impostazioni.PACE_FINE, 7 * 60 + 30)));
    }

    // ---------- Aiutanti grafici ----------

    private void parla(String f) {
        if (prova != null && f != null) prova.speak(f, TextToSpeech.QUEUE_FLUSH, null, "prova");
    }

    private boolean accessoConcesso() {
        String elenco = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        String mio = new ComponentName(this, AscoltaNotifiche.class).flattenToString();
        return elenco != null && elenco.contains(mio);
    }

    private void sezione(String t) {
        TextView v = testo(t, 22);
        v.setTypeface(Typeface.DEFAULT_BOLD);
        v.setTextColor(COLORE_SEZIONE);
        v.setPadding(0, dp(28), 0, dp(4));
        box.addView(v);
    }

    private TextView testo(String t, int size) {
        TextView v = new TextView(this);
        v.setText(t);
        v.setTextSize(size);
        return v;
    }

    private TextView nota(String t) {
        TextView v = testo(t, 16);
        v.setPadding(0, dp(4), 0, dp(4));
        return v;
    }

    private Button bottone(String t, View.OnClickListener azione) {
        Button btn = new Button(this);
        btn.setText(t);
        btn.setTextSize(18);
        btn.setAllCaps(false);
        btn.setMinHeight(dp(56));
        if (azione != null) btn.setOnClickListener(azione);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(8);
        btn.setLayoutParams(lp);
        return btn;
    }

    private Button piccolo(String t, View.OnClickListener azione) {
        Button btn = new Button(this);
        btn.setText(t);
        btn.setTextSize(16);
        btn.setAllCaps(false);
        btn.setOnClickListener(azione);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        lp.rightMargin = dp(4);
        btn.setLayoutParams(lp);
        return btn;
    }

    private Switch interruttore(String t, String chiave, boolean predefinito) {
        Switch s = new Switch(this);
        s.setText(t);
        s.setTextSize(18);
        s.setChecked(pref.getBoolean(chiave, predefinito));
        s.setGravity(Gravity.CENTER_VERTICAL);
        s.setPadding(0, dp(14), 0, dp(14));
        s.setOnCheckedChangeListener((v, on) -> pref.edit().putBoolean(chiave, on).apply());
        return s;
    }

    private View spazio() {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(dp(10), 1));
        return v;
    }

    private LinearLayout contenitore() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(22), dp(10), dp(22), 0);
        return c;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}

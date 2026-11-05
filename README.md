# OrbitaNotify

App Android personale che inoltra le notifiche del telefono a **Orbita** (registrazione spese),
per **mail**, **Telegram** o **ntfy**. Si possono usare più canali insieme.

Segue il tema chiaro/scuro del telefono (su Android 12+ anche i colori dello sfondo).
Cinque schede in basso, così non serve scorrere.

## Indice

- [Schede dell'app](#schede-dellapp)
- [Regole](#regole)
- [Orbita](#orbita)
- [Notifiche duplicate e messaggi persi](#notifiche-duplicate-e-messaggi-persi)
- [Mail, Telegram, ntfy](#mail-telegram-ntfy)
- [Problemi comuni](#problemi-comuni)
- [Note](#note)

## Schede dell'app

- **Stato**: interruttore generale, accesso alle notifiche, risparmio batteria, invio di prova.
- **Canali**: Mail (SMTP), Telegram, ntfy, Orbita (JSON).
- **Regole**: quali notifiche inoltrare (vedi sotto).
- **Messaggio**: modello di oggetto e corpo, formato HTML, ignora notifiche fisse.
- **Cronologia**: ultimi invii con esito ed eventuali errori.

I campi di Canali e Messaggio si salvano da soli quando cambi scheda o esci dall'app.
Le regole si salvano con **Salva** in fondo alla schermata della regola.

## Regole

Senza regole non viene inoltrato nulla. Ogni regola ha:

- **App**: una app o "Tutte le app". Le regole di una app specifica vengono provate prima.
- **Parole**: "inoltra solo se contiene" e "ignora se contiene", separate da virgola. Il confronto cerca
  il testo come sottostringa, senza distinguere maiuscole: `pagata` **non** trova "Pagato".
  Per coprire le varianti scrivi la radice (`pagat`) oppure più parole separate da virgola.
- **Destinatari**: solo per questa regola (vuoto = quelli del canale Mail).
- **Riepilogo**: 0 = un invio per notifica; N = un unico invio con tutte le notifiche raccolte ogni N minuti.
- **Giorni e fascia oraria**: fuori da questi orari le notifiche vengono ignorate (non accodate).
- **Conto, metodo di pagamento, tag** e **registrazione automatica**: per Orbita (vedi sotto).

Vale la prima regola che corrisponde. Le impostazioni della versione 1 (app scelte e filtri) vengono
convertite in regole al primo avvio, se l'app viene aggiornata senza disinstallare.

## Orbita

Il canale **Orbita (JSON)** fa una POST all'indirizzo che inserisci (es. `https://tuo-ddns.it:8080/api/notifiche`).
Se Orbita risponde con un codice 2xx l'invio è riuscito. Il token, se impostato, viene mandato come
`Authorization: Bearer <token>`. Meglio usare https.

Con l'interruttore **Mail solo se Orbita non risponde** (attivo di default) la mail parte soltanto
quando Orbita non è raggiungibile o risponde con un errore. Se Orbita non è raggiungibile ma la mail
parte, l'errore di Orbita compare in Cronologia come già compensato.

### Conto, metodo e tag per regola

Ogni regola può avere un **conto**, un **metodo di pagamento** e un **tag**, inviati a Orbita insieme
alle notifiche. Le tendine si riempiono con l'elenco preso da Orbita
(`GET <indirizzo>/opzioni`, con lo stesso token) e restano salvate per l'uso senza rete; si può
scrivere anche a mano. Finché non si scarica l'elenco, i metodi propongono quelli standard
(Contanti, Bonifico, PayPal, Satispay…).

Il nome del conto deve coincidere con quello di Orbita (maiuscole escluse); lo stesso vale per il metodo.
Il tag è uno solo per regola; il simbolo `#` e gli spazi vengono tolti.

### Registrazione automatica

Ogni regola ha l'opzione **Registra la spesa in automatico**:

- **No**: la spesa resta in «Da confermare».
- **Solo se riconosciuta con le regole fisse**: Orbita registra da sola quando ricava categoria e importo con le sue regole.
- **Sempre**: registra anche quando Orbita ricorre a Gemini.

Orbita registra da sola solo se conosce già la categoria: se il testo non contiene il nome di una categoria
esistente (parola intera), la proposta resta «Da confermare» con categoria «Generica». In quel caso conto,
metodo e tag possono comunque arrivare compilati; se risultano vuoti, controlla la regola nell'app e la versione installata.

### Formato del JSON

```json
{
  "source": "notif2mail",
  "version": 1,
  "sent_at": 1762341600000,
  "rule": "WhatsApp",
  "digest": false,
  "conto": "Conto principale",
  "metodo": "Bonifico",
  "auto": "regole",
  "tags": ["casa"],
  "items": [
    {
      "ts": 1762341590000,
      "app": "WhatsApp",
      "pkg": "com.whatsapp",
      "title": "Nome contatto",
      "text": "Pagata bolletta Enel 120 €"
    }
  ]
}
```

`auto` vale `""`, `"regole"` oppure `"tutto"`. Con il riepilogo attivo `digest` è `true` e `items` contiene
più notifiche.

## Notifiche duplicate e messaggi persi

L'app scarta una notifica **solo** se ha lo stesso titolo e lo stesso testo dell'ultima della stessa
conversazione **ed è arrivata entro 30 secondi**. Serve a ignorare gli aggiornamenti ripetuti che Android
fa della stessa notifica. Oltre i 30 secondi lo stesso testo passa di nuovo, quindi due pagamenti identici
(due caffè da 1,50 € nello stesso bar) vengono registrati entrambi.

Altri casi in cui una notifica non arriva:

- **WhatsApp raggruppa i messaggi.** Quando in una chat ne arrivano più d'uno ravvicinati, può mostrare
  una notifica riassuntiva ("3 nuovi messaggi") al posto del testo. L'app salta le notifiche riassuntive
  di proposito, quindi i messaggi intermedi possono non essere visti.
- **Il filtro "Contiene" non corrisponde.** Vedi la sezione Regole.
- **Fuori da giorni o fascia oraria** della regola.
- **Notifiche fisse** (es. musica in riproduzione): vengono ignorate se l'opzione è attiva in Messaggio.

Per capire dove si ferma un messaggio, confronta la **Cronologia dell'app** con la scheda **Notifiche di Orbita**:

- manca già nella Cronologia → il blocco è nell'app, in Android o in WhatsApp;
- c'è nella Cronologia ma non in Orbita → il blocco è lato Orbita.

## Mail, Telegram, ntfy

### Parametri SMTP più comuni

| Provider | Server | Porta | Sicurezza |
|----------|--------|-------|-----------|
| Gmail | smtp.gmail.com | 465 | SSL |
| Gmail (alt.) | smtp.gmail.com | 587 | STARTTLS |
| Outlook / Hotmail | smtp-mail.outlook.com | 587 | STARTTLS |
| iCloud | smtp.mail.me.com | 587 | STARTTLS |

**Gmail**: serve la verifica in due passaggi e una *password per le app*
(https://myaccount.google.com/apppasswords). La password normale dà errore 534.

### Telegram

1. Su Telegram scrivi a **@BotFather**, crea un bot e copia il token.
2. Scrivi un messaggio al tuo nuovo bot.
3. Ricava il tuo Chat ID (ad esempio con **@userinfobot**) e inseriscilo insieme al token.

### ntfy

Installa l'app **ntfy** sull'altro dispositivo e iscriviti a un argomento. Inserisci lo stesso
argomento nell'app. Su ntfy.sh chiunque conosca il nome può leggere i messaggi: scegline uno lungo
e difficile da indovinare, oppure usa un server tuo con token.

### Modello del messaggio

Oggetto e corpo usano i segnaposto `{app}`, `{title}`, `{text}`, `{pkg}`, `{date}`.
Predefiniti: oggetto `[{app}] {title}`, corpo con app, data, titolo e testo.

## Problemi comuni

| Sintomo | Cosa controllare |
|---------|------------------|
| Non parte niente | Interruttore generale su Stato, accesso alle notifiche concesso, almeno un canale attivo, almeno una regola |
| La spesa resta in «Da confermare» | Categoria non trovata nel testo; con «Registra in automatico» su No non registra mai |
| Conto, metodo o tag vuoti in Orbita | Sono scelti e salvati nella regola? Premi **Salva**. |
| Un messaggio identico al precedente non compare | È arrivato entro 30 secondi dal precedente: è il filtro antiduplicati |

## Note

- Password e token sono salvati cifrati sul telefono.
- Le notifiche in attesa di invio restano in un database interno finché non partono; poi vengono
  cancellate. La cronologia conserva solo gli oggetti degli invii (ultimi 200).
- Se manca la rete l'invio viene ritentato da solo (fino a 6 volte); con il riepilogo, l'invio
  parte ogni N minuti circa (Android può ritardare un po' per risparmiare batteria).
- Se almeno un canale riesce, l'invio è considerato riuscito; gli errori degli altri canali
  compaiono nella Cronologia.

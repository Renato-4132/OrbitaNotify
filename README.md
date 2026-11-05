# OrbitaNotify

App Android personale che inoltra le notifiche del telefono a **Orbita** (registrazione spese),
per **mail**, **Telegram** o **ntfy**. Si possono usare più canali insieme.

Segue il tema chiaro/scuro del telefono (su Android 12+ anche i colori dello sfondo).
Cinque schede in basso, così non serve scorrere.

## Indice

- [Schede dell'app](#schede-dellapp)
- [Collegare l'app a Orbita](#collegare-lapp-a-orbita)
- [Regole](#regole)
- [Conto, metodo, tag e registrazione automatica](#conto-metodo-tag-e-registrazione-automatica)
- [Notifiche duplicate e messaggi persi](#notifiche-duplicate-e-messaggi-persi)
- [Mail, Telegram, ntfy](#mail-telegram-ntfy)
- [Problemi comuni](#problemi-comuni)
- [Note](#note)

## Schede dell'app

Le schede in basso sono cinque:

- **Stato**: interruttore generale, accesso alle notifiche, risparmio batteria, **Invia notifica di prova**.
- **Canali**: in alto ci sono quattro sotto-schede, **Mail**, **Telegram**, **ntfy** e **Orbita**.
  Ognuna ha il proprio interruttore. I dati di Orbita si inseriscono nella sotto-scheda **Orbita**.
- **Regole**: quali notifiche inoltrare (vedi sotto).
- **Messaggio**: modello di oggetto e corpo, formato HTML, ignora notifiche fisse.
- **Cronologia**: ultimi invii con esito ed eventuali errori.

I campi di Canali e Messaggio si salvano da soli quando cambi scheda o esci dall'app.
Le regole si salvano con **Salva** in fondo alla schermata della regola.

## Collegare l'app a Orbita

1. Apri la scheda **Canali** e tocca la sotto-scheda **Orbita** (la quarta, in alto).
2. Attiva **Invia a Orbita**.
3. In **Indirizzo** scrivi l'indirizzo di Orbita, ad esempio `https://tuo-ddns.it:8080/api/notifiche`.
4. In **Token segreto** scrivi il token, se in Orbita ne hai impostato uno. Altrimenti lascia vuoto.
5. Vai su **Stato**, tocca **Invia notifica di prova** e controlla in **Cronologia** che l'esito sia positivo.

Meglio usare `https`.

**Mail solo se Orbita non risponde** (nella stessa sotto-scheda, attivo di default): la mail parte
soltanto quando Orbita non è raggiungibile o risponde con un errore. In quel caso l'errore di Orbita
compare in Cronologia come già compensato dalla mail.

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

## Conto, metodo, tag e registrazione automatica

Ogni regola può avere un **conto**, un **metodo di pagamento** e un **tag**, inviati a Orbita insieme
alle notifiche. Le tendine si riempiono con l'elenco preso da Orbita (stesso indirizzo e stesso token
della sotto-scheda Orbita) e restano salvate per l'uso senza rete; si può scrivere anche a mano.
Finché non si scarica l'elenco, i metodi propongono quelli standard (Contanti, Bonifico, PayPal, Satispay…).

Il nome del conto deve coincidere con quello di Orbita (maiuscole escluse); lo stesso vale per il metodo.
Il tag è uno solo per regola; il simbolo `#` e gli spazi vengono tolti.

**Registra la spesa in automatico**

Prima Orbita deve riconoscere la notifica come un pagamento o un incasso. Lo fa in due modi:

- **Con le regole fisse**: nel testo ci sono un importo con `€` o `EUR` (gli importi preceduti da "saldo",
  "disponibile", "residuo" ecc. vengono ignorati) e una parola tipo *pagato, addebito, acquisto, carta,
  ricevuto, accredito, stipendio, rimborso*.
- **Con Gemini**: solo se le regole fisse non bastano, il testo contiene `€` o `EUR` e in Orbita è
  configurata la chiave di Gemini. Senza chiave questa strada non esiste.

Se non la riconosce in nessuno dei due modi, la notifica non genera nessuna proposta in «Da confermare».

Poi l'opzione della regola decide cosa succede:

- **No**: la spesa resta in «Da confermare» e la confermi tu.
- **Solo se riconosciuta con le regole fisse**: registra da sola solo quando il riconoscimento è avvenuto con le regole fisse.
- **Sempre**: registra da sola anche quando il riconoscimento è avvenuto con Gemini.

**Quando la registrazione automatica non scatta.** Anche con l'opzione attiva, Orbita registra solo se
trova una categoria. La cerca in quest'ordine:

1. la categoria già usata in passato per la stessa descrizione;
2. il nome di una categoria esistente scritto nel testo (parola intera);
3. la categoria ricordata per quell'app, cioè quella dell'ultima spesa registrata o confermata da quell'app.

Se non ne trova nessuna (o trova solo «Generica»), la spesa resta in «Da confermare». Dopo che ne confermi
una a mano, per quell'app le successive possono andare in automatico.

Altri casi:

- se il **controllo doppioni** di Orbita è attivo e nella stessa data c'è già una spesa con stessa
  descrizione e stesso importo, la nuova viene scartata e non registrata;
- **conto** e **metodo** vengono usati solo se coincidono con quelli che esistono in Orbita
  (maiuscole escluse); altrimenti restano vuoti, o vale l'ultimo ricordato per quell'app.

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
| Non trovo dove inserire i dati di Orbita | Canali → sotto-scheda **Orbita** (in alto, la quarta) |
| La spesa resta in «Da confermare» | Registrazione automatica su No; oppure Orbita non trova la categoria (vedi «Quando la registrazione automatica non scatta»); oppure è un doppione |
| La notifica non compare nemmeno in «Da confermare» | Orbita non l'ha riconosciuta come pagamento: serve `€`/`EUR` più una parola come *pagato* o *addebito* (o la chiave Gemini in Orbita) |
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

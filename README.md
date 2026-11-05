# OrbitaNotify 2.0

App Android personale che inoltra le notifiche per mail, Telegram o ntfy.
Segue il tema chiaro/scuro del telefono (su Android 12+ anche i colori dello sfondo).
Cinque schede in basso, così non serve scorrere.

## Compilare

Con GitHub Actions (il file `.github/workflows/build.yml` è già incluso):
carica il progetto nel repository, apri **Actions**, scarica `OrbitaNotify-debug` dagli Artifacts.

## ATTENZIONE: primo aggiornamento dalla versione 1

La versione 2 è firmata con una chiave fissa (custodita nei secrets di GitHub, non nel repository), diversa da quella
casuale che GitHub aveva usato per la versione 1. Per questo **la prima volta Android rifiuta
l'aggiornamento**: disinstalla la vecchia app e installa la nuova. Dovrai reinserire le
impostazioni (server, password). Dagli aggiornamenti successivi in poi si installa sopra
senza perdere nulla.

## Schede

- **Stato**: interruttore generale, accesso alle notifiche, risparmio batteria, invio di prova.
- **Canali**: Mail (SMTP), Telegram, ntfy. Si possono attivare più canali insieme.
- **Regole**: quali notifiche inoltrare (vedi sotto).
- **Messaggio**: modello di oggetto e corpo, formato HTML, ignora notifiche fisse.
- **Cronologia**: ultimi invii con esito ed eventuali errori.

I campi di Canali e Messaggio si salvano da soli quando cambi scheda o esci dall'app.

## Regole

Senza regole non viene inoltrato nulla. Ogni regola ha:
- **App**: una app o "Tutte le app". Le regole di una app specifica vengono provate prima.
- **Parole**: "inoltra solo se contiene" e "ignora se contiene" (separate da virgola).
- **Destinatari**: solo per questa regola (vuoto = quelli del canale Mail).
- **Riepilogo**: 0 = una mail per notifica; N = un'unica mail con tutte le notifiche raccolte ogni N minuti.
- **Giorni e fascia oraria**: fuori da questi orari le notifiche vengono ignorate (non accodate).

Le impostazioni della versione 1 (app scelte e filtri) vengono convertite in regole al primo avvio,
se l'app viene aggiornata senza disinstallare.

## Parametri SMTP più comuni

| Provider | Server | Porta | Sicurezza |
|----------|--------|-------|-----------|
| Gmail | smtp.gmail.com | 465 | SSL |
| Gmail (alt.) | smtp.gmail.com | 587 | STARTTLS |
| Outlook / Hotmail | smtp-mail.outlook.com | 587 | STARTTLS |
| iCloud | smtp.mail.me.com | 587 | STARTTLS |

**Gmail**: serve la verifica in due passaggi e una *password per le app*
(https://myaccount.google.com/apppasswords). La password normale dà errore 534.

## Telegram

1. Su Telegram scrivi a **@BotFather**, crea un bot e copia il token.
2. Scrivi un messaggio al tuo nuovo bot.
3. Ricava il tuo Chat ID (ad esempio con **@userinfobot**) e inseriscilo insieme al token.

## ntfy

Installa l'app **ntfy** sull'altro dispositivo e iscriviti a un argomento. Inserisci lo stesso
argomento nell'app. Su ntfy.sh chiunque conosca il nome può leggere i messaggi: scegline uno lungo
e difficile da indovinare, oppure usa un server tuo con token.

## Note

- Password e token sono salvati cifrati sul telefono.
- Le notifiche in attesa di invio restano in un database interno finché non partono; poi vengono
  cancellate. La cronologia conserva solo gli oggetti delle mail (ultime 200).
- Se manca la rete l'invio viene ritentato da solo (fino a 6 volte); con il riepilogo, la mail
  parte ogni N minuti circa (Android può ritardare un po' per risparmiare batteria).
- Se almeno un canale riesce, l'invio è considerato riuscito; gli errori degli altri canali
  compaiono nella Cronologia.

## Conto, metodo e tag per regola (Orbita)

Ogni regola può avere un **conto**, un **metodo di pagamento** e un **tag**, inviati a Orbita insieme alle notifiche.
Le tendine si riempiono con l'elenco preso da Orbita (`GET <indirizzo webhook>/opzioni`, con lo stesso
token) e restano salvate per l'uso senza rete; si può scrivere anche a mano. Il nome del conto deve
coincidere con quello di Orbita (maiuscole escluse); lo stesso vale per il metodo (Contanti, Bonifico, PayPal…).

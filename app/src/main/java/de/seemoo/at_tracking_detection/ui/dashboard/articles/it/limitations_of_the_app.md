# Limitazioni di questa app

AirGuard può trovare solo tracker basati su Bluetooth. I tracker che funzionano con il GPS e condividono la posizione con il proprietario tramite una connessione cellulare non possono essere trovati.

Sebbene AirGuard possa rilevare un'ampia varietà di tracker, le funzionalità avanzate come la riproduzione di un suono, l'identificazione del proprietario o la determinazione se il dispositivo è attualmente connesso al suo proprietario variano in base al produttore.

**Dispositivi Google Trova il mio dispositivo**
Questi sono tracker compatibili con la rete Trova il mio dispositivo di Google.
* **È connesso al proprietario:** ✅ (Determina se il proprietario è nelle vicinanze)
* **Riproduci suono:** ✅
* **Identifica proprietario:** ✅

**Apple AirTag & Dispositivi Dov'è (Find My)**
Questo include AirTag, AirPods e tracker di terze parti (come Chipolo o Pebblebee) che utilizzano la rete Apple Dov'è.
* **È connesso al proprietario:** ✅ (Determina se il proprietario è nelle vicinanze)
* **Riproduci suono:** ✅ (La disponibilità dipende dalla modalità specifica del dispositivo)
* **Identifica proprietario:** ✅ (Via NFC per AirTag e dispositivi Dov'è supportati)

**Samsung SmartTag**
Questi sono tracker prodotti da Samsung.
* **È connesso al proprietario:** ✅ (Determina se il proprietario è nelle vicinanze)
* **Riproduci suono:** ❌
* **Identifica proprietario:** ❌

**Tile Tracker**
Tile è un'azienda che produce esclusivamente tracker Bluetooth.
* **È connesso al proprietario:** ❌
* **Riproduci suono:** ❌
* **Identifica proprietario:** ❌

**Chipolo Tracker (senza Dov'è)**
Questi sono i tracker Bluetooth standard di Chipolo che non fanno parte della rete Apple Dov'è.
* **È connesso al proprietario:** ✅ (Supportato su alcuni modelli)
* **Riproduci suono:** ❌
* **Identifica proprietario:** ❌

**Pebblebee (senza Dov'è)**
Questi sono tracker Bluetooth standard di Pebblebee che non fanno parte della rete Apple Dov'è.
* **È connesso al proprietario:** ❌
* **Riproduci suono:** ✅
* **Identifica proprietario:** ❌

### Limitazioni tecniche aggiuntive

**Stato della connessione**
Attualmente non è possibile per tutti i tipi di tracker determinare se sono connessi al loro proprietario. Se un tracker non supporta questa funzione (ad es. Tile), AirGuard non può filtrarlo anche se il proprietario è nelle vicinanze. Pertanto, potresti vedere i tuoi tracker o quelli dei tuoi amici più spesso.

**Identificatori casuali**
Alcuni tracker Bluetooth cambiano regolarmente la loro identità (indirizzo MAC Bluetooth) — a volte più volte al giorno.
AirGuard potrebbe visualizzare lo stesso tracker come più voci diverse nel tempo perché non è sempre possibile far corrispondere queste identità randomizzate. I tracker che non cambiano la loro identificazione (ad es. Tile) vengono rilevati in modo più coerente ma possono comportare una maggiore probabilità di falsi positivi se ti trovi spesso vicino a loro.
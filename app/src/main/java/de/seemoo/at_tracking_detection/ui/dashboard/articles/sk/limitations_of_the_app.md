# Obmedzenia tejto aplikácie

AirGuard dokáže nájsť iba sledovacie zariadenia na báze Bluetooth. Sledovacie zariadenia, ktoré pracujú s GPS a zdieľajú polohu s vlastníkom prostredníctvom mobilného pripojenia, nie je možné nájsť.

Hoci AirGuard dokáže detegovať širokú škálu sledovacích zariadení, pokročilé funkcie, ako je prehranie zvuku, identifikácia vlastníka alebo určenie, či je zariadenie aktuálne pripojené k vlastníkovi, sa líšia podľa výrobcu.

**Zariadenia Google Find My (Nájdi moje zariadenie)**
Ide o sledovacie zariadenia kompatibilné so sieťou Google Find My Device.
* **Je pripojené k vlastníkovi:** ✅ (Určuje, či je vlastník nablízku)
* **Prehrať zvuk:** ✅
* **Identifikovať vlastníka:** ✅

**Apple AirTags a zariadenia Find My (Nájsť)**
Zahŕňa AirTags, AirPods a sledovacie zariadenia tretích strán (napr. Chipolo alebo Pebblebee), ktoré využívajú sieť Apple Find My.
* **Je pripojené k vlastníkovi:** ✅ (Určuje, či je vlastník nablízku)
* **Prehrať zvuk:** ✅ (Dostupnosť závisí od konkrétneho režimu zariadenia)
* **Identifikovať vlastníka:** ✅ (Cez NFC pre AirTags a podporované zariadenia Find My)

**Samsung SmartTags**
Ide o sledovacie zariadenia vyrobené spoločnosťou Samsung.
* **Je pripojené k vlastníkovi:** ✅ (Určuje, či je vlastník nablízku)
* **Prehrať zvuk:** ❌
* **Identifikovať vlastníka:** ❌

**Tile Tracker**
Tile je spoločnosť, ktorá vyrába výhradne Bluetooth sledovacie zariadenia.
* **Je pripojené k vlastníkovi:** ❌
* **Prehrať zvuk:** ❌
* **Identifikovať vlastníka:** ❌

**Chipolo Tracker (bez Find My)**
Ide o štandardné Bluetooth sledovacie zariadenia od Chipolo, ktoré nie sú súčasťou siete Apple Find My.
* **Je pripojené k vlastníkovi:** ✅ (Podporované pri niektorých modeloch)
* **Prehrať zvuk:** ❌
* **Identifikovať vlastníka:** ❌

**Pebblebee (bez Find My)**
Ide o štandardné Bluetooth sledovacie zariadenia od Pebblebee, ktoré nie sú súčasťou siete Apple Find My.
* **Je pripojené k vlastníkovi:** ❌
* **Prehrať zvuk:** ✅
* **Identifikovať vlastníka:** ❌

### Ďalšie technické obmedzenia

**Stav pripojenia**
V súčasnosti nie je pri všetkých typoch sledovacích zariadení možné určiť, či sú pripojené k svojmu vlastníkovi. Ak sledovacie zariadenie túto funkciu nepodporuje (napr. Tile), AirGuard ho nemôže vyfiltrovať, ani keď je vlastník nablízku. Môžete tak častejšie vidieť svoje vlastné sledovacie zariadenia alebo zariadenia priateľov.

**Náhodné identifikátory**
Niektoré Bluetooth sledovacie zariadenia pravidelne menia svoju identitu (MAC adresu Bluetooth) – niekedy aj viackrát denne.
AirGuard môže rovnaké sledovacie zariadenie v priebehu času zobraziť ako viacero rôznych záznamov, pretože nie je vždy možné tieto náhodné identity spárovať. Sledovacie zariadenia, ktoré nemenia svoju identifikáciu (napr. Tile), sú detegované konzistentnejšie, ale môžu viesť k vyššej pravdepodobnosti falošne pozitívnych detekcií, ak ste často v ich blízkosti.
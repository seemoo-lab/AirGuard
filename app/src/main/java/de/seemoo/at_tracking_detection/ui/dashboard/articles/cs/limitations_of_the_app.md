# Omezení této aplikace

AirGuard dokáže najít pouze sledovací zařízení na bázi Bluetooth. Sledovací zařízení, která pracují s GPS a sdílejí polohu s vlastníkem prostřednictvím mobilního připojení, nelze nalézt.

Ačkoli AirGuard dokáže detekovat širokou škálu sledovacích zařízení, pokročilé funkce, jako je přehrání zvuku, identifikace vlastníka nebo určení, zda je zařízení aktuálně připojeno k vlastníkovi, se u jednotlivých výrobců liší.

**Zařízení Google Find My (Najdi moje zařízení)**
Jedná se o sledovací zařízení kompatibilní se sítí Google Find My Device.
* **Je připojeno k vlastníkovi:** ✅ (Určuje, zda je vlastník nablízku)
* **Přehrát zvuk:** ✅
* **Identifikovat vlastníka:** ✅

**Apple AirTags a zařízení Find My (Najít)**
Zahrnuje AirTags, AirPods a sledovací zařízení třetích stran (např. Chipolo nebo Pebblebee), která využívají síť Apple Find My.
* **Je připojeno k vlastníkovi:** ✅ (Určuje, zda je vlastník nablízku)
* **Přehrát zvuk:** ✅ (Dostupnost závisí na konkrétním režimu zařízení)
* **Identifikovat vlastníka:** ✅ (Přes NFC pro AirTags a podporovaná zařízení Find My)

**Samsung SmartTags**
Jedná se o sledovací zařízení vyrobená společností Samsung.
* **Je připojeno k vlastníkovi:** ✅ (Určuje, zda je vlastník nablízku)
* **Přehrát zvuk:** ❌
* **Identifikovat vlastníka:** ❌

**Tile Tracker**
Tile je společnost, která vyrábí výhradně Bluetooth sledovací zařízení.
* **Je připojeno k vlastníkovi:** ❌
* **Přehrát zvuk:** ❌
* **Identifikovat vlastníka:** ❌

**Chipolo Tracker (bez Find My)**
Jedná se o standardní Bluetooth sledovací zařízení od Chipolo, která nejsou součástí sítě Apple Find My.
* **Je připojeno k vlastníkovi:** ✅ (Podporováno u některých modelů)
* **Přehrát zvuk:** ❌
* **Identifikovat vlastníka:** ❌

**Pebblebee (bez Find My)**
Jedná se o standardní Bluetooth sledovací zařízení od Pebblebee, která nejsou součástí sítě Apple Find My.
* **Je připojeno k vlastníkovi:** ❌
* **Přehrát zvuk:** ✅
* **Identifikovat vlastníka:** ❌

### Další technická omezení

**Stav připojení**
V současné době není u všech typů sledovacích zařízení možné určit, zda jsou připojena ke svému vlastníkovi. Pokud sledovací zařízení tuto funkci nepodporuje (např. Tile), AirGuard jej nemůže vyfiltrovat, ani když je vlastník nablízku. Můžete tak častěji vidět svá vlastní sledovací zařízení nebo zařízení přátel.

**Náhodné identifikátory**
Některá Bluetooth sledovací zařízení pravidelně mění svou identitu (MAC adresu Bluetooth) – někdy i vícekrát denně.
AirGuard může stejné sledovací zařízení v průběhu času zobrazit jako několik různých záznamů, protože není vždy možné tyto náhodné identity spárovat. Sledovací zařízení, která nemění svou identifikaci (např. Tile), jsou detekována konzistentněji, ale mohou vést k vyšší pravděpodobnosti falešně pozitivních detekcí, pokud jste často v jejich blízkosti.
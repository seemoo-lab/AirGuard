# Einschränkungen dieser App

AirGuard kann nur Bluetooth-basierte Tracker finden. Tracker, die mit GPS funktionieren und den Standort über eine Mobilfunkverbindung mit dem:der Besitzer:in teilen, können nicht gefunden werden.

Während AirGuard eine große Vielzahl an Trackern erkennen kann, variieren erweiterte Funktionen wie das Abspielen eines Tons, das Identifizieren des:der Besitzer:in oder die Feststellung, ob das Gerät aktuell mit dem:der Besitzer:in verbunden ist, je nach Hersteller.

**Google „Mein Gerät finden“-Geräte**
Dies sind Tracker, die mit dem „Mein Gerät finden“-Netzwerk von Google kompatibel sind.
* **Ist mit Besitzer:in verbunden:** ✅ (Bestimmt, ob der:die Besitzer:in in der Nähe ist)
* **Ton abspielen:** ✅
* **Besitzer:in identifizieren:** ✅

**Apple AirTags & „Wo ist?“-Geräte**
Dazu gehören AirTags, AirPods und Tracker von Drittanbietern (wie Chipolo oder Pebblebee), die das Apple „Wo ist?“-Netzwerk nutzen.
* **Ist mit Besitzer:in verbunden:** ✅ (Bestimmt, ob der:die Besitzer:in in der Nähe ist)
* **Ton abspielen:** ✅ (Verfügbarkeit hängt vom spezifischen Gerätemodus ab)
* **Besitzer:in identifizieren:** ✅ (Via NFC für AirTags und unterstützte „Wo ist?“-Geräte)

**Samsung SmartTags**
Diese Tracker werden von Samsung hergestellt.
* **Ist mit Besitzer:in verbunden:** ✅ (Bestimmt, ob der:die Besitzer:in in der Nähe ist)
* **Ton abspielen:** ❌
* **Besitzer:in identifizieren:** ❌

**Tile-Tracker**
Tile ist eine Firma, die ausschließlich Bluetooth-Tracker herstellt.
* **Ist mit Besitzer:in verbunden:** ❌
* **Ton abspielen:** ❌
* **Besitzer:in identifizieren:** ❌

**Chipolo Tracker (ohne „Wo ist?“-Funktion)**
Dies sind die Standard-Bluetooth-Tracker von Chipolo, die nicht Teil des Apple „Wo ist?“-Netzwerks sind.
* **Ist mit Besitzer:in verbunden:** ✅ (Bei einigen Modellen unterstützt)
* **Ton abspielen:** ❌
* **Besitzer:in identifizieren:** ❌

**Pebblebee (ohne „Wo ist?“-Funktion)**
Dies sind Standard-Bluetooth-Tracker von Pebblebee, die nicht Teil des Apple „Wo ist?“-Netzwerks sind.
* **Ist mit Besitzer:in verbunden:** ❌
* **Ton abspielen:** ✅
* **Besitzer:in identifizieren:** ❌

### Zusätzliche technische Einschränkungen

**Verbindungsstatus**
Beachte, dass es derzeit nicht für alle Tracker-Typen möglich ist, zu bestimmen, ob sie mit ihren Besitzer:innen verbunden sind. Wenn ein Tracker diese Funktion nicht unterstützt (z. B. Tile), kann AirGuard ihn nicht herausfiltern, selbst wenn der:die Besitzer:in in der Nähe ist. Du könntest also auch deine eigenen oder die Tracker von Freunden finden.

**Zufällige Identifikatoren**
Wichtig ist auch, dass manche Tracker ihre Identität (Bluetooth-MAC-Adresse) mehrmals täglich verändern.
AirGuard zeigt denselben Tracker im Laufe der Zeit möglicherweise als mehrere verschiedene Einträge an, da es oft nicht möglich ist, diese verschiedenen, zufällig generierten Identitäten einem Tracker zuzuordnen. Tracker, die ihre Identifikation nicht ändern (z. B. Tile), werden konsistenter erkannt, führen jedoch möglicherweise zu einer höheren Wahrscheinlichkeit von Fehlalarmen, wenn du dich oft in ihrer Nähe befindest.
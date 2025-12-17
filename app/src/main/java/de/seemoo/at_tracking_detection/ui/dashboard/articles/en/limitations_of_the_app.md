# Limitations of This App

AirGuard can only find Bluetooth-based trackers. Trackers that work with GPS and share the location with the owner via a cellular connection cannot be found.

While AirGuard can detect a wide variety of trackers, advanced features such as playing a sound, identifying the owner, or determining if the device is currently connected to its owner vary by manufacturer.

**Google Find My Devices**
These are trackers compatible with Google's Find My Device network.
* **Is Connected with Owner:** ✅ (Determines if the owner is nearby)
* **Play Sound:** ✅
* **Identify Owner:** ✅

**Apple AirTags & Find My Devices**
This includes AirTags, AirPods, and third-party trackers (like Chipolo or Pebblebee) that use the Apple Find My network.
* **Is Connected with Owner:** ✅ (Determines if the owner is nearby)
* **Play Sound:** ✅ (Availability depends on the specific device mode)
* **Identify Owner:** ✅ (Via NFC for AirTags and supported Find My devices)

**Samsung SmartTags**
These are trackers manufactured by Samsung.
* **Is Connected with Owner:** ✅ (Determines if the owner is nearby)
* **Play Sound:** ❌
* **Identify Owner:** ❌

**Tile Tracker**
Tile is a company that exclusively makes Bluetooth trackers.
* **Is Connected with Owner:** ❌
* **Play Sound:** ❌
* **Identify Owner:** ❌

**Chipolo Tracker (without Find My)**
These are the standard Bluetooth trackers from Chipolo that are not part of the Apple Find My network.
* **Is Connected with Owner:** ✅ (Supported on some models)
* **Play Sound:** ❌
* **Identify Owner:** ❌

**Pebblebee (without Find My)**
These are standard Bluetooth trackers from Pebblebee that are not part of the Apple Find My network.
* **Is Connected with Owner:** ❌
* **Play Sound:** ✅
* **Identify Owner:** ❌

### Additional Technical Limits

**Connection Status**
It is not currently possible for all tracker types to determine whether they are connected to their owner. If a tracker does not support this feature (e.g., Tile), AirGuard cannot filter it out even if the owner is nearby. Thus, you might see your own or friends' trackers more often.

**Randomized Identifiers**
Some Bluetooth trackers change their identity (Bluetooth MAC address) regularly—sometimes multiple times a day.
AirGuard may display the same tracker as multiple different entries over time because it is not always possible to match these randomized identities. Trackers that do not change their identification (e.g., Tile) are detected more consistently but may result in a higher likelihood of false positive detections if you are near them often.
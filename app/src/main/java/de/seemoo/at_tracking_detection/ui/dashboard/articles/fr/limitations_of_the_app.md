# Limites de cette application

AirGuard ne peut trouver que des traceurs basés sur le Bluetooth. Les traceurs qui fonctionnent avec le GPS et partagent la localisation avec le propriétaire via une connexion cellulaire ne peuvent pas être trouvés.

Bien qu'AirGuard puisse détecter une grande variété de traceurs, les fonctionnalités avancées telles que la lecture d'un son, l'identification du propriétaire ou la détermination si l'appareil est actuellement connecté à son propriétaire varient selon le fabricant.

**Appareils Google Find My (Localiser mon appareil)**
Ce sont des traceurs compatibles avec le réseau Localiser mon appareil de Google.
* **Est connecté au propriétaire :** ✅ (Détermine si le propriétaire est à proximité)
* **Émettre un son :** ✅
* **Identifier le propriétaire :** ✅

**Apple AirTags & Appareils Localiser (Find My)**
Cela inclut les AirTags, AirPods et les traceurs tiers (comme Chipolo ou Pebblebee) qui utilisent le réseau Apple Localiser.
* **Est connecté au propriétaire :** ✅ (Détermine si le propriétaire est à proximité)
* **Émettre un son :** ✅ (La disponibilité dépend du mode spécifique de l'appareil)
* **Identifier le propriétaire :** ✅ (Via NFC pour les AirTags et les appareils Localiser pris en charge)

**Samsung SmartTags**
Ce sont des traceurs fabriqués par Samsung.
* **Est connecté au propriétaire :** ✅ (Détermine si le propriétaire est à proximité)
* **Émettre un son :** ❌
* **Identifier le propriétaire :** ❌

**Traceur Tile**
Tile est une entreprise qui fabrique exclusivement des traceurs Bluetooth.
* **Est connecté au propriétaire :** ❌
* **Émettre un son :** ❌
* **Identifier le propriétaire :** ❌

**Traceur Chipolo (sans Localiser)**
Ce sont les traceurs Bluetooth standard de Chipolo qui ne font pas partie du réseau Apple Localiser.
* **Est connecté au propriétaire :** ✅ (Pris en charge sur certains modèles)
* **Émettre un son :** ❌
* **Identifier le propriétaire :** ❌

**Pebblebee (sans Localiser)**
Ce sont des traceurs Bluetooth standard de Pebblebee qui ne font pas partie du réseau Apple Localiser.
* **Est connecté au propriétaire :** ❌
* **Émettre un son :** ✅
* **Identifier le propriétaire :** ❌

### Limites techniques supplémentaires

**État de connexion**
Il n'est actuellement pas possible pour tous les types de traceurs de déterminer s'ils sont connectés à leur propriétaire. Si un traceur ne prend pas en charge cette fonctionnalité (par ex. Tile), AirGuard ne peut pas le filtrer même si le propriétaire est à proximité. Ainsi, vous pourriez voir vos propres traceurs ou ceux de vos amis plus souvent.

**Identifiants aléatoires**
Certains traceurs Bluetooth changent régulièrement leur identité (adresse MAC Bluetooth) — parfois plusieurs fois par jour.
AirGuard peut afficher le même traceur comme plusieurs entrées différentes au fil du temps car il n'est pas toujours possible de faire correspondre ces identités aléatoires. Les traceurs qui ne changent pas leur identification (par ex. Tile) sont détectés de manière plus cohérente mais peuvent entraîner une probabilité plus élevée de faux positifs si vous êtes souvent près d'eux.
# Questions fréquentes (FAQ)

## Général & Fonctionnalités de base

**Quels types de trackers pouvez-vous détecter ?**
Nous détectons actuellement divers trackers, y compris les Apple AirTags, AirPods, appareils FindMy, autres appareils Apple en Mode Perdu, Samsung SmartTags, Chipolos et Tiles.

**Quelle est la consommation de batterie d'AirGuard ?**
AirGuard est conçu pour être économe en énergie. Avec une utilisation typique, vous ne remarquerez pas de différence significative dans l'autonomie de la batterie ; il utilise généralement moins de 1 % de votre batterie.

**AirGuard sera-t-il toujours gratuit ?**
Oui, nous prévoyons de maintenir AirGuard gratuit indéfiniment.

## Comprendre les détections

**Que faire si je vois des trackers à proximité ?**
Si vous remarquez des trackers dans votre zone lors d'une recherche manuelle, ne vous inquiétez pas immédiatement. Ces trackers sont simplement à proximité et ne vous suivent pas nécessairement. Notre application, AirGuard, vous alertera si l'un de ces trackers se déplace avec vous. S'ils ne le font pas, il n'y a généralement pas lieu de s'inquiéter.

**Quelle est la différence entre les appareils blancs et gris ?**
Les appareils blancs (ou noirs en mode sombre) sont en mode "hors ligne", déconnectés de leur propriétaire. Ceux-ci peuvent constituer des menaces de suivi. Nous enregistrons leurs emplacements et vous alertons s'ils semblent vous suivre. Les appareils gris sont connectés ou ont été récemment connectés à l'appareil de leur propriétaire. Ils sont généralement sûrs et ne sont pas enregistrés dans l'application.

**Que signifie 'appareil connecté' ?**
Un appareil connecté est un appareil actuellement ou récemment (dans les 15 minutes) connecté à l'appareil de son propriétaire. Ceux-ci ne sont pas enregistrés dans l'application car ils ne présentent aucun risque, étant près de leur propriétaire.

**Les appareils connectés à leurs propriétaires sont-ils sûrs ?**
Oui, les appareils liés à leurs propriétaires sont sûrs car ils ne peuvent pas envoyer indépendamment des données de localisation. Cela les rend impropres au suivi indésirable ou au harcèlement.

**Pourquoi n'enregistrez-vous pas les données des appareils connectés ?**
Les appareils connectés voyagent généralement avec leur propriétaire, ils ne constituent donc pas une menace de suivi. Par exemple, si quelqu'un dans le train a un iPhone et un AirTag dans son sac, vous ne recevrez pas d'alerte. Nous supposons que l'AirTag ne vous suit pas. Mais s'il n'y a pas d'iPhone à proximité, l'AirTag pourrait être préoccupant.

**Pourquoi certains trackers sont-ils listés deux fois ?**
Les trackers peuvent apparaître deux fois car ils peuvent changer périodiquement leur identité Bluetooth. Cela rend difficile leur suivi constant, entraînant des listes en double.

**Pourquoi ne vois-je pas toujours mes AirTags dans l'application ?**
Parfois, vos AirTags peuvent ne pas apparaître dans l'application. Cependant, ils sont toujours visibles s'ils sont liés à l'Apple ID de quelqu'un d'autre et non connectés à votre appareil.

## Alertes de suivi & Risques

**Comment l'application évalue-t-elle le risque de suivi ?**
L'application calcule les niveaux de risque de suivi : aucun risque, risque moyen et risque élevé. Ceci est basé sur plusieurs facteurs, y compris la fréquence et les endroits où l'appareil de suivi a été près de vous. Un risque élevé ne signifie pas toujours que vous êtes suivi, alors vérifiez toujours la carte du tracker.

**Quelles mesures prendre si je reçois une alerte de suivi ?**
1. Recherchez manuellement le tracker.
2. Si possible, activez son son pour le localiser.
3. Examinez la carte de l'application pour voir depuis combien de temps il vous suit.
4. Désactivez le tracker ou retirez sa batterie. Si elle n'est pas amovible (comme dans les trackers Tile), enveloppez-le dans du papier d'aluminium pour affaiblir son signal.
5. Signalez-le à la police avec le tracker.
6. N'allez pas dans un endroit sûr, comme votre domicile, tant que le tracker n'est pas désactivé.

**Que faire si je ne trouve pas le tracker après une alerte ?**
De fausses alarmes peuvent survenir, en particulier dans les zones bondées comme les transports en commun. Si vous recevez des alertes répétées différents jours, revérifiez. Utilisez les fonctions sonores, les indicateurs de force du signal ou les scans détaillés pour localiser l'appareil.

**Pourquoi pourrais-je avoir du mal à activer le son d'un tracker ?**
Ce problème peut survenir si vous êtes trop loin du tracker. Rapprochez-vous et utilisez la force du signal ou le scan détaillé pour vous aider à le localiser. Le problème peut également survenir si le tracker sort de la portée ou se connecte à son propriétaire pendant votre recherche. Parfois, le tracker peut également décider de ne pas jouer le son en fonction de son état interne.

## Paramètres de l'application & Gestion des appareils

**À quoi sert la 'sensibilité de détection' dans les paramètres ?**
Ce paramètre ajuste la durée pendant laquelle un tracker doit vous suivre avant d'être considéré comme un risque. Une sensibilité plus faible signifie qu'il doit vous suivre plus longtemps ; une sensibilité plus élevée signale les appareils plus rapidement.

**Quel est l'impact de l'activation du mode de scan à faible consommation ?**
Ce mode réduit la fréquence des scans, économisant de l'énergie mais diminuant l'efficacité de la détection. Il pourrait manquer certains appareils à risque. Nous suggérons d'utiliser le mode normal pour une meilleure sécurité, qui ne consomme pas beaucoup plus de batterie sur les téléphones modernes.

**Que se passe-t-il si je restreins l'accès à la localisation de l'application ?**
Sans accès à la localisation, l'application ne repose que sur les scans Bluetooth. Cela peut entraîner davantage de fausses alarmes car nous ne pouvons pas suivre votre mouvement par rapport au tracker. Nous recommandons d'autoriser l'accès à la localisation pour des alertes plus précises.

**Puis-je ignorer ou étiqueter mes propres trackers ?**
Actuellement, vous ne pouvez ignorer que les trackers de marque Tile en raison de limitations techniques. Vous pouvez étiqueter les trackers, mais sachez que le même tracker peut changer son identifiant et sera vu comme un nouveau tracker. Nous recommandons de les nommer juste pour les identifier plus tard.

**Pourquoi ne puis-je pas ignorer certains appareils ?**
Les appareils qui changent fréquemment leur identifiant ne peuvent pas être ignorés. Lorsqu'ils changent leur clé, notre système les traite comme de nouveaux appareils, déclenchant de nouvelles alertes. Certains appareils (comme les Samsung SmartTags) changent souvent de clés, mais vous pouvez ignorer ceux qui ne changent pas (comme les Chipolos et Tiles).
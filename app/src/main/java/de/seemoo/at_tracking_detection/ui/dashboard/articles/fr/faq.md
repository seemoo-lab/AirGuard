# FAQ

## Généralités et fonction principale

**Quels types de traceurs pouvez-vous détecter ?**
Nous détectons actuellement divers traceurs, y compris les appareils Google Localiser, les Apple AirTags, AirPods, appareils Localiser, autres appareils Apple en mode Perdu, Samsung SmartTags, Chipolos et Tiles.

**Quelle quantité de batterie AirGuard utilise-t-elle ?**
AirGuard est conçu pour être économe en énergie. Avec une utilisation typique, vous ne remarquerez pas de différence significative dans l'autonomie de la batterie ; elle utilise généralement moins de 1 % de votre batterie.

**AirGuard sera-t-elle toujours gratuite ?**
Oui, nous prévoyons de garder AirGuard gratuite indéfiniment.

## Comprendre les détections

**Que dois-je faire si je vois des traceurs à proximité ?**
Si vous remarquez des traceurs dans votre zone via une recherche manuelle, ne vous inquiétez pas immédiatement. Ces traceurs sont simplement à proximité et ne vous suivent pas nécessairement. Notre application, AirGuard, vous alertera si l'un de ces traceurs se déplace avec vous. Si ce n'est pas le cas, il n'y a généralement pas lieu de s'inquiéter.

**Quelle est la différence entre les appareils blancs et gris ?**
Les appareils blancs (ou noirs en mode sombre) sont en mode « hors ligne », déconnectés de leur propriétaire. Ceux-ci peuvent être des menaces de pistage. Nous enregistrons leurs emplacements et vous alertons s'ils semblent vous suivre. Les appareils gris sont connectés ou ont été récemment connectés à l'appareil de leur propriétaire. Ils sont généralement sûrs et ne sont pas enregistrés dans l'application.

**Que signifie « appareil connecté » ?**
Un appareil connecté est un appareil actuellement ou récemment (dans les 15 minutes) connecté à l'appareil de son propriétaire. Ceux-ci ne sont pas enregistrés dans l'application car ils ne posent aucun risque, étant près de leur propriétaire.

**Les appareils connectés à leurs propriétaires sont-ils sûrs ?**
Oui, les appareils liés à leurs propriétaires sont sûrs car ils ne peuvent pas envoyer indépendamment des données de localisation. Cela les rend inaptes au pistage indésirable ou au harcèlement.

**Pourquoi n'enregistrez-vous pas les données sur les appareils connectés ?**
Les appareils connectés voyagent généralement avec leur propriétaire, ils ne constituent donc pas une menace de pistage. Par exemple, si quelqu'un dans le train a un iPhone et un AirTag dans son sac, vous ne recevrez pas d'alerte. Nous supposons que l'AirTag ne vous suit pas. Mais s'il n'y a pas d'iPhone à proximité, l'AirTag pourrait être une préoccupation.

**Pourquoi certains traceurs sont-ils listés deux fois ?**
Les traceurs peuvent apparaître deux fois car ils peuvent changer périodiquement leur identité Bluetooth. Cela rend difficile leur suivi cohérent, entraînant des listes en double.

**Pourquoi ne vois-je pas toujours mes AirTags dans l'application ?**
Parfois, vos AirTags peuvent ne pas apparaître dans l'application. Cependant, ils sont toujours visibles s'ils sont liés à l'identifiant Apple de quelqu'un d'autre et non connectés à votre appareil.

## Alertes de suivi et risques

**Comment l'application évalue-t-elle le risque de suivi ?**
L'application calcule les niveaux de risque de suivi : aucun risque, risque moyen et risque élevé. Cela est basé sur plusieurs facteurs, y compris la fréquence et l'endroit où le traceur a été près de vous. Un risque élevé ne signifie pas toujours que vous êtes suivi, vérifiez donc toujours la carte du traceur.

**Quelles étapes dois-je suivre si je reçois une alerte de suivi ?**
1. Recherchez manuellement le traceur.
2. Si possible, activez son son pour le localiser.
3. Examinez la carte de l'application pour voir depuis combien de temps il vous suit.
4. Désactivez le traceur ou retirez sa batterie. Si elle n'est pas amovible (comme dans les traceurs Tile), enveloppez-le dans du papier aluminium pour affaiblir son signal.
5. Signalez-le à la police avec le traceur.
6. Ne vous rendez pas dans un endroit sûr, comme votre domicile, tant que le traceur n'est pas désactivé.

**Et si je ne trouve pas le traceur après une alerte ?**
Des fausses alertes peuvent se produire, en particulier dans les zones bondées comme les transports en commun. Si vous recevez des alertes répétées à des jours différents, vérifiez à nouveau. Utilisez les fonctionnalités sonores, les indicateurs de force du signal ou les analyses détaillées pour localiser l'appareil.

**Pourquoi pourrais-je avoir du mal à activer le son d'un traceur ?**
Ce problème peut survenir si vous êtes trop loin du traceur. Rapprochez-vous et utilisez la force du signal ou l'analyse détaillée pour vous aider à le localiser. Le problème peut également survenir si le traceur sort de la portée ou se connecte à son propriétaire pendant votre recherche. Parfois, le traceur peut également décider de ne pas émettre de son en fonction de son état interne.

## Paramètres de l'application et gestion des appareils

**Que fait la « sensibilité de détection » dans les paramètres ?**
Ce paramètre ajuste combien de temps un traceur doit vous suivre avant d'être considéré comme un risque. Une sensibilité plus faible signifie qu'il doit vous suivre plus longtemps ; une sensibilité plus élevée signale les appareils plus rapidement.

**Quel est l'impact de l'activation du mode d'analyse à faible consommation ?**
Ce mode réduit la fréquence d'analyse, économisant l'énergie mais diminuant l'efficacité de la détection. Il pourrait manquer certains appareils risqués. Nous suggérons d'utiliser le mode normal pour une meilleure sécurité, ce qui ne draine pas significativement plus de batterie sur les téléphones modernes.

**Et si je restreins l'accès à la localisation de l'application ?**
Sans accès à la localisation, l'application repose uniquement sur les analyses Bluetooth. Cela peut entraîner plus de fausses alertes car nous ne pouvons pas suivre votre mouvement par rapport au traceur. Nous recommandons d'autoriser l'accès à la localisation pour des alertes plus précises.

**Puis-je ignorer ou étiqueter mes propres traceurs ?**
Actuellement, vous ne pouvez ignorer que les traceurs de marque Tile en raison de limitations techniques. Vous pouvez étiqueter les traceurs mais sachez que le même traceur peut changer son identifiant et sera vu comme un nouveau traceur. Nous recommandons de les nommer juste pour les identifier plus tard.

**Pourquoi ne puis-je pas ignorer certains appareils ?**
Les appareils qui changent fréquemment leur identifiant ne peuvent pas être ignorés. Lorsqu'ils changent leur clé, notre système les traite comme de nouveaux appareils, déclenchant de nouvelles alertes. Certains appareils (comme les Samsung SmartTags) changent souvent de clé, mais vous pouvez ignorer ceux qui ne changent pas (comme les Chipolos et Tiles).
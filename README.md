# Gestion SWU

> Votre collection Star Wars Unlimited, vos decks et vos recherches, réunis dans une application web en français.

**Gestion SWU** permet d’importer le catalogue des cartes, de suivre les exemplaires possédés et de construire des decks en les comparant à sa collection. Chaque joueur dispose de son compte, de ses recherches et de ses listes personnelles. Il peut choisir de partager une vitrine ou de publier certains decks dans la communauté.

L’interface est adaptée à l’ordinateur et au téléphone. Elle comprend des galeries de cartes agrandissables, un ajout rapide par numéro et une lecture de référence à partir d’une photo.

**Documentation de l’état du projet intégrant les fonctionnalités de communauté, au 9 novembre 2026.** Les versions techniques ci-dessous correspondent aux fichiers du projet, sans présumer qu’il s’agit des dernières versions disponibles.

---

## Sommaire

1. [Fonctionnalités](#fonctionnalites)
2. [Comprendre les cartes et les quantités](#cartes-et-quantites)
3. [Technologies et architecture](#architecture)
4. [Installation et premier démarrage](#installation)
5. [Comptes et permissions](#comptes)
6. [Importer le catalogue](#import)
7. [Utiliser sa collection](#collection)
8. [Ajouter et scanner des cartes](#ajout)
9. [Construire un deck](#decks)
10. [Recherches, vitrine et communauté](#partage)
11. [Navigation et pages principales](#navigation)
12. [Organisation du code](#code)
13. [Base de données et migrations](#base)
14. [Sécurité et confidentialité](#securite)
15. [Maintenance et sauvegardes](#maintenance)
16. [Dépannage](#depannage)
17. [Vérification du fonctionnement](#verification)
18. [Limites et évolutions possibles](#limites)

---

<a id="fonctionnalites"></a>
## 1. Fonctionnalités

| Espace | Ce qu’il permet de faire |
| --- | --- |
| **Accueil** | Consulter les actualités officielles en français et accéder aux principales fonctions. |
| **Catalogue** | Rechercher les cartes importées, filtrer par extension, coût et affinités, consulter les images et ajouter des exemplaires. |
| **Ma collection** | Retrouver ses cartes regroupées par référence standard, voir les finitions possédées et ajuster leurs quantités. |
| **Ajout rapide** | Enregistrer une carte avec son extension, son numéro imprimé et sa finition, puis enchaîner les saisies. |
| **Scanner** | Photographier ou sélectionner une image, lire sa référence et confirmer la carte à ajouter. |
| **Mes decks** | Choisir un leader et une base, composer une liste et identifier les exemplaires manquants. |
| **Mes recherches** | Conserver les cartes marquées d’un cœur et les retrouver dans une liste dédiée. |
| **Ma vitrine** | Choisir les rubriques de son profil que les autres joueurs peuvent consulter. |
| **Communauté** | Découvrir les joueurs et les decks publiés volontairement. |
| **Mon compte** | Modifier son pseudo, son e-mail et son mot de passe. |
| **Administration** | Gérer les extensions, importer les cartes et administrer les accès utilisateurs. |

### Accueil et actualités

L’accueil affiche un carrousel alimenté par les actualités officielles Star Wars Unlimited. Le serveur récupère jusqu’à cinq articles français publiés, avec leur titre, leur description, leur image et leur lien.

La récupération s’effectue en arrière-plan : le rendu de l’accueil n’attend pas la réponse du site officiel. Un cache en mémoire limite les appels réseau. L’actualisation devient possible après environ **30 minutes** ; après un échec, une nouvelle tentative devient possible après **5 minutes**, lors d’une consultation. Le dernier résultat réussi est conservé pendant une indisponibilité de la source.

La tuile **Decks de la commu** ouvre la liste des publications. Les tuiles **Tournois** et **Decks du moment** restent des emplacements annoncés pour plus tard.

<a id="cartes-et-quantites"></a>
## 2. Comprendre les cartes et les quantités

Cette distinction est essentielle au fonctionnement de l’application.

### Catalogue, possession et finition

- Une **fiche du catalogue** décrit une carte importée : titre, sous-titre, numéro, extension, images, coût, affinités et type.
- Une **possession** associe un utilisateur à une fiche du catalogue, avec un total d’exemplaires et un nombre de foils.
- Une **hyperspace** possède sa propre fiche et son propre numéro. Elle est rattachée à sa carte standard pour permettre le regroupement.
- Une **foil** est une finition comptée dans la possession de la fiche concernée. Elle ne crée pas une nouvelle fiche du catalogue.

| Finition possédée | Fiche utilisée | Comptage |
| --- | --- | --- |
| Standard non foil | Carte standard | Total standard, hors foils |
| Standard foil | Même carte standard | Total standard **et** sous-total foil |
| Hyperspace non foil | Variante hyperspace | Total de la variante, hors foils |
| Hyperspace foil | Même variante hyperspace | Total de la variante **et** sous-total foil |

### Exemple : une seule carte dans la collection

Pour une même carte de jeu :

| Exemplaires | Quantité |
| --- | ---: |
| Standard non foil | 1 |
| Standard foil | 1 |
| Hyperspace non foil | 1 |
| **Total regroupé** | **3** |
| **Dont foils** | **1** |

La collection affiche **une seule tuile**, avec l’image de la référence standard et le total **3 exemplaires, dont 1 foil**. Un clic ouvre la galerie des versions réellement possédées.

En base, `quantite` comprend déjà les foils :

```text
non_foils = quantite - quantite_foil
```

Il ne faut donc jamais calculer le stock en additionnant `quantite` et `quantite_foil`.

### Numéro imprimé et référence standard

L’ajout rapide recherche le **numéro imprimé sur l’exemplaire**. Une hyperspace garde son numéro spécifique, même si la collection l’affiche ensuite sous la carte standard.

Le regroupement repose sur la relation `carte_standard_id`, et non sur une simple égalité de titre. Deux cartes distinctes portant un titre similaire ne doivent pas être fusionnées.

<a id="architecture"></a>
## 3. Technologies et architecture

| Élément | Technologie ou configuration |
| --- | --- |
| Langage | Java 21 |
| Application | Spring Boot 4.0.9, version déclarée dans `pom.xml` |
| Serveur web | Spring MVC et serveur embarqué Spring Boot |
| Pages | Thymeleaf et fragments réutilisables |
| Authentification | Spring Security, sessions et formulaires |
| Persistance | Spring Data JPA / Hibernate |
| Base | MySQL 8.4 |
| Migrations | Flyway et module MySQL |
| Compilation | Maven |
| Interface | HTML, CSS et JavaScript |
| Lecture de photo | Tesseract.js 6.0.1, chargé dans le navigateur |
| Services locaux | Docker Compose pour MySQL et phpMyAdmin |

### Répartition des responsabilités

1. Le navigateur affiche les pages Thymeleaf et gère les interactions : menus, fenêtres, agrandissement, caméra et OCR.
2. Les contrôleurs Spring reçoivent les requêtes et les formulaires.
3. Les services appliquent les règles de gestion dans leurs transactions.
4. Les repositories assurent les lectures et écritures en base.
5. Les services d’import et d’actualités communiquent avec la source officielle.

Dans la configuration de développement documentée ici, **l’application Java s’exécute sur la machine hôte**. MySQL et phpMyAdmin s’exécutent dans Docker. Il n’est pas nécessaire de conteneuriser l’application pour l’utiliser ainsi.

### Adresses de développement

| Service | Accès depuis la machine hôte | Port dans le conteneur |
| --- | --- | ---: |
| Application | [http://localhost:8090](http://localhost:8090) | Application lancée sur l’hôte |
| phpMyAdmin | [http://127.0.0.1:8084](http://127.0.0.1:8084) | 80 |
| MySQL | `127.0.0.1:3310` | 3306 |

**Le port MySQL reste `3306` dans Docker.** Le port `3310` est celui publié sur la machine hôte. phpMyAdmin contacte le service `mysql:3306` ; Spring Boot lancé sur l’hôte contacte `localhost:3310`.

<a id="installation"></a>
## 4. Installation et premier démarrage

### 4.1. Prérequis

- Un JDK **Java 21**, sélectionné aussi dans l’IDE.
- Maven accessible avec la commande `mvn`.
- Docker et Docker Compose fonctionnels.
- Un navigateur récent.
- Une connexion Internet pour importer le catalogue, charger les actualités et télécharger les ressources OCR.

IntelliJ IDEA peut être utilisé pour ouvrir le projet Maven et lancer la classe principale.

### 4.2. Préparer les fichiers du projet

Le répertoire de travail doit contenir `pom.xml` et le dossier `src`.

Si l’application a été assemblée à partir de correctifs ZIP, leurs dossiers `src` se fusionnent dans le même projet. Les fichiers modifiés remplacent leurs versions précédentes. Les correctifs ne constituent pas, à eux seuls, une application complète.

Ne gardez pas deux contrôleurs actifs pour les mêmes routes. Renommer une ancienne classe en `Old` ne suffit pas à la désactiver si elle conserve `@Controller`.

### 4.3. Démarrer MySQL et phpMyAdmin

Si vous disposez déjà d’un fichier Compose fonctionnel avec ces ports, conservez-le. Sinon, voici un **exemple de configuration locale** à placer dans `compose.yaml`, à côté de `pom.xml` :

```yaml
name: gestion-swu

services:
  mysql:
    image: mysql:8.4
    restart: unless-stopped
    environment:
      MYSQL_DATABASE: gestion_swu
      MYSQL_USER: swu_app
      MYSQL_PASSWORD: ${MYSQL_PASSWORD}
      MYSQL_ROOT_PASSWORD: ${MYSQL_ROOT_PASSWORD}
    ports:
      - "127.0.0.1:3310:3306"
    volumes:
      - mysql_data:/var/lib/mysql
    command:
      - "--character-set-server=utf8mb4"
      - "--collation-server=utf8mb4_0900_ai_ci"
    healthcheck:
      test:
        - CMD-SHELL
        - 'MYSQL_PWD="$$MYSQL_PASSWORD" mysql --protocol=TCP -h 127.0.0.1 -u"$$MYSQL_USER" "$$MYSQL_DATABASE" -e "SELECT 1" >/dev/null 2>&1'
      interval: 10s
      timeout: 5s
      retries: 20
      start_period: 30s

  phpmyadmin:
    image: phpmyadmin:5.2-apache
    restart: unless-stopped
    depends_on:
      mysql:
        condition: service_healthy
    environment:
      PMA_HOST: mysql
      PMA_PORT: 3306
      UPLOAD_LIMIT: 64M
    ports:
      - "127.0.0.1:8084:80"

volumes:
  mysql_data:
```

Créez un fichier `.env` au même emplacement, avec vos propres valeurs :

```dotenv
MYSQL_PASSWORD=REMPLACER_PAR_UN_MOT_DE_PASSE_APPLICATION
MYSQL_ROOT_PASSWORD=REMPLACER_PAR_UN_AUTRE_MOT_DE_PASSE_ROOT
```

Ces valeurs sont des exemples à remplacer, pas des identifiants fournis par l’application. Ne publiez pas le fichier `.env` dans un dépôt.

Démarrez les services :

```bash
docker compose up -d
docker compose ps
```

Le volume `mysql_data` conserve la base entre les redémarrages. Les variables d’initialisation MySQL servent lors de la création initiale de ce volume : changer ensuite le mot de passe dans `.env` ne change pas automatiquement celui du compte déjà créé dans MySQL.

### 4.4. Configurer Spring Boot

La configuration attendue dans [`application.properties`](src/main/resources/application.properties) est :

```properties
spring.application.name=gestion-swu
server.port=8090

spring.datasource.url=jdbc:mysql://localhost:3310/gestion_swu?connectionTimeZone=UTC
spring.datasource.username=swu_app
spring.datasource.password=${MYSQL_PASSWORD}

spring.jpa.hibernate.ddl-auto=validate
spring.jpa.open-in-view=false
spring.flyway.enabled=true
```

Le mot de passe transmis à Java doit correspondre à celui du compte MySQL `swu_app`.

> Le fichier `.env` lu par Docker Compose n’est pas automatiquement chargé par l’application Java lancée depuis IntelliJ ou Maven.

#### Dans IntelliJ IDEA

1. Ouvrez le dossier contenant `pom.xml` et laissez Maven charger les dépendances.
2. Vérifiez que le SDK du projet et le JDK de lancement sont Java 21.
3. Créez ou ouvrez la configuration de lancement de `fr.xalkinn.swu.Main`.
4. Dans les variables d’environnement de cette configuration, ajoutez `MYSQL_PASSWORD` avec la même valeur que dans votre configuration Docker.
5. Démarrez les conteneurs, puis lancez `Main`.

#### Depuis un terminal

Sous PowerShell :

```powershell
$env:MYSQL_PASSWORD = "VOTRE_MOT_DE_PASSE_APPLICATION"
mvn spring-boot:run
```

Sous Linux ou macOS :

```bash
export MYSQL_PASSWORD='VOTRE_MOT_DE_PASSE_APPLICATION'
mvn spring-boot:run
```

Au démarrage, Flyway applique les migrations manquantes, puis Hibernate vérifie la cohérence du schéma. Il ne faut pas créer les tables manuellement avant ce premier démarrage.

### 4.5. Créer le premier administrateur

Une inscription crée un compte **USER actif**. Aucun compte administrateur avec mot de passe prédéfini n’est fourni.

Pour initialiser une nouvelle installation :

1. Ouvrez `/inscription` et créez votre compte.
2. Dans phpMyAdmin, sélectionnez la base `gestion_swu`.
3. Promouvez uniquement le compte voulu, en remplaçant l’adresse ci-dessous :

```sql
UPDATE utilisateurs
SET role = 'ADMIN'
WHERE email = 'votre-adresse@example.fr';
```

4. Reconnectez-vous avec ce compte et ouvrez `/admin`.
5. Utilisez ensuite l’interface d’administration pour gérer les accès.

### 4.6. Charger les premières cartes

Dans **Administration** :

1. Enregistrez une extension avec son code, par exemple `SOR`.
2. Ouvrez l’import des cartes.
3. Importez cette extension ou lancez l’import de toutes les extensions enregistrées.
4. Consultez le bilan, puis le catalogue.

Une base nouvellement créée peut donc être vide de cartes même si l’application démarre correctement.

<a id="comptes"></a>
## 5. Comptes et permissions

### Profils d’accès

| Profil | Accès |
| --- | --- |
| **Visiteur** | Accueil, inscription, connexion, joueurs et rubriques publiques autorisées, decks communautaires publiés. |
| **USER** | Fonctions personnelles : catalogue, collection, ajouts, scan, decks, recherches, compte et réglages de vitrine. |
| **ADMIN** | Fonctions USER et administration des extensions, imports et comptes. |

La connexion accepte un **pseudo ou un e-mail**, sans distinction de casse pour leur recherche. Le principal de sécurité conserve l’identifiant stable du compte ; le nom de session reste son e-mail.

### Modifier son compte

La page `/compte` permet de modifier le pseudo, l’e-mail et le mot de passe. Le mot de passe actuel est demandé pour valider les changements. Un changement d’e-mail ou de mot de passe déconnecte la session courante et demande une nouvelle connexion.

### Administrer les accès

L’administration permet de rechercher les utilisateurs, de changer leur rôle et d’activer ou désactiver leur accès. Le dernier administrateur actif est protégé contre une rétrogradation ou une désactivation qui laisserait l’application sans administrateur.

Les accès d’une session authentifiée sont revérifiés en base lors des requêtes concernées. Un compte désactivé perd son accès ; ses vitrines et publications communautaires ne sont plus consultables publiquement.

<a id="import"></a>
## 6. Importer le catalogue

### Source et contenu

L’import utilise la source officielle configurée dans [`ImportCatalogueService`](src/main/java/fr/xalkinn/swu/importation/ImportCatalogueService.java) :

```text
https://admin.starwarsunlimited.com/api/card-list
```

Les requêtes sélectionnent la langue française et l’extension demandée. Le service traite les fiches standard ainsi que les variantes hyperspace prises en charge par son filtre de variantes.

Les principales données enregistrées sont :

- identifiant de la source, titre et sous-titre ;
- numéro imprimé et extension ;
- URL des images recto et verso ;
- coût et affinités, avec leur multiplicité ;
- type de carte pour l’éditeur de decks ;
- indicateur hyperspace et relation avec la carte standard.

Les images sont référencées par leur URL : l’application ne constitue pas une copie locale complète des fichiers d’illustration.

### Déroulement d’un import

1. Charger toutes les pages de l’extension depuis la source.
2. Vérifier la langue, l’extension, les données obligatoires, les doublons et la cohérence de la pagination.
3. Dans une transaction, créer ou mettre à jour les cartes selon leur identifiant source.
4. Rattacher les variantes à leur référence standard.
5. Valider les contraintes et retourner le nombre d’ajouts et de mises à jour.

Un réimport met à jour les fiches existantes sans recréer les possessions des joueurs. Il ne constitue pas une synchronisation avec suppression automatique de toutes les fiches absentes de la réponse.

### Import de toutes les extensions

L’import global parcourt **les extensions déjà enregistrées dans l’application**. Il ne découvre pas automatiquement toutes les extensions existantes dans le jeu.

Chaque extension est traitée séparément. Une extension en échec n’annule pas les imports réussis des autres extensions ; le bilan distingue les réussites et les erreurs.

L’interface affiche un état d’attente et bloque les doubles soumissions depuis la page. Cet indicateur ne représente pas un pourcentage serveur précis de progression.

### Affinités utilisées par les filtres

| Code technique | Libellé | Couleur |
| --- | --- | --- |
| `VIGILANCE` | Vigilance | Bleu |
| `COMMAND` | Commandement | Vert |
| `AGGRESSION` | Agressivité | Rouge |
| `CUNNING` | Ruse | Jaune |
| `HEROISM` | Héroïsme | Blanc |
| `VILLAINY` | Infâmie | Noir |

Les modes de combinaison sont **toutes les affinités cochées**, **au moins une** et **sans affinité**.

<a id="collection"></a>
## 7. Utiliser sa collection

### Catalogue

La recherche textuelle du catalogue porte sur le titre ou le sous-titre. Elle se combine avec l’extension, les limites de coût et les affinités.

Les filtres avancés sont repliables. Les critères actifs restent visibles sous forme de pastilles, supprimables individuellement, avec une action pour tout effacer.

Un clic sur une carte ouvre sa grande fiche :

- image recto et verso lorsqu’il existe ;
- agrandissement au clic sur chaque image ;
- coût, affinités et informations d’extension ;
- stock de **cette référence du catalogue**, foil inclus ;
- ajout d’un exemplaire non foil ou foil ;
- cœur pour les recherches.

Les boutons d’ajustement présents sur les tuiles restent utilisables séparément. L’ajustement rapide sans précision de finition concerne un exemplaire non foil.

### Collection regroupée

La collection recherche dans l’ensemble des cartes possédées, avant pagination, par titre, sous-titre ou numéro. Elle propose aussi des filtres par extension, version possédée et présence dans les recherches.

Un filtre **Avec foil** sélectionne les cartes pour lesquelles au moins un exemplaire foil est possédé. Il ne remplace pas le total par le seul nombre de foils : toutes les versions de la carte retenue restent comptées et consultables.

Les filtres **Avec standard** et **Avec hyperspace** portent sur la famille de versions ; ils ne signifient pas « non foil ».

### Galerie des versions

Un clic sur une tuile ouvre une galerie avec les finitions effectivement possédées : standard, standard foil, hyperspace et hyperspace foil, selon le stock.

Chaque vignette affiche son image, son numéro, sa quantité et ses commandes `−1` / `+1`. Les images sont agrandissables. La fenêtre reste ouverte après une modification et retrouve sa position de défilement lorsque le stockage de l’onglet est disponible.

Retirer le dernier exemplaire d’une finition la fait disparaître de la galerie. Retirer tous les exemplaires du groupe retire sa tuile de la collection. Le catalogue reste intact.

La limite de **9 999 exemplaires** s’applique à une possession d’une fiche de carte, et non à une addition arbitraire de tous les groupes.

<a id="ajout"></a>
## 8. Ajouter et scanner des cartes

### Ajout rapide

Depuis **Ma collection → Ajout de carte → Ajout rapide** :

1. Choisissez l’extension.
2. Saisissez le numéro imprimé, par exemple `005`.
3. Choisissez la finition : classique, foil, hyperspace ou hyperspace foil.
4. Validez avec le bouton ou la touche Entrée.

En cas de correspondance unique, un exemplaire est ajouté. Si plusieurs fiches correspondent, l’application demande de choisir avant d’ajouter. L’extension et la finition restent sélectionnées pour faciliter une série de saisies.

La carte doit exister dans le catalogue importé. Pour une hyperspace, utilisez son propre numéro et sélectionnez la finition hyperspace correspondante.

### Photo et OCR

Depuis **Ma collection → Ajout de carte → Scanner une carte** :

1. Ouvrez la caméra ou la webcam, ou choisissez une image.
2. Photographiez une carte nette, droite, avec les inscriptions de référence lisibles.
3. Lancez la lecture de l’extension et du numéro.
4. Vérifiez ou corrigez les valeurs proposées.
5. Recherchez dans le catalogue et confirmez la bonne carte.

Le traitement OCR se déroule dans le navigateur avec Tesseract.js. Le script prépare l’image, notamment sa zone de référence, pour extraire un code d’extension et un numéro. La recherche serveur reçoit ces valeurs : il ne s’agit pas d’une reconnaissance automatique de l’illustration de la carte.

La confirmation de scan ajoute actuellement **un exemplaire non foil**. Pour préciser une finition foil, utilisez l’ajout rapide ou les actions adaptées du catalogue.

### Caméra sur téléphone

La caméra intégrée vérifie que le navigateur dispose d’un contexte sécurisé et de `getUserMedia`. `localhost` sur la machine de développement peut fonctionner, tandis qu’une adresse distante en HTTP peut être bloquée.

Pour un accès mobile, utilisez une adresse HTTPS reconnue comme sécurisée par le navigateur et autorisez l’accès à la caméra. L’utilisation de Tailscale ne rend pas, à elle seule, une URL HTTP compatible avec cette exigence du navigateur.

Le choix d’un fichier reste une alternative. Le premier OCR peut être plus lent en raison du chargement du moteur et de ses ressources ; la vitesse dépend également du téléphone et de l’image. Une référence ambiguë doit être corrigée manuellement avant confirmation.

<a id="decks"></a>
## 9. Construire un deck

### Création et composition

Dans **Ma collection → Mes decks** :

1. Créez une liste avec un nom et, éventuellement, des notes.
2. Cliquez sur l’emplacement `+` du leader ou de la base pour effectuer leur sélection.
3. Recherchez les cartes du catalogue et ajoutez leurs quantités.
4. Ajustez les quantités dans **Votre liste** ; la valeur `0` retire la ligne du deck.

Les résultats de l’éditeur utilisent les références standard. Les variantes possédées sont prises en compte pour calculer le stock disponible. Une carte dont le type n’a pas été importé peut être exclue des choix disponibles : réactualisez son extension.

**Votre liste** est un accordéon. Son état ouvert ou fermé est conservé par deck dans l’onglet du navigateur. Les ajouts retrouvent la position de travail, y compris si l’allongement de la composition a déplacé les résultats.

### Comprendre les compteurs

| Compteur | Signification |
| --- | --- |
| **Dans la liste principale** | Somme des quantités des lignes du deck, hors leader et base. |
| **Exemplaires disponibles** | Exemplaires possédés utilisables pour la liste sélectionnée, leader et base inclus lorsqu’ils sont choisis. |
| **Exemplaires à trouver** | Différence entre les quantités nécessaires et le stock possédé, leader et base inclus. |

Les stocks regroupent les finitions standard et hyperspace, foil ou non. Ils ne sont **pas réservés entre les decks** : les mêmes exemplaires peuvent contribuer à la comparaison de plusieurs listes.

### Galeries du deck

Le compteur de liste principale ouvre une galerie des cartes du deck. Celui des exemplaires à trouver ouvre la galerie des cartes manquantes. Les images sont cliquables pour les lire en grand.

Dans la galerie des cartes manquantes, le cœur permet d’ajouter une carte à ses recherches sans retourner dans le catalogue. Cette action reste volontaire : ajouter une carte à un deck ne crée pas automatiquement une recherche.

Créer, modifier ou supprimer un deck ne change pas les possessions de la collection.

L’éditeur accepte jusqu’à **99 exemplaires par ligne** et **300 références principales distinctes**. Ces limites techniques ne constituent pas une validation des règles de tournoi.

<a id="partage"></a>
## 10. Recherches, vitrine et communauté

### Mes recherches : une carte marquée d’un cœur

Le cœur ajoute ou retire une carte de la liste personnelle des recherches. Son état est synchronisé entre les emplacements qui l’affichent.

La recherche est rattachée à la référence standard : les variantes de la même famille partagent donc cet état. Elle ne stocke pas encore un objectif chiffré ni une finition précise à obtenir. Retirer une recherche ne supprime aucune possession.

### Ma vitrine : des rubriques publiques choisies

Dans `/ma-vitrine`, le joueur choisit de publier ou non sa vitrine et d’autoriser séparément le partage de :

- sa collection ;
- ses decks ;
- ses recherches.

Une vitrine doit être publiée pour apparaître dans l’annuaire des joueurs. Une rubrique non autorisée n’est pas accessible simplement en devinant son URL.

### Decks de la commu : une publication par deck

Chaque deck peut être publié depuis son éditeur, puis retiré de la communauté. La liste publique se trouve dans `/communaute/decks`, avec une recherche par nom de deck, pseudo, leader ou base.

La page publique affiche le nom, les notes et la composition, avec le pseudo de l’auteur. Elle ne montre ni son e-mail, ni ses stocks personnels, ni ses cartes manquantes, ni les commandes de modification.

Les changements enregistrés sur un deck publié sont visibles sur sa page publique. Il s’agit du deck courant, pas d’un instantané figé au moment de la publication. Un projet incomplet peut être partagé.

### Deux réglages de partage indépendants

| Réglage | Effet |
| --- | --- |
| **Partager mes decks dans ma vitrine** | Autorise la consultation des decks via la rubrique de la vitrine publiée. |
| **Publier ce deck dans la communauté** | Ajoute ce deck précis à la liste communautaire et à son adresse publique dédiée. |

**Retirer un deck de la communauté ne le masque pas automatiquement dans une vitrine dont le partage des decks est activé.** Inversement, une vitrine privée n’empêche pas la publication volontaire d’un deck dans la communauté.

Les decks existants n’ont pas été publiés automatiquement dans la communauté lors de l’ajout de cette fonctionnalité.

<a id="navigation"></a>
## 11. Navigation et pages principales

Le menu connecté est organisé ainsi :

- **Accueil**
- **Les cartes**
- **Ma collection**
  - Consulter ma collection
  - Ajout de carte
    - Ajout rapide
    - Scanner une carte
  - Mes decks
  - Ma vitrine
  - Mes recherches
- **Communauté**
  - Les joueurs
  - Decks de la commu
- **Administration**, pour les administrateurs
- **Mon compte**
  - Mon espace
  - Paramètres du compte
  - Déconnexion

Le sous-menu d’ajout s’ouvre au survol ou au clic sur ordinateur, et au clic sur petit écran. Sur mobile, la navigation se replie derrière le bouton **Menu**. Les niveaux sont également utilisables au clavier.

### Routes de consultation

| Chemin | Page | Accès |
| --- | --- | --- |
| `/` | Accueil | Public |
| `/login` | Connexion | Public |
| `/inscription` | Création de compte | Public |
| `/catalogue` | Catalogue importé | Connecté |
| `/collection` | Collection regroupée | Connecté |
| `/collection/ajout-rapide` | Saisie rapide | Connecté |
| `/scan` | Photo et saisie de référence | Connecté |
| `/decks` | Decks personnels | Connecté |
| `/decks/{id}` | Éditeur d’un deck personnel | Propriétaire |
| `/recherches` | Cartes marquées d’un cœur | Connecté |
| `/ma-vitrine` | Réglages de partage | Connecté |
| `/joueurs` | Annuaire des vitrines publiées | Public |
| `/joueurs/{id}` | Vitrine et rubrique autorisée | Public selon partage |
| `/joueurs/{joueurId}/decks/{deckId}` | Deck partagé dans une vitrine | Public selon partage |
| `/communaute/decks` | Decks publiés | Public |
| `/communaute/decks/{id}` | Fiche communautaire | Public si publication autorisée |
| `/espace` | Tableau de bord personnel | Connecté |
| `/compte` | Paramètres du compte | Connecté |
| `/admin` | Administration | ADMIN |
| `/admin/extensions` | Gestion des extensions | ADMIN |
| `/admin/import/extensions` | Import et bilans | ADMIN |
| `/admin/utilisateurs` | Gestion des accès | ADMIN |

Exemple de rubrique : `/joueurs/1?rubrique=decks`.

### Principales actions serveur

Les modifications sont effectuées par des requêtes **POST**, notamment :

| Chemin | Action |
| --- | --- |
| `/collection/ajuster` | Ajouter ou retirer un exemplaire avec sa finition |
| `/collection/ajout-rapide` | Ajouter par extension, numéro et finition |
| `/scan/rechercher` | Chercher les candidats pour une référence |
| `/scan/ajouter` | Confirmer un ajout après scan |
| `/decks` | Créer un deck |
| `/decks/{id}/choisir` | Choisir ou retirer le leader ou la base |
| `/decks/{id}/ajouter` | Ajouter une carte à la liste |
| `/decks/{id}/quantite` | Modifier une quantité |
| `/decks/{id}/publication` | Publier ou retirer de la communauté |
| `/api/recherches/cartes` | Modifier un cœur avec réponse JSON |
| `/recherches/cartes` | Même modification par formulaire classique |
| `/admin/import/extensions/toutes` | Importer toutes les extensions enregistrées |
| `/logout` | Fermer la session |

Ces routes appartiennent à l’application et utilisent ses contrôles d’accès et sa protection CSRF. Elles ne constituent pas une API publique à jeton destinée à des intégrations externes. Le flux d’actualités du navigateur est exposé en lecture par `/api/accueil/actualites`.

<a id="code"></a>
## 12. Organisation du code

Le package principal est `fr.xalkinn.swu`. La classe de démarrage est [`Main`](src/main/java/fr/xalkinn/swu/Main.java).

| Répertoire ou package | Responsabilité |
| --- | --- |
| `src/main/java/fr/xalkinn/swu/actualite` | Actualités officielles et cache |
| `administration` | Gestion des utilisateurs et de leurs accès |
| `carte` | Modèle du catalogue, filtres et consultation |
| `collection` | Possessions, quantités et ajout rapide |
| `compte` | Modification du profil et du mot de passe |
| `deck` | Decks personnels, composition et publications communautaires |
| `extension` | Référentiel des extensions |
| `importation` | Appels à la source et synchronisation du catalogue |
| `recherche` | Cartes marquées d’un cœur |
| `scan` | Recherche et confirmation après lecture de référence |
| `securite` | Authentification, autorisation et contrôle des sessions |
| `utilisateur` | Comptes et inscription |
| `vitrine` | Réglages de partage et consultation publique des joueurs |
| `web` | Utilitaires de présentation, dont les filtres actifs |
| `src/main/resources/templates` | Pages Thymeleaf |
| `src/main/resources/static/css` | Styles communs, styles par page et composants partagés |
| `src/main/resources/static/js` | Interactions et traitements côté navigateur |
| `src/main/resources/db/migration` | Évolutions SQL versionnées |

### Composants visuels partagés

| Fichier | Utilité |
| --- | --- |
| [`fragments/header.html`](src/main/resources/templates/fragments/header.html) | En-tête et chargement de la navigation commune |
| [`fragments/menu.html`](src/main/resources/templates/fragments/menu.html) | Arborescence et visibilité des liens |
| [`fragments/footer.html`](src/main/resources/templates/fragments/footer.html) | Pied de page |
| [`fragments/deck.html`](src/main/resources/templates/fragments/deck.html) | Tuile leader/base commune aux listes de decks |
| [`fragments/recherches.html`](src/main/resources/templates/fragments/recherches.html) | Cœur et messages liés aux recherches |
| [`fragments/galerie-cartes.html`](src/main/resources/templates/fragments/galerie-cartes.html) | Grandes fiches, versions possédées et agrandissement |
| [`navigation.js`](src/main/resources/static/js/navigation.js) | Menu mobile, sous-niveaux, survol et clavier |
| [`galerie-cartes.js`](src/main/resources/static/js/galerie-cartes.js) | Fenêtres et agrandissements du catalogue et de la collection |
| [`deck.js`](src/main/resources/static/js/deck.js) | Fenêtres du deck et état de l’accordéon |
| [`recherches.js`](src/main/resources/static/js/recherches.js) | Synchronisation des cœurs |
| [`position-retour.js`](src/main/resources/static/js/position-retour.js) | Retour à la position après les formulaires de cartes |

La page de collection récente utilise `galerie-cartes.js` à la place de l’ancien gestionnaire `collection.js`. Évitez de charger les deux gestionnaires sur la même page.

<a id="base"></a>
## 13. Base de données et migrations

### Modèle principal

| Table | Rôle et relations |
| --- | --- |
| `utilisateurs` | Compte, pseudo, e-mail, hash du mot de passe, rôle et état actif |
| `extensions` | Extensions du catalogue |
| `cartes` | Fiches d’une extension ; relation facultative vers une carte standard de la même extension |
| `carte_affinites` | Affinités d’une carte et nombre de symboles |
| `possessions` | Association utilisateur/carte, quantité totale et foils inclus |
| `decks` | Liste d’un utilisateur, leader, base, dates et état de publication communautaire |
| `deck_cartes` | Cartes et quantités d’un deck |
| `recherches_cartes` | Association unique utilisateur/référence recherchée |
| `vitrines` | Réglages de publication et de partage d’un utilisateur |
| `verrous_administration` | Verrou transactionnel pour les changements d’accès |
| `flyway_schema_history` | Historique technique des migrations appliquées |

Les quantités de collection et les quantités d’un deck sont des données distinctes. Les stocks sont comparés à la demande ; la création d’un deck ne déplace pas les lignes de `possessions`.

### Historique des migrations

| Version | Évolution |
| --- | --- |
| V1 | Utilisateurs |
| V2 | Extensions |
| V3 | Cartes |
| V4 | Images recto et verso |
| V5 | Coût et affinités |
| V6 | Possessions |
| V7 | Quantité foil |
| V8 | Indicateur hyperspace |
| V9 | Relation entre variante et carte standard |
| V10 | Verrou d’administration |
| V11 | Type de carte et decks |
| V12 | Recherches et vitrines |
| V13 | Publication communautaire des decks |

Les migrations sont appliquées dans l’ordre par Flyway au démarrage. `ddl-auto=validate` vérifie le schéma ; il ne remplace pas les migrations.

Ne modifiez pas une migration déjà appliquée pour faire évoluer la base : ajoutez une nouvelle version. N’exécutez pas manuellement une migration que Flyway doit appliquer. Un écart de checksum doit être expliqué avant toute tentative de réparation de l’historique.

<a id="securite"></a>
## 14. Sécurité et confidentialité

Le fonctionnement repose notamment sur :

- le hachage des mots de passe via le `PasswordEncoder` de Spring Security ;
- des inscriptions limitées à un compte USER actif ;
- la protection CSRF sur les formulaires et les requêtes de modification ;
- le contrôle du propriétaire pour les opérations sur les decks ;
- la séparation des possessions et recherches par utilisateur ;
- la vérification des rôles et de l’état actif ;
- des transactions et verrous pour limiter les incohérences lors de modifications simultanées ;
- des vues publiques dédiées qui n’utilisent pas la vue privée et ses stocks.

Les réglages de vitrine et de publication communautaire déterminent les données visibles publiquement. Un cœur de recherche ne rend pas à lui seul le profil public.

Les réglages de développement proposés limitent les ports MySQL et phpMyAdmin à la machine locale. Une mise en ligne demande une configuration de déploiement adaptée, notamment HTTPS et la gestion des secrets ; la configuration locale n’est pas une procédure complète de production.

<a id="maintenance"></a>
## 15. Maintenance et sauvegardes

### Construire et lancer le JAR

Depuis le dossier contenant `pom.xml`, avec Java 21 et Maven :

```bash
mvn clean package
java -jar target/gestion-1.0-SNAPSHOT.jar
```

Le processus Java doit toujours disposer de `MYSQL_PASSWORD`. MySQL doit être démarré et accessible au moment où l’application initialise sa base.

### Arrêter les services locaux

```bash
docker compose stop
```

Un arrêt des services conserve les données du volume. Évitez `docker compose down -v` si vous souhaitez conserver la base : l’option `-v` supprime les volumes gérés par cette configuration.

### Sauvegarder la base

Avant une évolution de schéma ou une intervention importante :

1. Ouvrez phpMyAdmin et sélectionnez `gestion_swu`.
2. Exportez la base complète au format SQL, avec sa structure et ses données.
3. Conservez le fichier hors du volume Docker utilisé par la base.
4. Vérifiez périodiquement qu’une restauration fonctionne sur une base de test.

La sauvegarde doit inclure les comptes, les possessions, les decks, les recherches, les vitrines et l’historique Flyway. Un catalogue réimportable ne suffit pas à restaurer les données personnelles des joueurs.

### Faire évoluer l’application

Pour un changement de modèle : créez une migration suivante, adaptez l’entité et le service, puis vérifiez le parcours concerné. Pour un changement visuel partagé : privilégiez le fragment ou le composant commun plutôt que plusieurs copies divergentes.

Après une mise à jour des styles ou scripts, rechargez sans cache avec **Ctrl + F5**. Les paramètres de version des ressources aident à distinguer leurs évolutions.

<a id="depannage"></a>
## 16. Dépannage

| Symptôme | Vérification et action |
| --- | --- |
| MySQL refuse la connexion | Vérifier `docker compose ps`, le port hôte `3310`, le compte `swu_app` et la valeur de `MYSQL_PASSWORD` transmise à Java. |
| L’application ne résout pas `${MYSQL_PASSWORD}` | Définir la variable dans la configuration de lancement IntelliJ ou dans le terminal ; le `.env` de Compose n’est pas chargé automatiquement par Java. |
| phpMyAdmin fonctionne mais Spring Boot ne se connecte pas | phpMyAdmin utilise `mysql:3306` dans Docker ; Java sur l’hôte utilise `localhost:3310`. Vérifier aussi le mot de passe du compte applicatif. |
| Changer `.env` ne change pas le mot de passe MySQL | Le volume existant contient déjà les comptes initialisés. Modifier le compte MySQL et sa configuration de façon cohérente, sans supprimer le volume pour contourner le problème. |
| Port `8090` déjà utilisé | Arrêter l’ancien processus Java ou choisir un autre `server.port`, puis utiliser l’URL correspondante. |
| `Ambiguous mapping` au démarrage | Rechercher deux contrôleurs actifs déclarant le même chemin et la même méthode HTTP. Une classe renommée `Old` reste active si son annotation est conservée. |
| `UnsupportedClassVersionError` ou méthodes Java introuvables à la compilation | Vérifier le Java utilisé par Maven, le SDK du projet et celui du lancement : Java 21 est requis. |
| Échec de validation Flyway ou Hibernate | Lire l’erreur de migration ou de schéma. Vérifier la présence et l’ordre des migrations avant toute modification manuelle de l’historique. |
| Aucune carte dans le catalogue | Créer l’extension puis importer ses cartes ; contrôler le bilan et le code de l’extension. |
| Leader ou base absents de l’éditeur | Réimporter l’extension pour renseigner les types de cartes. |
| Hyperspace séparée de sa carte standard | Vérifier l’import et le rattachement `carte_standard_id` ; le regroupement ne se fait pas uniquement par le titre. |
| Numéro introuvable en ajout rapide | Vérifier l’extension, le numéro imprimé et la finition choisie, puis l’import de la variante. |
| Images absentes | Vérifier l’URL importée et l’accès réseau à l’hébergement des images ; un rechargement CSS ne répare pas une URL manquante. |
| Caméra inactive sur téléphone | Vérifier HTTPS, l’autorisation caméra et la disponibilité de `getUserMedia`. Sinon choisir un fichier. |
| OCR lent ou ambigu | Vérifier le chargement du lecteur, la netteté, les reflets et la lisibilité du numéro ; corriger la référence manuellement si nécessaire. |
| CSS ou JavaScript semblent anciens | Recharger sans cache et vérifier que les fichiers sont bien dans `static/css` ou `static/js`. |
| `Identifier ... has already been declared` | Vérifier qu’un script n’est pas inclus deux fois et qu’un ancien gestionnaire n’est pas chargé avec son remplaçant. |
| Une page remonte après un ajout | Vérifier le chargement de `position-retour.js` et l’accès au stockage de l’onglet. Le retour de position n’est pas disponible si ce stockage est bloqué. |
| Réponse 403 à une modification | Vérifier la session, les droits et le jeton CSRF ; ne pas désactiver globalement CSRF pour contourner l’erreur. |
| Un deck retiré reste visible dans la vitrine | Le partage de vitrine est indépendant de la publication communautaire. Modifier aussi le réglage de vitrine si ce partage n’est plus souhaité. |

Les journaux Spring Boot précisent les erreurs de connexion, d’import, de migration et de démarrage. L’onglet Réseau et la console du navigateur aident à diagnostiquer les images, la caméra, l’OCR et les scripts.

<a id="verification"></a>
## 17. Vérification du fonctionnement

Après une installation ou une mise à jour, ce parcours permet de vérifier les fonctions principales :

1. Démarrer MySQL et l’application, puis contrôler l’absence d’erreur de migration.
2. Se connecter avec l’e-mail, puis avec le pseudo.
3. Importer une extension et vérifier quelques fiches, images et filtres.
4. Ajouter un exemplaire non foil, un foil et une hyperspace d’une même famille ; contrôler le total et les finitions.
5. Ajuster une quantité dans la galerie et vérifier le retour au même endroit.
6. Marquer une carte d’un cœur et la retrouver dans les recherches.
7. Créer un deck, choisir leader/base et comparer les cartes nécessaires à la collection.
8. Tester les galeries du deck et le cœur d’une carte manquante.
9. Publier un deck et consulter son URL avec un navigateur déconnecté.
10. Retirer sa publication et vérifier que cette URL n’est plus accessible.
11. Vérifier séparément les rubriques autorisées et interdites d’une vitrine.
12. Tester le menu, le sous-menu d’ajout et les fenêtres sur téléphone.

Les correctifs ont fait l’objet de vérifications ciblées de compilation, de persistance sur H2 en mode MySQL, de rendu Thymeleaf, de sécurité et de comportement dans Chromium. Ces vérifications ne remplacent pas un essai avec la base MySQL de l’installation.

La présence d’un dossier `src/test` ne garantit pas une suite de tests automatisés complète. Ne pas considérer un simple succès de compilation comme une validation de tous les parcours métier.

<a id="limites"></a>
## 18. Limites et évolutions possibles

### Limites actuelles

- L’OCR peut demander une correction manuelle et ne reconnaît pas une carte uniquement à son illustration.
- Les imports et actualités dépendent de la disponibilité et de la structure de la source externe.
- Les illustrations dépendent de leurs URL distantes.
- L’éditeur ne valide pas encore l’intégralité des règles de tournoi, les restrictions de construction ni les pénalités d’affinité.
- Les stocks ne sont pas réservés entre les decks.
- Les cœurs représentent une liste de cartes recherchées, sans quantité cible ni finition demandée.
- Les fonctions de marché, demandes d’échange, tournois et decks du moment ne sont pas opérationnelles.
- Copier un deck communautaire dans ses decks personnels n’est pas encore implémenté.

### Pistes d’évolution

La copie d’un deck communautaire, des recherches par finition et quantité, une validation de construction ou des échanges entre joueurs peuvent être ajoutés ultérieurement. Ces pistes ne décrivent pas des fonctions déjà disponibles.

---

**Projet communautaire indépendant.** Gestion SWU n’est pas affilié à Lucasfilm ou Fantasy Flight Games. Les cartes, illustrations, noms et contenus officiels restent la propriété de leurs ayants droit.

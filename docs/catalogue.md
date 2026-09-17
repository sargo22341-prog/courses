# Catalogue et autocomplétion

[← Documentation](README.md)

L'autocomplétion est **100 % locale** : aucune requête n'est faite, ni pendant la frappe, ni pour
tenir le catalogue à jour. Le catalogue combine trois sources, toutes stockées dans Room :

| Source | Origine | Mise à jour |
| --- | --- | --- |
| Catalogue de base | `assets/catalog/seed.json`, 266 produits traduits dans les six langues | avec l'application |
| OpenFoodFacts | `assets/catalog/taxonomy-<langue>.json`, généré depuis la taxonomie des catégories | avec l'application |
| Produits personnalisés | créés par l'utilisateur ou venus de Home Assistant | à la saisie |

Les deux premières sont des `BundledCatalogSource` : des fichiers livrés avec l'application, lus par
`CatalogImporter` quand leur version ou la langue change, jamais à chaque démarrage. L'application
ne contacte **jamais** OpenFoodFacts ([ADR 0025](adr/0025-catalogue-genere-a-la-compilation.md)).

## Génération du catalogue OpenFoodFacts

`scripts/generate-catalog.py` est lancé à la main sur un poste de développement, jamais par le build
ni par l'application. Il lit deux fichiers publics sous licence ODbL :

- `static.openfoodfacts.org/data/taxonomies/categories.json` (~4,6 Mo) : les noms par langue, les
  parents, les appellations protégées et les origines de ~14 700 catégories ;
- `raw.githubusercontent.com/openfoodfacts/openfoodfacts-server/…/categories.txt` (~3,6 Mo) : la
  même taxonomie sous sa forme source, **seule à contenir les synonymes** par langue
  (`fr: Laits, lait`). Le JSON publié ne garde que le premier nom de chaque ligne.

Pourquoi les catégories plutôt que les produits : la base produits complète pèse plusieurs Go et
contient surtout des références de marque. Les catégories (`Laits demi-écrémés`, `Tomates cerise`…)
correspondent à ce qu'on écrit sur une liste de courses.

Le script écrit un fichier par langue, commité au dépôt :

```
python scripts/generate-catalog.py            # tout, du téléchargement aux vérifications
python scripts/generate-catalog.py --offline  # réutilise le cache de build/catalog-sources
python scripts/generate-catalog.py --no-verify  # sans les vérifications Gradle
```

Tout est automatique sauf le commit. Le script compare les produits qu'il vient de calculer à ceux
des fichiers déjà présents et, **seulement s'ils diffèrent**, monte la version du catalogue (dans
les fichiers et dans `AssetTaxonomyCatalogSource.VERSION`), ajoute la ligne de note dans
`RELEASE_NOTES.md` et lance les vérifications Gradle d'`AGENTS.md`. Sans changement réel, il ne
touche à rien : la date de génération seule ne crée pas de diff. Un fichier absent est réécrit sans
monter la version.
Nettoyage appliqué : nom obligatoire dans la langue ; exclusion des appellations protégées (AOP/IGP)
et des entrées liées à une origine ; au plus 4 mots et 40 caractères ; aucun chiffre
(`Laits 2ème âge`) ; dédoublonnage sur le nom normalisé en gardant l'entrée la plus générique.

Données extraites pour chaque produit :

| Champ | Contenu |
| --- | --- |
| `id` | identifiant de taxonomie (`en:milks`), le même dans toutes les langues |
| `name` | nom dans la langue du fichier |
| `category` | nom du rayon d'origine dans la taxonomie, affiché sous la suggestion |
| `score` | score de base : plus l'entrée est proche d'une racine, plus elle est générique |
| `section` | rayon de l'application, lu sur **tout le graphe de parents** ([Catégories](categories.md)) |
| `aliases` | synonymes de la taxonomie dans cette langue |

La version du catalogue n'existe qu'à un endroit, `AssetTaxonomyCatalogSource.VERSION` : le script
la lit et l'incrémente lui-même. C'est elle qui décide du réimport — un catalogue importé par une
version précédente de l'application est relu au prochain démarrage. Un test vérifie qu'elle
correspond à celle inscrite dans les six fichiers.

Résultat en septembre 2026 :

| Langue | Produits | Alias | Rangés | Fichier |
| --- | --- | --- | --- | --- |
| français | 6 554 | 3 432 | 98,8 % | 988 Ko |
| anglais | 7 585 | 3 161 | 98,6 % | 1 075 Ko |
| allemand | 3 159 | 1 312 | 98,1 % | 447 Ko |
| espagnol | 2 601 | 1 083 | 98,0 % | 376 Ko |
| italien | 2 257 | 436 | 97,9 % | 299 Ko |
| portugais | 882 | 119 | 96,0 % | 117 Ko |

Soit environ 570 Ko ajoutés au paquet, les fichiers y étant compressés.

## Import dans Room

`CatalogImporter` compare, pour chaque source, la version et la langue déjà importées
(`seed_version`, `open_food_facts_version`… dans DataStore) à celles de la source. Si elles
correspondent, **le fichier n'est même pas lu**. Sinon, l'import est transactionnel : les produits
sont insérés ou mis à jour par leur identifiant, puis toutes les lignes que l'import n'a pas
écrites sont supprimées — c'est ainsi qu'un produit sans nom dans la nouvelle langue disparaît.

Stockage : `catalog_products` (nom, nom normalisé indexé, catégorie, rayon, source, score de base,
version d'import), `catalog_aliases` (alias et alias normalisé), et `product_usage` (habitudes,
**séparées** du catalogue pour ne jamais être effacées par un import,
[ADR 0008](adr/0008-habitudes-separees-du-catalogue.md)).

Les identifiants ne dépendent pas de la langue : habitudes d'usage et liens des articles sont
conservés d'un import à l'autre.

**Réglages → Catalogue alimentaire** n'affiche plus que le nombre de produits disponibles hors
connexion et l'attribution Open Food Facts : il n'y a plus rien à synchroniser.

## Recherche et classement

`SearchSuggestionsUseCase` (100 % local), exécuté hors du fil principal (dispatcher `Default`) 60 ms
après la dernière frappe ; une frappe suivante annule la recherche en cours :

1. Normalisation (`TextNormalizer`) : minuscules, sans accents, `œ → oe`, `ß → ss`, ponctuation →
   espaces, identique dans toutes les langues. Seule la saisie est normalisée à chaque frappe : les
   noms et alias du catalogue sont comparés sous leur forme normalisée enregistrée dans Room.
2. Présélection SQL : nom ou alias contenant la saisie (300 candidats au plus), en ne lisant que
   les colonnes utiles au classement.
3. Classement (`SuggestionRanker`), par paliers de 1 000 points que les bonus ne peuvent pas
   franchir : **exact > préfixe > début de mot > partiel > approximatif**. Un produit trouvé
   seulement par un alias perd 50 points. À palier égal : fréquence d'utilisation (+20 par ajout,
   plafonné), récence (+150 sur 3 jours, +100 sur 14 jours, +40 sur 60 jours), score de base du
   catalogue, puis nom le plus court.
4. Si les résultats sont insuffisants : recherche tolérante aux fautes (`FuzzyMatcher`, distance
   d'édition avec transpositions sur les préfixes de mots : `lati → Lait`, `tomatos → Tomates`),
   sur les produits dont un mot commence par la même lettre (3 000 au plus : en français, environ
   2 400 des 6 800 produits partagent la lettre la plus fréquente, « d » de « de »).

Les alias viennent du catalogue de base (écrits à la main) et des synonymes de la taxonomie :
« patates » trouve « Pommes de terre », « alimentation infantile » trouve « Aliments pour bébé ».

## Ajout d'un article

- Si rien ne correspond exactement, `+ Ajouter « … »` crée un **produit personnalisé** local,
  proposé ensuite comme les autres.
- Chaque ajout alimente les statistiques d'usage, y compris quand l'historique est désactivé
  dans les réglages (le réglage ne masque que la liste).

## Historique

`ProductHistoryUseCase` lit les 100 produits les plus ajoutés (`product_usage` joint au catalogue,
par nombre d'ajouts puis ajout le plus récent ; l'usage d'un produit disparu du catalogue est
ignoré), retire ceux déjà à acheter dans la liste (même produit du catalogue ou même nom normalisé,
pour les articles venus de Home Assistant pas encore rattachés), dédoublonne les noms et en garde
30. La liste suit Room en direct. « Vider l'historique » supprime toutes les lignes de
`product_usage` ; le catalogue et les produits personnalisés ne sont pas touchés.
- Ajouter un produit déjà présent incrémente sa quantité ; ajouter un produit déjà acheté le remet
  « à acheter » (`AddItemUseCase`).

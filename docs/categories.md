# Catégories

[← Documentation](README.md)

Réglage **Réglages → Liste de courses → Ranger les articles par catégorie**, **désactivé par
défaut**. Activé, les articles à acheter sont regroupés sous un en-tête par rayon : pictogramme
(emoji, [ADR 0014](adr/0014-pictogrammes-en-emoji.md)), nom et couleur propres à la catégorie.
Les articles achetés restent dans « Achetés ».

## Rayons

| Ordre | Catégorie | Source OpenFoodFacts (exemples d'identifiants) |
| --- | --- | --- |
| 1 | 🥕 Fruits et légumes | `en:fruits`, `en:vegetables`, `en:nuts`, `en:potatoes` |
| 2 | 🥖 Boulangerie | `en:breads`, `en:viennoiseries`, `en:pastries` |
| 3 | 🧀 Produits laitiers et œufs | `en:dairies`, `en:eggs`, `en:cheeses` |
| 4 | 🥩 Viandes et poissons | `en:meats-and-their-products`, `en:seafood`, `en:meat-alternatives` |
| 5 | 🍱 Traiteur et plats préparés | `en:meals`, `en:sandwiches`, `en:pizzas-pies-and-quiches` |
| 6 | 🥫 Épicerie salée | `en:condiments`, `en:canned-foods`, `en:cereals-and-potatoes` |
| 7 | 🍫 Épicerie sucrée | `en:sweet-snacks`, `en:confectioneries`, `en:jams` |
| 8 | 🧊 Surgelés | `en:frozen-foods`, `en:ice-creams-and-sorbets` |
| 9 | 🥤 Boissons | `en:beverages-and-beverages-preparations` |
| 10 | 🧴 Hygiène et maison | catalogue de base uniquement (la taxonomie ne couvre que l'alimentaire) |
| 11 | 🍼 Bébé et animaux | `en:baby-foods` ; croquettes, litière… du catalogue de base |
| 12 | 🛒 **Autres** | tout article non reconnu, notamment les produits créés à la main |

## Import

Le rayon des produits OpenFoodFacts est calculé une fois pour toutes par
`scripts/generate-catalog.py` ([Catalogue](catalogue.md)), puis livré dans les fichiers
`assets/catalog/taxonomy-<langue>.json`. Le script parcourt **tout le graphe de parents** d'une
catégorie, par niveaux, et retient le premier identifiant connu de sa table : `en:breads` l'emporte
sur la racine `en:plant-based-foods-and-beverages`, et un produit qu'une branche de parents ne place
pas est placé par l'autre (98,8 % des produits français contre 97,7 % avec une seule branche). Le
catalogue de base déclare le rayon de chaque section (`seed.json`, version 3). Stockage : colonne
`catalog_products.groceryCategory`.

## Rangement d'un article

La catégorie est **calculée**, pas stockée sur l'article
([ADR 0010](adr/0010-categorie-calculee-par-nom.md)) :

1. **Par nom** (`GroupItemsByCategoryUseCase`) : un article prend le rayon du produit du catalogue
   qui porte **son nom** dans la langue de l'application, au singulier ou au pluriel
   (`CategoryNameKeys` et les terminaisons régulières de chaque langue, `WordForms` : `Tomate`
   trouve `Tomates`, `pomodoro` trouve `Pomodori`, `limón` trouve `Limones`). Le rangement suit
   donc les renommages et s'applique de la même façon aux articles tapés à la main et aux
   **articles créés dans Home Assistant**, dès que la taxonomie ou le catalogue de base connaît ce
   nom. Si les deux le connaissent, le catalogue de base l'emporte.
2. **Sinon, par produit associé** : l'article prend le rayon du produit auquel il est rattaché
   (`shopping_items.catalogProductId`). C'est ce qui garde à sa place un article écrit dans
   l'ancienne langue (« Lait » une fois l'application en anglais, rattaché à `seed:lait`, désormais
   nommé « Milk »).
3. **Sinon** : « Autres ».

Le rangement se met à jour tout seul après un import du catalogue (requête Room observée).

## Rattachement des articles

`LinkItemsToCatalogUseCase`, `ItemCatalogLinkResolver` — au démarrage puis après chaque import
(catalogue de base ou taxonomie, donc après tout changement de langue), pour les articles de
toutes les listes :

1. un produit du catalogue portant le nom de l'article ;
2. à défaut, le produit déjà associé s'il existe encore dans le catalogue ;
3. à défaut, un produit personnalisé de même nom (créé si besoin, comme à la saisie).

Le produit donne à l'article son rayon et sa catégorie OpenFoodFacts (identifiant de taxonomie
`en:…` et catégorie parente). L'écriture ne touche que le lien, seulement si l'article n'a pas été
renommé entre-temps : ce n'est pas une modification synchronisée, rien n'est envoyé à Home
Assistant. Renommer un article (dans l'application ou dans Home Assistant) retire son lien, sauf si
seules la casse, les accents ou la ponctuation changent ; il est retrouvé au rattachement suivant.

**Articles des listes Mealie** : leur texte enveloppe l'aliment dans des mots de recette (« gousse
ail », « graines de sésame ou selon le goût »). À la synchronisation, ils sont rattachés au produit
du catalogue de base ou OpenFoodFacts que nomme la plus longue suite de mots de leur texte, par son
nom ou un alias, au singulier ou au pluriel (`FindProductInTextUseCase`, `ProductNameWindows`) ; un
produit personnalisé n'est jamais choisi ainsi. Leur nom ne portant pas celui du produit, ils sont
rangés par le produit associé ([Rangement d'un article](#rangement-dun-article), étape 2). Un
article Mealie encore rattaché à un produit personnalisé est de nouveau cherché à chaque
synchronisation ([Home Assistant](home-assistant.md#listes-mealie)).
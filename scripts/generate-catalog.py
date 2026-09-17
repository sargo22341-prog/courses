#!/usr/bin/env python3
"""Génère le catalogue alimentaire embarqué dans l'application à partir de la taxonomie
OpenFoodFacts des catégories.

Ce script tourne sur un poste de développement, jamais dans l'application ni dans le build : il
écrit `app/src/main/assets/catalog/taxonomy-<langue>.json`, un fichier par langue de l'interface,
qui est ensuite relu et commité. L'application n'appelle donc plus OpenFoodFacts
(docs/adr/0025-catalogue-genere-a-la-compilation.md).

Deux sources, toutes deux publiques et sous licence ODbL :

- `categories.json` (CDN OpenFoodFacts) : noms par langue, parents, appellations protégées,
  origines. C'est le fichier que l'application téléchargeait auparavant.
- `categories.txt` (dépôt openfoodfacts-server) : la même taxonomie sous sa forme source, seule à
  contenir les **synonymes** par langue (`fr: Laits, lait`). Le JSON du CDN les perd.

Usage :

    python scripts/generate-catalog.py                 # télécharge les sources puis génère
    python scripts/generate-catalog.py --offline       # réutilise le cache déjà téléchargé
    python scripts/generate-catalog.py --cache-dir DIR # emplacement du cache (défaut : build/catalog-sources)

Après génération, incrémenter `AssetTaxonomyCatalogSource.VERSION` **et** `FORMAT_VERSION` ci-dessous
(un test vérifie qu'ils correspondent), puis relancer les tests.
"""

from __future__ import annotations

import argparse
import json
import re
import sys
import unicodedata
import urllib.request
from collections import OrderedDict
from datetime import date
from pathlib import Path

# Version du format écrit dans chaque fichier. Doit rester égale à AssetTaxonomyCatalogSource.VERSION :
# l'incrémenter fait réimporter le catalogue au prochain démarrage.
FORMAT_VERSION = 1

CATEGORIES_JSON_URL = "https://static.openfoodfacts.org/data/taxonomies/categories.json"
CATEGORIES_TXT_URL = (
    "https://raw.githubusercontent.com/openfoodfacts/openfoodfacts-server/main/taxonomies/food/categories.txt"
)
USER_AGENT = "courses-catalog-generator (https://github.com/sargo22341-prog/courses)"

# Langues de l'interface (AppLanguage.tag).
LANGUAGES = ["de", "en", "es", "fr", "it", "pt"]

# Mêmes règles de nettoyage qu'avant : ce qui s'écrit sur une liste de courses est court et sans
# chiffre. « Laits 2ème âge », « Laits entiers pasteurisés de montagne bio » sont écartés.
MAX_NAME_LENGTH = 40
MAX_WORDS = 4
MAX_DEPTH = 15
MAX_BASE_SCORE = 5

# Rayon d'une catégorie OpenFoodFacts, lu sur sa chaîne de parents : le premier identifiant connu
# gagne, du plus précis au plus général. Les rayons « Hygiène et maison » et « Bébé et animaux » ne
# sont alimentés que par le catalogue de base : la taxonomie ne couvre que l'alimentaire.
SECTIONS: "OrderedDict[str, list[str]]" = OrderedDict(
    [
        (
            "FRUITS_VEGETABLES",
            [
                "en:fruits-and-vegetables-based-foods", "en:fruits", "en:vegetables", "en:fresh-vegetables",
                "en:fresh-fruits", "en:dried-fruits", "en:nuts", "en:mushrooms", "en:potatoes", "en:aromatic-plants",
                "en:legumes-and-their-products",
            ],
        ),
        (
            "BAKERY",
            [
                "en:breads", "en:viennoiseries", "en:pastries", "en:brioches", "en:crepes-and-galettes",
                "en:sweet-pies", "en:pies", "en:cakes",
            ],
        ),
        (
            "DAIRY_EGGS",
            [
                "en:dairies", "en:eggs", "en:eggs-and-their-products", "en:cheeses", "en:milks", "en:yogurts",
                "en:butters", "en:creams", "en:dairy-desserts",
            ],
        ),
        (
            "MEAT_FISH",
            [
                "en:meats-and-their-products", "en:meats", "en:seafood", "en:fishes", "en:fish-and-meat-and-eggs",
                "en:poultries", "en:prepared-meats", "en:meat-alternatives", "en:caviar-substitutes",
            ],
        ),
        (
            "DELI",
            [
                "en:meals", "en:sandwiches", "en:pizzas-pies-and-quiches", "en:terrines", "en:pastas-and-dumplings",
                "en:meal-kits", "en:breaded-products",
            ],
        ),
        ("FROZEN", ["en:frozen-foods", "en:ice-creams-and-sorbets", "en:frozen-desserts"]),
        (
            "DRINKS",
            ["en:beverages-and-beverages-preparations", "en:beverages", "en:alcoholic-beverages", "en:waters", "en:syrups"],
        ),
        (
            "SWEET_GROCERY",
            [
                "en:sweet-snacks", "en:biscuits-and-cakes", "en:confectioneries", "en:chocolates",
                "en:cocoa-and-its-products", "en:desserts", "en:breakfasts", "en:bee-products", "en:sweeteners",
                "en:sweet-spreads", "en:jams", "en:fruit-preserves", "en:compotes", "en:dessert-sauces",
                "en:breakfast-cereals", "en:sugars",
            ],
        ),
        (
            "SAVORY_GROCERY",
            [
                "en:salty-snacks", "en:appetizers", "en:condiments", "en:sauces", "en:canned-foods",
                "en:cereals-and-potatoes", "en:pastas", "en:rices", "en:legumes", "en:fats", "en:dried-products",
                "en:cooking-helpers", "en:broths", "en:spreads", "en:chips-and-fries", "en:snacks",
                "en:plant-based-foods", "en:plant-based-foods-and-beverages", "en:cereals-and-their-products",
                "en:flours", "en:spices", "en:vinegars", "en:soups",
            ],
        ),
        ("BABY_PETS", ["en:baby-foods", "en:baby-milks"]),
    ]
)

SECTION_BY_ID = {entry_id: section for section, ids in SECTIONS.items() for entry_id in ids}

DIACRITICS = re.compile(r"[̀-ͯ]+")
NON_ALPHANUMERIC = re.compile(r"[^a-z0-9]+")
# Une ligne de langue de categories.txt : « fr: Laits, lait ». Les propriétés (« wikidata:en: … »)
# ont toutes un préfixe de plus de trois lettres et ne peuvent pas être confondues avec elle.
LANGUAGE_LINE = re.compile(r"^([a-z]{2,3}):\s*(.+)$")
SYNONYM_LINE = re.compile(r"^synonyms:([a-z]{2,3}):\s*(.+)$")


def normalize(text: str) -> str:
    """Forme canonique de TextNormalizer : minuscules, sans accent, ligatures étendues."""
    lower = text.lower().replace("œ", "oe").replace("æ", "ae").replace("ß", "ss")
    without_accents = DIACRITICS.sub("", unicodedata.normalize("NFD", lower))
    return NON_ALPHANUMERIC.sub(" ", without_accents).strip()


def download(url: str, destination: Path, offline: bool) -> Path:
    if offline:
        if not destination.exists():
            sys.exit(f"--offline mais {destination} est absent : lancer une fois sans --offline.")
        return destination
    destination.parent.mkdir(parents=True, exist_ok=True)
    print(f"Téléchargement de {url}")
    request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(request, timeout=180) as response, destination.open("wb") as file:
        file.write(response.read())
    return destination


def read_synonyms(path: Path) -> "dict[str, dict[str, list[str]]]":
    """Synonymes de categories.txt, par langue puis par nom canonique normalisé.

    Le JSON du CDN ne garde que le premier nom de chaque ligne de langue ; les suivants sont les
    synonymes, la seule source d'alias que la taxonomie offre. Le rattachement se fait sur le nom
    canonique plutôt que sur l'identifiant d'entrée, que seul le serveur OpenFoodFacts sait dériver.
    """
    by_language: "dict[str, dict[str, list[str]]]" = {language: {} for language in LANGUAGES}
    # Nom canonique de chaque langue du bloc en cours ; les blocs sont séparés par une ligne vide.
    canonical_of_block: "dict[str, str]" = {}

    for raw in path.read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if not line:
            canonical_of_block.clear()
            continue
        if line.startswith("#") or line.startswith("<"):
            continue
        extra = SYNONYM_LINE.match(line)
        if extra:
            language, values = extra.group(1), extra.group(2)
            canonical = canonical_of_block.get(language)
            if canonical and language in by_language:
                by_language[language].setdefault(canonical, []).extend(part.strip() for part in values.split(","))
            continue
        match = LANGUAGE_LINE.match(line)
        if not match:
            continue
        language, values = match.group(1), match.group(2)
        names = [part.strip() for part in values.split(",") if part.strip()]
        if not names:
            continue
        canonical = normalize(names[0])
        canonical_of_block[language] = canonical
        if language in by_language and len(names) > 1:
            by_language[language].setdefault(canonical, []).extend(names[1:])
    return by_language


def is_useful(entry: dict, name: str) -> bool:
    return (
        "protected_name_type" not in entry
        and "origins" not in entry
        and len(name) <= MAX_NAME_LENGTH
        and not any(character.isdigit() for character in name)
        and len([word for word in name.split(" ") if word.strip()]) <= MAX_WORDS
    )


def ancestors_of(entry_id: str, entries: dict) -> "list[str]":
    """Ancêtres du parent direct à la racine, en suivant le premier parent connu."""
    chain: "list[str]" = []
    parents = entries.get(entry_id, {}).get("parents", [])
    current = next((parent for parent in parents if parent in entries), None)
    while current and current != entry_id and current not in chain and len(chain) < MAX_DEPTH:
        chain.append(current)
        parents = entries.get(current, {}).get("parents", [])
        current = next((parent for parent in parents if parent in entries), None)
    return chain


def section_of(entry_id: str, entries: dict) -> "str | None":
    """Rayon lu sur tout le graphe de parents, par niveaux : le plus proche gagne.

    La taxonomie a plusieurs parents par entrée (« Biscuits aux flocons d'avoine au chocolat » est à
    la fois un biscuit au chocolat et un biscuit aux flocons d'avoine). Suivre le seul premier
    parent laissait sans rayon des produits que l'autre branche sait placer.
    """
    level = [entry_id]
    visited = {entry_id}
    for _ in range(MAX_DEPTH):
        for candidate in level:
            section = SECTION_BY_ID.get(candidate)
            if section:
                return section
        following = []
        for candidate in level:
            for parent in entries.get(candidate, {}).get("parents", []):
                if parent in entries and parent not in visited:
                    visited.add(parent)
                    following.append(parent)
        if not following:
            return None
        level = following
    return None


def aliases_of(name: str, synonyms: "list[str]") -> "list[str]":
    """Synonymes utilisables : mêmes règles de forme que les noms, sans répéter le nom du produit."""
    seen = {normalize(name)}
    kept = []
    for synonym in synonyms:
        cleaned = synonym.strip()
        if not cleaned or len(cleaned) > MAX_NAME_LENGTH:
            continue
        if any(character.isdigit() for character in cleaned):
            continue
        if len([word for word in cleaned.split(" ") if word.strip()]) > MAX_WORDS:
            continue
        key = normalize(cleaned)
        if not key or key in seen:
            continue
        seen.add(key)
        kept.append(cleaned)
    return kept


def build(language: str, entries: dict, synonyms: "dict[str, list[str]]") -> "list[dict]":
    """Produits du catalogue dans une langue, dédoublonnés sur leur nom normalisé."""
    by_name: "dict[str, dict]" = {}
    for entry_id, entry in entries.items():
        name = (entry.get("name") or {}).get(language)
        name = name.strip() if name else None
        if not name or not is_useful(entry, name):
            continue
        chain = ancestors_of(entry_id, entries)
        shelf = chain[-2] if len(chain) >= 2 else (chain[-1] if chain else None)
        product = {
            "id": entry_id,
            "name": name[0].upper() + name[1:],
            "score": max(MAX_BASE_SCORE - len(chain), 0),
        }
        category = (entries.get(shelf, {}).get("name") or {}).get(language) if shelf else None
        if category:
            product["category"] = category
        section = section_of(entry_id, entries)
        if section:
            product["section"] = section
        product_aliases = aliases_of(product["name"], synonyms.get(normalize(name), []))
        if product_aliases:
            product["aliases"] = product_aliases
        key = normalize(product["name"])
        existing = by_name.get(key)
        # À nom égal, l'entrée la plus générique l'emporte : « Laits » plutôt qu'un doublon profond.
        if existing is None or existing["score"] < product["score"]:
            by_name[key] = product
    return sorted(by_name.values(), key=lambda product: product["id"])


def write(path: Path, language: str, products: "list[dict]", taxonomy_date: str) -> None:
    """Un produit par ligne : le fichier reste relisible et un diff montre les produits changés."""
    header = {
        "version": FORMAT_VERSION,
        "language": language,
        "source": "OpenFoodFacts categories taxonomy (ODbL)",
        "generated": taxonomy_date,
    }
    lines = [json.dumps(product, ensure_ascii=False, separators=(", ", ": ")) for product in products]
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8", newline="\n") as file:
        file.write("{\n")
        for key, value in header.items():
            file.write(f"  {json.dumps(key)}: {json.dumps(value, ensure_ascii=False)},\n")
        file.write('  "products": [\n')
        file.write(",\n".join(f"    {line}" for line in lines))
        file.write("\n  ]\n}\n")


def main() -> None:
    root = Path(__file__).resolve().parent.parent
    parser = argparse.ArgumentParser(description="Génère le catalogue embarqué depuis la taxonomie OpenFoodFacts.")
    parser.add_argument("--cache-dir", type=Path, default=root / "build" / "catalog-sources")
    parser.add_argument("--offline", action="store_true", help="réutilise les fichiers déjà téléchargés")
    parser.add_argument("--out-dir", type=Path, default=root / "app" / "src" / "main" / "assets" / "catalog")
    arguments = parser.parse_args()

    json_path = download(CATEGORIES_JSON_URL, arguments.cache_dir / "categories.json", arguments.offline)
    txt_path = download(CATEGORIES_TXT_URL, arguments.cache_dir / "categories.txt", arguments.offline)

    entries = json.loads(json_path.read_text(encoding="utf-8"))
    synonyms = read_synonyms(txt_path)
    taxonomy_date = date.fromtimestamp(json_path.stat().st_mtime).isoformat()
    print(f"{len(entries)} entrées lues, format {FORMAT_VERSION}")

    for language in LANGUAGES:
        products = build(language, entries, synonyms[language])
        destination = arguments.out_dir / f"taxonomy-{language}.json"
        write(destination, language, products, taxonomy_date)
        alias_count = sum(len(product.get("aliases", [])) for product in products)
        placed = sum(1 for product in products if "section" in product)
        size = destination.stat().st_size // 1024
        print(f"{language}: {len(products):5d} produits, {alias_count:5d} alias, {placed:5d} rangés, {size:4d} Ko")


if __name__ == "__main__":
    main()

<p align="center">
  <a href="README.md">English</a> ·
  <a href="README.fr.md">Français</a> ·
  <a href="README.de.md">Deutsch</a> ·
  <strong>Español</strong> ·
  <a href="README.it.md">Italiano</a> ·
  <a href="README.pt.md">Português</a>
</p>

<p align="center">
  <img src="./docs/icon/courses_icon.svg" width="120" alt="Logotipo de courses" />
</p>

<h1 align="center">courses</h1>

<p align="center">
  Una lista de la compra para Android sencilla, rápida y <strong>sin conexión</strong>,<br />
  con sincronización opcional con Home Assistant.
</p>

<p align="center">
  <a href="https://github.com/sargo22341-prog/courses/releases/latest"><img src="./docs/images/badges/badge_github.png" height="80" alt="Get it on GitHub" /></a>
  <a href="https://apps.obtainium.imranr.dev/redirect?r=obtainium://add/https://github.com/sargo22341-prog/courses"><img src="./docs/images/badges/badge_obtainium.png" height="80" alt="Get it on Obtainium" /></a>
</p>

## Aviso

> [!WARNING]
> Esta aplicación se ha **desarrollado con la ayuda de una inteligencia artificial**.
> El código, las pruebas y la documentación se han generado en gran parte con IA y después se han
> verificado con pruebas automatizadas, pero no todo se ha revisado línea por línea ni validado en
> todas las situaciones reales. Úsala con conocimiento de causa y consulta las
> [limitaciones conocidas](docs/limites-connues.md).

## Presentación

**courses** parte de una idea sencilla: *abrir → escribir → seleccionar → marcar*. La aplicación
arranca directamente en la lista, sin cuenta, sin configuración y sin pantalla de carga. Todo se
guarda en el teléfono: puedes consultar y modificar tus listas al fondo de una tienda, sin red.

Para compartir una lista con la familia o mostrarla en un panel, puede sincronizarse con las
listas `todo` de una instancia autoalojada de **Home Assistant**. Es totalmente opcional.

Funciones principales:

- **varias listas**, con cantidades y unidades (`12`, `1,5 kg`);
- **autocompletado instantáneo y sin conexión**, tolerante a errores de escritura, que aprende
  los productos que compras con más frecuencia;
- **historial** de los productos más añadidos, que se propone al tocar el campo de búsqueda vacío
  (se puede desactivar y borrar en los ajustes);
- **catálogo de alimentos totalmente integrado**: un catálogo base escrito a mano y varios miles
  de productos generados a partir de la taxonomía de
  [OpenFoodFacts](https://world.openfoodfacts.org/), incluidos en la aplicación — no se descarga
  nada y no se envía nada de lo que escribes;
- **orden por sección** (frutas y verduras, panadería, lácteos…), opcional;
- **seis idiomas**: francés, inglés, alemán, español, italiano, portugués;
- **Home Assistant (opcional)**: sincronización en ambos sentidos, en tiempo real con la
  aplicación abierta, sin perder los cambios hechos sin conexión;
- tema claro, oscuro o del sistema, Material 3;
- **sin cuenta, sin analíticas, sin dependencia de los servicios de Google Play**: funciona en
  GrapheneOS.

### Vista previa

| Primer inicio | Lista de la compra | Autocompletado |
| --- | --- | --- |
| ![Pantalla de bienvenida y elección del idioma](docs/images/accueil.png) | ![Lista de la compra](docs/images/liste.png) | ![Sugerencias al escribir](docs/images/autocompletion.png) |

| Orden por sección | Tema oscuro | Ajustes |
| --- | --- | --- |
| ![Artículos ordenados por categoría](docs/images/categories.png) | ![Lista en tema oscuro](docs/images/sombre.png) | ![Ajustes](docs/images/reglages.png) |

Capturas tomadas en un emulador con datos ficticios.

## Instalación

No hay ninguna versión publicada en una tienda de aplicaciones: el APK firmado de cada versión se
adjunta a las releases de GitHub del repositorio, o la aplicación se compila desde el código
fuente.

Requisitos: Android Studio reciente (JDK 21 incluido), SDK de Android 37 y un teléfono o emulador
con **Android 17 (API 37)** como mínimo.

```powershell
$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:installDebug
```

Detalles, verificaciones y release firmada: [Desarrollo](docs/developpement.md).

## Home Assistant en resumen

1. En Home Assistant: Perfil → Seguridad → Tokens de acceso de larga duración → crear un token (la
   aplicación puede escanearlo como código QR).
2. En la aplicación: Ajustes → Home Assistant → dirección, token, **Probar la conexión**.
3. Elegir para cada lista: crearla en Home Assistant, vincularla a una lista existente o
   mantenerla local.

El token se cifra con el Keystore de Android y no se vuelve a mostrar nunca. Guía completa:
[Home Assistant](docs/home-assistant.md).

## Privacidad

- Las listas, el historial y las preferencias se quedan en el teléfono.
- No se contacta ningún servicio externo sin una acción del usuario: el catálogo de alimentos se
  incluye con la aplicación.
- Solo se contacta con Home Assistant si se ha configurado; es el único servidor al que la
  aplicación sabe conectarse.

## Tecnologías

| Capa | Tecnología |
| --- | --- |
| Lenguaje | Kotlin, corrutinas, Flow |
| Interfaz | Jetpack Compose, Material 3, Navigation Compose |
| Datos | Room (única fuente de verdad), DataStore |
| Inyección | Hilt |
| Red | Retrofit, OkHttp (REST y WebSocket), Kotlin Serialization |
| Seguridad | Android Keystore (AES-256-GCM) |
| Escaneo QR | CameraX + ZXing |
| Plataforma | Android 17 (API 37) como mínimo |

## Documentación

Toda la documentación (en francés) está en [`docs/`](docs/README.md):

- [Interfaz](docs/interface.md) · [Funcionamiento sin conexión](docs/hors-ligne.md) ·
  [Idiomas](docs/langues.md)
- [Catálogo y autocompletado](docs/catalogue.md) · [Categorías](docs/categories.md)
- [Home Assistant](docs/home-assistant.md) · [Sincronización](docs/synchronisation.md) ·
  [Estrategia de conflictos](docs/conflits.md)
- [Arquitectura](docs/architecture.md) · [Decisiones de arquitectura (ADR)](docs/adr/README.md)
- [Seguridad y privacidad](docs/securite.md) · [Limitaciones conocidas](docs/limites-connues.md)
- [Desarrollo](docs/developpement.md) · [Pruebas](docs/tests.md) ·
  [Release firmada](docs/release.md)

Reglas de contribución (personas y agentes): [`AGENTS.md`](AGENTS.md).

## Licencia

Copyright © 2026 sargo.

courses es software libre distribuido bajo la **GNU General Public License v3.0**: puedes
redistribuirlo y modificarlo según sus términos; toda versión modificada y redistribuida debe
mantenerse bajo la misma licencia. Texto completo: [`LICENSE`](LICENSE).

Los datos del catálogo proceden de [Open Food Facts](https://world.openfoodfacts.org) y siguen
bajo licencia [ODbL](https://opendatacommons.org/licenses/odbl/1-0/). Los archivos de
`app/src/main/assets/catalog/` son una base derivada generada por `scripts/generate-catalog.py`.

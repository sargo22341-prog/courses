<p align="center">
  <a href="README.md">English</a> ·
  <a href="README.fr.md">Français</a> ·
  <a href="README.de.md">Deutsch</a> ·
  <a href="README.es.md">Español</a> ·
  <a href="README.it.md">Italiano</a> ·
  <strong>Português</strong>
</p>

<p align="center">
  <img src="./docs/icon/courses_icon.svg" width="120" alt="Logótipo do courses" />
</p>

<h1 align="center">courses</h1>

<p align="center">
  Uma lista de compras Android simples, rápida e <strong>offline</strong>,<br />
  com sincronização opcional com o Home Assistant.
</p>

<p align="center">
  <a href="https://github.com/sargo22341-prog/courses/releases/latest"><img src="./docs/images/badges/badge_github.png" height="80" alt="Get it on GitHub" /></a>
  <a href="https://apps.obtainium.imranr.dev/redirect?r=obtainium://add/https://github.com/sargo22341-prog/courses"><img src="./docs/images/badges/badge_obtainium.png" height="80" alt="Get it on Obtainium" /></a>
</p>

## Aviso

> [!WARNING]
> Esta aplicação foi **desenvolvida com a ajuda de inteligência artificial**.
> O código, os testes e a documentação foram produzidos em grande parte por IA e depois
> verificados por testes automatizados, mas nem tudo foi revisto linha a linha nem validado em
> todas as situações reais. Use-a com conhecimento de causa e consulte as
> [limitações conhecidas](docs/limites-connues.md).

## Apresentação

O **courses** parte de uma ideia simples: *abrir → escrever → selecionar → marcar*. A aplicação
abre diretamente na lista, sem conta, sem configuração e sem ecrã de carregamento. Tudo fica
guardado no telemóvel: pode consultar e alterar as suas listas no fundo de uma loja, sem rede.

Para partilhar uma lista com a família ou mostrá-la num painel, ela pode sincronizar-se com as
listas `todo` de uma instância **Home Assistant** auto-hospedada. É totalmente opcional.

Funcionalidades principais:

- **várias listas**, com quantidades e unidades (`12`, `1,5 kg`);
- **preenchimento automático instantâneo e offline**, tolerante a erros de escrita, que aprende
  os produtos comprados com mais frequência;
- **histórico** dos produtos mais adicionados, sugerido ao tocar no campo de pesquisa vazio
  (pode ser desativado e apagado nas definições);
- **catálogo alimentar totalmente incorporado**: um catálogo base escrito à mão e vários milhares
  de produtos gerados a partir da taxonomia do
  [OpenFoodFacts](https://world.openfoodfacts.org/), incluídos na aplicação — nada é
  transferido, nada do que escreve é enviado;
- **organização por secção** (frutas e legumes, padaria, laticínios…), opcional;
- **seis idiomas**: francês, inglês, alemão, espanhol, italiano, português;
- **Home Assistant (opcional)**: sincronização nos dois sentidos, em tempo real com a aplicação
  aberta, sem perder as alterações feitas offline;
- tema claro, escuro ou do sistema, Material 3;
- **sem conta, sem análises, sem dependência dos serviços Google Play**: funciona no GrapheneOS.

### Pré-visualização

| Primeiro arranque | Lista de compras | Preenchimento automático |
| --- | --- | --- |
| ![Ecrã de boas-vindas e escolha do idioma](docs/images/accueil.png) | ![Lista de compras](docs/images/liste.png) | ![Sugestões durante a escrita](docs/images/autocompletion.png) |

| Organização por secção | Tema escuro | Definições |
| --- | --- | --- |
| ![Artigos organizados por categoria](docs/images/categories.png) | ![Lista em tema escuro](docs/images/sombre.png) | ![Definições](docs/images/reglages.png) |

Capturas feitas num emulador com dados fictícios.

## Instalação

Não existe versão publicada numa loja de aplicações: o APK assinado de cada versão está anexado
às releases GitHub do repositório, ou a aplicação pode ser compilada a partir do código-fonte.

Requisitos: Android Studio recente (JDK 21 incluído), SDK Android 37 e um telemóvel ou emulador
com, no mínimo, **Android 17 (API 37)**.

```powershell
$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:installDebug
```

Detalhes, verificações e release assinada: [Desenvolvimento](docs/developpement.md).

## Home Assistant em resumo

1. No Home Assistant: Perfil → Segurança → Tokens de acesso de longa duração → criar um token (a
   aplicação pode lê-lo como código QR).
2. Na aplicação: Definições → Home Assistant → endereço, token, **Testar a ligação**.
3. Escolher para cada lista: criá-la no Home Assistant, associá-la a uma lista existente ou
   mantê-la local.

O token é cifrado pelo Keystore do Android e nunca volta a ser mostrado. Guia completo:
[Home Assistant](docs/home-assistant.md).

## Privacidade

- As listas, o histórico e as preferências ficam no telemóvel.
- Nenhum serviço externo é contactado sem uma ação do utilizador: o catálogo alimentar vem com a
  aplicação.
- O Home Assistant só é contactado se tiver sido configurado; é o único servidor que a aplicação
  sabe contactar.

## Tecnologias

| Camada | Tecnologia |
| --- | --- |
| Linguagem | Kotlin, corrotinas, Flow |
| Interface | Jetpack Compose, Material 3, Navigation Compose |
| Dados | Room (única fonte de verdade), DataStore |
| Injeção | Hilt |
| Rede | Retrofit, OkHttp (REST e WebSocket), Kotlin Serialization |
| Segurança | Android Keystore (AES-256-GCM) |
| Leitura QR | CameraX + ZXing |
| Plataforma | Android 17 (API 37) no mínimo |

## Documentação

Toda a documentação (em francês) está em [`docs/`](docs/README.md):

- [Interface](docs/interface.md) · [Funcionamento offline](docs/hors-ligne.md) ·
  [Idiomas](docs/langues.md)
- [Catálogo e preenchimento automático](docs/catalogue.md) · [Categorias](docs/categories.md)
- [Home Assistant](docs/home-assistant.md) · [Sincronização](docs/synchronisation.md) ·
  [Estratégia de conflitos](docs/conflits.md)
- [Arquitetura](docs/architecture.md) · [Decisões de arquitetura (ADR)](docs/adr/README.md)
- [Segurança e privacidade](docs/securite.md) · [Limitações conhecidas](docs/limites-connues.md)
- [Desenvolvimento](docs/developpement.md) · [Testes](docs/tests.md) ·
  [Release assinada](docs/release.md)

Regras de contribuição (pessoas e agentes): [`AGENTS.md`](AGENTS.md).

## Licença

Copyright © 2026 sargo.

O courses é software livre distribuído sob a **GNU General Public License v3.0**: pode
redistribuí-lo e modificá-lo nos termos desta licença; qualquer versão modificada e
redistribuída deve manter a mesma licença. Texto completo: [`LICENSE`](LICENSE).

Os dados do catálogo provêm do [Open Food Facts](https://world.openfoodfacts.org) e continuam sob
a licença [ODbL](https://opendatacommons.org/licenses/odbl/1-0/). Os ficheiros de
`app/src/main/assets/catalog/` são uma base derivada produzida por `scripts/generate-catalog.py`.

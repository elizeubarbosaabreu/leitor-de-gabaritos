# Leitor de Gabaritos

App Android que lê folhas de respostas (gabaritos) preenchidas à mão por **visão computacional (OMR)** — sem precisar de scanner ou marcador de caixa — e corrige a prova automaticamente, informando nota e detalhamento questão a questão.

<p align="center">
  <img src="modelos/GABARITO FUNDAMENTAL.png" alt="Modelo de gabarito – Ensino Fundamental (A–D)" width="220"/>
  &nbsp;&nbsp;&nbsp;&nbsp;
  <img src="modelos/GABARITO MEDIO.png" alt="Modelo de gabarito – Ensino Médio (A–E)" width="220"/>
</p>

> Calibrado e validado com **fotos reais de gabarito** (câmera de celular, tinta azul, close-up e página inteira) além de folhas sintéticas com rotação, perspectiva e ruído.

---

## Funcionalidades

- **Leitura OMR de bolhas** com detecção robusta de grade (10 questões, sem depender de marcas de calibração).
- **Dois tipos de gabarito**, selecionáveis na tela de leitura:
  - **Ensino Fundamental** → alternativas **A–D** (4 colunas);
  - **Ensino Médio** → alternativas **A–E** (5 colunas).
- **Correção automática** contra o gabarito oficial (salvo no aparelho) com acertos/erros/brancos e percentual.
- **OCR (ML Kit)** para numerar as questões automaticamente a partir dos números impressos na folha.
- Entrada de imagem por **câmera** ou **galeria**.
- Resultado com lista questão a questão: resposta do aluno × resposta correta.
- Funciona offline; os dados ficam apenas no aparelho.

---

## Modelos de gabarito

Os gabaritos oficiais usados na calibração estão em [`modelos/`](modelos/):

| Arquivo | Nível | Alternativas |
|---|---|---|
| `modelos/GABARITO FUNDAMENTAL.png` | Ensino Fundamental | A – D (4 colunas) |
| `modelos/GABARITO MEDIO.png` | Ensino Médio | A – E (5 colunas) |

Ambos contêm o quadro de respostas com **10 questões** (linhas) e bolhas circulares. No modelo Fundamental, a quinta coluna física é apenas uma barra de referência (não é uma alternativa), por isso o leitor usa 4 colunas para esse nível.

---

## Como o leitor funciona (algoritmo)

O motor OMR fica em `app/src/main/java/com/elizeu/gabarito/OmrEngine.kt` e foi portado 1:1 de um protótipo em Python validado com fotos reais. Etapas:

1. **Escala de cinza** e **limiar adaptativo de Bradley** (`s=45, t=0.12`), sem desfoque/dilatação.
2. **Componentes conexos** (8-vizinhança) com filtro de *aspect ratio* ≤ 2.0.
3. **Busca da grade em camadas de limiar** (`0.035 … 0.007` × `min(W,H)`), com **banda de tamanho** definida pela mediana dos componentes `[0.55, 1.8]×mediana` — resistente a ruído de compressão JPEG e texto impresso.
4. **Colunas** = clusters das posições `x` (tol `0.5×med`), tomadas as `nx` de maior contagem (4 ou 5, conforme o nível).
5. **Linhas** = clusters das posições `y` com **cobertura de colunas** (tol `0.45×med`) e **fusão de fragmentos** por pitch dominante.
6. **Preenchimento** = área do maior componente dentro de `0.45×med` do centro da célula; a bolha é considerada **marcada** se a área exceder `1.7 × mediana` das células (limiar absoluto — uma folha inteira em branco não gera respostas falsas).

### Validação

- **Fotos reais:** 4 fotos (2 close-up + 2 página inteira, EF e EM) × 3 resoluções → **10/10 em todos os casos**; as fotos estão embutidas nos testes (`app/src/test/resources/photos/`).
- **Sintético:** folha de 20 questões limpa → 20/20; folha rotacionada 2° + ruído → 20/20.
- Testes executados automaticamente pelo Gradle (`testDebugUnitTest`).

---

## Estrutura do projeto

```
.
├── modelos/                        # Gabaritos oficiais (PNG)
│   ├── GABARITO FUNDAMENTAL.png
│   └── GABARITO MEDIO.png
├── app/
│   ├── build.gradle.kts
│   └── src/
│       ├── main/java/com/elizeu/gabarito/
│       │   ├── OmrEngine.kt        # Motor OMR (detecção + preenchimento)
│       │   ├── Corretor.kt         # Orquestra OMR + OCR (ML Kit)
│       │   ├── KeyParser.kt        # Lê gabarito oficial digitado
│       │   ├── GabaritoStore.kt    # Persistência do gabarito oficial
│       │   ├── ImageUtils.kt       # Decodificação/rotação de imagem
│       │   └── ui/                 # Fragments (Home, Scan, Result)
│       ├── res/                    # Layouts, strings, temas
│       └── test/java/com/elizeu/gabarito/
│           ├── OmrEngineTest.kt    # Testes sintéticos
│           └── RealPhotoTest.kt    # Testes com fotos reais
├── build.gradle.kts
├── settings.gradle.kts
├── gradlew                          # Gradle wrapper
└── README.md
```

---

## Como compilar

Pré-requisitos:

- **Android SDK** (compileSdk 34) — configure `local.properties` com `sdk.dir` ou a variável `ANDROID_HOME`.
- **JDK 17+**.

```bash
# build de depuração + testes unitários
./gradlew :app:assembleDebug :app:testDebugUnitTest

# APK release (assinado)
./gradlew :app:assembleRelease
```

O APK release é gerado em `app/build/outputs/apk/release/app-release.apk`.

> A assinatura de release usa o arquivo `keystore/release.keystore` (ignorado no Git por segurança). Para reproduzir releases é necessário ter esse arquivo localmente.

---

## Como usar

1. Instale o APK no aparelho (Android 8.0+, API 26).
2. Na tela inicial, salve o **gabarito oficial** (ex.: `1C 2A 3D …`).
3. Em **Ler prova**, selecione o tipo (**Fundamental A–D** ou **Médio A–E**).
4. Fotografe a folha de respostas com boa luz, de frente e enquadrando o quadro de bolhas.
5. Toque em **Processar e corrigir** — o resultado aparece questão a questão com a nota.

Dicas de foto: evite reflexos e sombras; aproxime a câmera do quadro de respostas (a leitura funciona também com a página inteira visível).

---

## Testes

```bash
./gradlew :app:testDebugUnitTest
```

| Teste | Cobre |
|---|---|
| `OmrEngineTest.readsCleanSheetEM` | Folha sintética EM, 20 questões, 5 colunas |
| `OmrEngineTest.readsCleanSheetEF` | Folha sintética EF, 10 questões, 4 colunas |
| `OmrEngineTest.readsRotatedNoisySheet` | Folha rotacionada 2° + ruído |
| `OmrEngineTest.blankSheetYieldsNoAnswers` | Folha em branco → nenhuma resposta |
| `RealPhotoTest.readsAllRealPhotos` | 4 fotos reais (EF/EM, close-up e página inteira) → 10/10 |

---

## Tecnologias

- Kotlin, ViewBinding, Fragments/Navigation simples
- Material Design 3 (Material Components)
- Google ML Kit (reconhecimento de texto — latin)
- Gradle 8.7 / AGP 8.4.2

## Licença

Projeto pessoal sem licença definida — uso e distribuição sujeitos à autorização do autor.

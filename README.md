# Leitor de Gabaritos

App Android que lê folhas de respostas (gabaritos) preenchidas à mão por **visão computacional (OMR)** — sem precisar de scanner ou marcador de caixa — e corrige a prova automaticamente, informando nota e detalhamento questão a questão.

O gabarito é gerado pelo **Gerador de Provas** (https://elizeubarbosa.com.br/ferramentas/gerador-de-provas.html):
quadro com números, bolhas Ⓐ–Ⓔ e **4 marcas de referência nos cantos**
(□ quadrado · ○ círculo · △ triângulo · □ quadrado) que ancoram a leitura.

> Validado com **folhas sintéticas** na geometria canônica exata: 10/20 questões, EM (5 colunas) e EF (4), rotação de 3°, perspectiva, folha toda preenchida e folha em branco. Teste no celular com uma folha impressa pelo gerador antes de usar em sala.

---

## Funcionalidades

- **Leitura OMR de bolhas** por **marcadores de canto** (4 marcas impressas: quadrado, círculo, triângulo, quadrado): a foto é normalizada por homografia e as bolhas são lidas pela geometria fixa — imune a rotação e perspectiva.
- **Dois tipos de gabarito**, selecionáveis na tela de leitura e aceitando até **40 questões**:
  - **Gabarito A–D** → alternativas **A–D** (4 colunas);
  - **Gabarito A–E** → alternativas **A–E** (5 colunas).
- **Correção automática** contra o gabarito oficial (salvo no aparelho) com acertos/erros/brancos e percentual.
- **Valor da prova configurável**: informe quanto a prova vale (ex.: 10 pontos) e a nota é calculada proporcionalmente ao número de acertos (o valor fica salvo para as próximas correções).
- **OCR (ML Kit)** para numerar as questões automaticamente a partir dos números impressos na folha.
- Entrada de imagem por **câmera** ou **galeria**.
- Resultado com lista questão a questão: resposta do aluno × resposta correta.
- Funciona offline; os dados ficam apenas no aparelho.

---

## Marcadores de canto (formato novo)

A folha de bolhas tem 4 marcas sólidas de 6×6 mm nos cantos, com 2,5 mm de
folga da moldura (quiet zone) — por isso nenhuma bolha pintada se confunde
com elas:

| Canto | Forma | Posição |
|---|---|---|
| TL (topo-esquerda) | quadrado | centro a 5,5 mm das bordas |
| TR (topo-direita) | círculo | ídem |
| BL (base-esquerda) | triângulo (base no topo) | ídem |
| BR (base-direita) | quadrado | ídem |

Formas distintas identificam cada canto (e a rotação da foto). A geometria
canônica em mm vive em `GeometriaFolha.kt` (app) e no comentário do CSS do
gerador — **os dois lados precisam ser alterados juntos**.

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
│           └── MarcadorTest.kt     # Formato novo (geometria canônica)
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
3. Em **Ler prova**, selecione o tipo (**Gabarito A–D** ou **Gabarito A–E**) e informe **quanto a prova vale** (padrão: 10 pontos).
4. Fotografe a folha de respostas com boa luz, de frente e enquadrando o quadro de bolhas.
5. Toque em **Processar e corrigir** — o resultado aparece questão a questão com a nota já na escala escolhida (ex.: 8 acertos em prova de 10 → **8**; prova de 100 → **80**).

Dicas de foto: evite reflexos e sombras; aproxime a câmera do quadro de respostas (a leitura funciona também com a página inteira visível).

---

## Testes

```bash
./gradlew :app:testDebugUnitTest
```

| Teste | Cobre |
|---|---|
| `MarcadorTest.*` | Formato novo (sintético, geometria canônica): limpo, 20 questões todas pintadas, rotação 3°, perspectiva, folha em branco e detecção das formas dos cantos |

---

## Tecnologias

- Kotlin, ViewBinding, Fragments/Navigation simples
- Material Design 3 (Material Components)
- Google ML Kit (reconhecimento de texto — latin)
- Gradle 8.7 / AGP 8.4.2

---

## Gerador de Provas e Atividades com Gabarito

Além do leitor de gabaritos, o Prof. Elizeu Barbosa mantém um gerador online de provas e atividades:

- **Acessar:** https://elizeubarbosa.com.br/ferramentas/gerador-de-provas.html
- **Recursos:**
  - Criação de questões com alternativas (A–E), V/F, dissertativas e com imagens
  - Gabarito compacto no canto da prova ou folha de gabarito separada com bolhas para o aluno preencher
  - Inclusão de cabeçalho da escola (logo, nome, ano)
  - Impressão em formato A4
  - Exportação para PDF e Word (.docx)
  - Modelos para Ensino Fundamental (A–D) e Médio (A–E)
  - Até 40 questões por prova

- **Outras ferramentas disponíveis:**
  - Gerador de Caça-Palavras (https://elizeubarbosa.com.br/ferramentas/gerador-cacapalavras.html)
  - Gerador de Cruzadinhas (https://elizeubarbosa.com.br/ferramentas/gerador-cruzadinhas.html)
  - Gerador de Apresentação Sozi

A ferramenta complementa este leitor de gabaritos: o professor pode gerar a prova e o gabarito online, imprimir em A4, e depois utilizar este app para corrigir as folhas fotografadas pelos alunos.

## Licença

Projeto pessoal sem licença definida — uso e distribuição sujeitos à autorização do autor.

# Leitor de Gabaritos 📝

Aplicativo Android para leitura e correção automática de gabaritos de provas de múltipla escolha. Basta fotografar a folha de respostas e o app calcula a nota instantaneamente.

## ✨ Funcionalidades

- **Leitura via OCR** - Fotografe o gabarito oficial e o app lê automaticamente
- **Correção automática** - Fotografe a folha de respostas do aluno e veja a nota na hora
- **Dois tipos de gabarito** - Suporte para gabarito A-D (4 alternativas) e A-E (5 alternativas)
- **Valor da prova personalizável** - Defina quantos pontos a prova vale
- **Histórico salvo** - O gabarito oficial fica salvo para correções futuras

## 🎯 Gerador de Provas

> **Crie suas provas online gratuitamente!**
> 
> Acesse: **[https://elizeubarbosa.com.br/ferramentas/gerador-de-provas.html](https://elizeubarbosa.com.br/ferramentas/gerador-de-provas.html)**
> 
> O gerador permite criar provas profissionais com formatação automática, gabarito e folha de respostas pronta para impressão.

## 📱 Capturas de Tela

| Tela Inicial | Leitura do Gabarito | Correção de Prova |
|-------------|---------------------|-------------------|
| ![Home](docs/home.png) | ![Scan](docs/scan.png) | ![Result](docs/result.png) |

## 🚀 Como usar

1. **Configure o gabarito oficial**
   - Digite manualmente (ex: `1E, 2D, 3C, 4A, 5B`)
   - Ou fotografe o gabarito impresso (botão "Ler gabarito de uma foto")

2. **Corrija as provas**
   - Toque em "Ler prova e corrigir"
   - Fotografe a folha de respostas do aluno
   - Veja a nota calculada automaticamente

## 🛠️ Tecnologias

- **Kotlin** + **Android SDK**
- **ML Kit Text Recognition** (Google) para OCR
- **OpenCV** para processamento de imagem
- **ViewBinding** + **Fragments** + **Material Design 3**

## 📦 Instalação

### Opção 1: Baixar APK
Baixe o APK mais recente na [página de releases](https://github.com/elizeubarbosa/leitor-de-gabaritos/releases)

### Opção 2: Compilar do fonte
```bash
git clone https://github.com/elizeubarbosa/leitor-de-gabaritos.git
cd leitor-de-gabaritos
./gradlew assembleDebug
```
O APK estará em `app/build/outputs/apk/debug/app-debug.apk`

## 🔧 Configuração

O app usa `SharedPreferences` para persistir:
- Gabarito oficial salvo
- Tipo de gabarito (A-D ou A-E)
- Valor da prova em pontos

## 📄 Licença

MIT License - Veja [LICENSE](LICENSE) para detalhes.

## 👨‍💻 Autor

**Elizeu Barbosa**
- Site: [elizeubarbosa.com.br](https://elizeubarbosa.com.br)
- Gerador de Provas: [elizeubarbosa.com.br/ferramentas/gerador-de-provas.html](https://elizeubarbosa.com.br/ferramentas/gerador-de-provas.html)
- GitHub: [@elizeubarbosa](https://github.com/elizeubarbosa)

---

⭐ **Se este projeto te ajudou, deixe uma estrela no GitHub!**
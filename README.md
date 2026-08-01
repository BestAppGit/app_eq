# EQ HyperOS

Prova de conceito de um equalizador Android simples para uso pessoal em aparelhos Xiaomi com HyperOS, pensado inicialmente para Redmi Note 7 e Redmi Note 14.

## Tela inicial

A primeira versão tem apenas o controle da banda mais próxima de 60 Hz:

```text
60 Hz

[-]      [+]
```

Os botões ajustam a banda em passos de 1 dB, respeitando o limite informado pelo equalizador do próprio Android.

## Gerar APK online

O projeto está preparado para gerar o APK pelo GitHub Actions, sem Android Studio instalado no computador.

1. Suba este diretório para um repositório no GitHub.
2. Abra a aba `Actions`.
3. Execute o workflow `Build APK`.
4. Baixe o artifact `eq-hyperos-debug-apk`.

O arquivo gerado será:

```text
app-debug.apk
```

## Gerar APK por NPM

```bash
npm run apk
```

Esse comando chama o Gradle por baixo:

```bash
gradle assembleDebug
```

Para usar esse modo localmente, o computador precisa ter JDK 17, Android SDK command-line tools e Gradle instalados.

O APK debug local será gerado em:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Observação

O Android aplica efeitos de áudio por sessão. Esta prova usa a sessão global `0`, que pode variar conforme versão do Android, HyperOS, player de áudio e permissões do sistema.

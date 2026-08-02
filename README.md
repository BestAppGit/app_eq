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

## Calibrador local

O calibrador fica em `calibrator/` e roda sem dependências externas. O Python apenas abre o servidor local; quem emite o tom senoidal é o navegador via Web Audio.

```bash
python calibrator/server.py
```

Depois abra:

```text
http://127.0.0.1:8787
```

Para acessar pelo celular, mantenha computador e celular na mesma rede Wi-Fi e abra no navegador do celular o endereço `Calibrador na rede` mostrado no terminal, por exemplo:

```text
http://192.168.0.25:8787
```

Se não abrir, libere a porta `8787` no firewall do Windows.

Ele reproduz um tom senoidal de 25 Hz a 80 Hz, permite ajustar cada frequência em passos de 0,5 dB, aceita comandos de voz em português e exporta um JSON versionado com `preampDb`, todas as frequências calibradas em `calibrationPoints` e filtros `peaking` derivados.

Se não ouvir o tom em 25 Hz, use o botão `Teste 440 Hz`. Muitos alto-falantes comuns praticamente não reproduzem 25-40 Hz, então o teste em 440 Hz separa problema de áudio do navegador de limitação física do falante.

## Importar perfil no Android

No app, use `Importar perfil` para selecionar o JSON exportado pelo calibrador. Importar um novo arquivo substitui o perfil anterior. Depois use `Ativar perfil` ou `Desativar perfil`.

## Diagnóstico no Redmi Note 14

O APK inclui duas telas para validar se o aparelho permite a precisão necessária antes de continuar o projeto.

### Diagnóstico Equalizer

Mostra:

```text
Número de bandas
Centro de cada banda
Faixa de cada banda
Mapeamento de 55 Hz a 65 Hz
```

Se várias frequências próximas, como `59 Hz`, `60 Hz` e `63 Hz`, caírem na mesma banda, o `Equalizer` Android não serve para calibração frequência por frequência.

No Redmi Note 14 testado, o `Equalizer` reportou 5 bandas e mapeou toda a faixa de `55 Hz` a `65 Hz` para a mesma banda de centro `60 Hz`. Isso reprova o `Equalizer` clássico para o requisito de correção 1 Hz por 1 Hz.

### Teste DynamicsProcessing

Cria um `DynamicsProcessing` na sessão global `0` com 11 bandas de cutoff entre `55 Hz` e `65 Hz`, aplicando um corte forte em `59 Hz` no Pre-EQ e no Post-EQ.

Use um player externo tocando sweep ou tons nessa região e clique em `Aplicar teste 55-65 Hz`. Se o corte soar estreito e claramente localizado em `59 Hz`, esta API pode ser investigada como solução. Se a mudança afetar uma região ampla ou não funcionar em áudio de outros apps, ela não atende ao requisito.

O teste não aplica preamp, para evitar que a música inteira fique mais baixa e confunda a avaliação.

### Medidor DynamicsProcessing

Use arquivos WAV de tom fixo reproduzidos pelo YouTube Music, já que ele aceita o efeito neste aparelho. O fluxo é:

```text
1. Reproduzir o WAV externo, por exemplo 59 Hz.
2. Clicar em Medir sem efeito.
3. Clicar em Aplicar teste 59 Hz.
4. Clicar em Medir com efeito.
5. Comparar o delta de 55 Hz a 65 Hz.
```

Se `59 Hz` cair bastante e `58 Hz`/`60 Hz` ficarem próximos de `0 dB` de delta, o corte é estreito. Se várias frequências vizinhas caírem juntas, o `DynamicsProcessing` está trabalhando como banda ampla.

## Observação

O Android aplica efeitos de áudio por sessão. Esta prova usa a sessão global `0`, que pode variar conforme versão do Android, HyperOS, player de áudio e permissões do sistema.

## Limitação de DSP

O Android `Equalizer` disponível para uso global trabalha com bandas fixas do aparelho e não permite configurar frequência central e fator Q paramétricos reais. A API `DynamicsProcessing` expõe bandas com frequência de corte e ganho, mas também é vinculada a sessões de áudio e não fornece um filtro paramétrico `frequency + gain + Q` global.

Por isso, o APK importa o JSON paramétrico, valida seus limites e aplica a melhor aproximação possível nas bandas fixas retornadas pelo `Equalizer`. O `preampDb` é usado como redução global aproximada em todas as bandas para reduzir risco de clipping.

# Monstro V18 — editor Android

Versão **18.2-FX** construída sobre a 18.1 testada pelo usuário. Agora inclui Motion Blur temporal, Auto Retention, Digital Glitch, RGB Split, Impact Shake, Psycho Strobe, Hue Shift, Focus Edge, Master Zoom e Blockbuster, além de todas as funções de edição anteriores.

Os oito efeitos podem ser combinados por clipe, ficam salvos no projeto e são aplicados pelo mesmo shader na prévia e na exportação. O strobe inicia desligado e pede confirmação antes de ativar flashes de 2 Hz. O modo leve reduz a resolução para 854 × 480, limita a taxa a cerca de 30 fps e solicita 2,5 Mbps. O modo normal usa 1280 × 720, até 60 fps e 5 Mbps; não cria quadros ausentes na fonte. O codec pode ajustar o bitrate solicitado.

**Instalação de teste:** esta edição usa o nome Monstro V18 FX e o pacote `com.monstro.v18.fx`, permitindo manter a 18.1 instalada. Os projetos do aplicativo anterior permanecem nele; importe novamente seus vídeos no FX. O APK utiliza assinatura de teste gerada pelo Android durante o build.

Versão 18.1: importa vídeos, reproduz o clipe selecionado, corta início/fim,
divide na posição da prévia, remove e reordena clipes, aplica quatro presets de cor
e exporta a sequência em MP4 real usando Media3 Transformer.

## Usar

1. Instale o APK de teste gerado em **Actions → Android - testar e gerar APK → Artifacts → MonstroV18-APK**. Extraia o ZIP e abra `app-debug.apk` no Android 7 ou superior.
2. Toque em **Importar vídeos** e escolha um ou mais arquivos locais no seletor do Android.
3. Toque num clipe para ver a prévia com controles de reprodução. Ajuste as duas pontas da barra de corte. Para dividir, pause no ponto desejado e toque em **Dividir na posição**.
4. Use **Mover**, **Excluir** e os presets para montar a sequência. O áudio pode ser removido de todo o projeto.
5. Toque em **Exportar projeto em MP4** e mantenha o aplicativo aberto. Após concluir, use **Salvar último MP4 exportado** para escolher uma pasta.

O projeto é salvo automaticamente no aparelho. Os vídeos originais não são copiados nem alterados; mantenha-os disponíveis no local de origem. A saída anterior fica disponível até outra exportação concluir. Cancelar ou falhar não apaga a última saída concluída.

## Limites desta versão

- Prévia de um clipe por vez; exportação junta os clipes na ordem da lista.
- Saída H.264/AAC com proporção preservada; 480p no modo leve ou 720p no modo normal.
- Presets de cor Original, Neon, Trap Lord, Dark Energy e Blockbuster; efeitos visuais combináveis. Não inclui efeitos de áudio.
- Ainda não há trilha musical separada, textos, transições, desfazer ou múltiplos projetos.
- A exportação depende dos codecs do aparelho. Arquivos protegidos, corrompidos ou formatos incompatíveis podem falhar, com mensagem de erro.
- Não há serviço de exportação em segundo plano: mantenha o aplicativo aberto. Encerrar o processo interrompe a renderização, mas conserva o projeto.

## Compilar e verificar

JDK 17, Gradle 8.4 e Android SDK 34. Abra no Android Studio ou execute:

```sh
gradle :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

O workflow instala a versão fixa do Gradle e compila os arquivos do repositório sem reescrevê-los.
Os testes unitários cobrem intervalos de corte e divisão relativa à prévia já cortada.

## Teste no aparelho antes de considerar a versão pronta

- Importar dois vídeos, incluindo um sem áudio; reproduzir, pausar e buscar.
- Cortar um clipe que começa após 0, dividir e conferir a ordem das partes.
- Reordenar, remover, mudar o preset e fechar/reabrir para conferir o projeto.
- Exportar e abrir o MP4 salvo em outro reprodutor: conferir duração, ordem, cor e áudio.
- Cancelar a exportação e cancelar o seletor de salvamento; tentar novamente.
- Testar vídeo vertical, falta de espaço e arquivo removido da origem.

## Verificação dos efeitos

O teste Android `ChaosExportTest` exporta uma sequência cortada com os oito efeitos, zoom e áudio; verifica o arquivo gerado, duração, presença de áudio e imagem não vazia. O vídeo sintético de teste foi gerado com FFmpeg testsrc2 e sine. Os testes unitários verificam também os IDs persistidos e a preservação dos efeitos ao dividir clipes.

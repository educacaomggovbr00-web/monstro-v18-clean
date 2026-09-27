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


## 18.4 — Trap Lyrics FX

Importe um SRT UTF-8 ou UTF-16 com o botão **+ Importar Legenda (.srt)**. As frases usam o tempo da linha do tempo final (após cortes e ordem dos clipes), são salvas no projeto e gravadas no MP4. O SRT não contém tempos por palavra: o destaque é distribuído igualmente dentro da duração da frase. Ajuste o SRT se alterar a montagem. Suporta frases multilinha, BOM, CRLF, vírgula/ponto nos milissegundos e ignora blocos inválidos com aviso; limite 2 MB / 5.000 frases.

Roxo/vermelho neon, frase completa, palavra em destaque e pop-in/glow. **Compatibilidade de legenda** reduz a textura para 360 px de largura, desativa glow/pop e reduz atualizações; mantém palavras e texto no MP4. A prévia usa Canvas separado do processador de vídeo, inclusive em modo de compatibilidade da prévia. A exportação reutiliza bitmap e textura para limitar alocações.

Exportação 9:16 ou 16:9, com imagem inteira sobre fundo ampliado/desfocado. Modo leve: 540×960 / 960×540, até 30 fps, 2,5 Mbps. Alta qualidade: 1080×1920 / 1920×1080, até 30 fps, 10 Mbps solicitados. O encoder pode reduzir parâmetros quando o hardware não os suporta. A prévia do clipe mantém seu enquadramento anterior; o formato de saída é aplicado na exportação.

Presets agora usam matrizes RGB diagonais positivas no domínio linear documentado do Media3. A conversão YUV/faixa/transferência segue os metadados ColorInfo do vídeo no input sampler padrão; os presets não reconvertem RGB como YUV e não impõem BT.601/709. HDR é convertido para SDR na exportação. Isso evita matrizes duplicadas ou inversão dos canais, mas vídeos com metadados incorretos e particularidades de codecs físicos ainda precisam de teste no aparelho.

Testes adicionados: parser/bordas/overlap/tempos das palavras, proporções e matrizes de cor; exportação instrumentada 9:16 em modo leve e 1080p, áudio, fundo e legendas antes/depois de uma junção. Testes anteriores de reprodução e recuperação continuam ativos. Pacote de teste independente `com.monstro.v18.lyrics`.

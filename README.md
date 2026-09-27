# Monstro V18 — editor Android

**Estado: implementação preparada, ainda não compilada nem testada em aparelho.** O envio ao GitHub foi bloqueado por permissão de escrita (HTTP 403). Não há APK gerado neste pacote.

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
- Saída 1280 × 720, H.264/AAC, com proporção preservada e barras quando necessário.
- Os presets são ajustes de contraste/cor; não incluem glitch, RGB split ou efeitos de áudio.
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

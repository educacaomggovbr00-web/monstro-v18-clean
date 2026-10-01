# Integração de funcionalidades do ClearCut no Monstro V18

Fonte examinada: `SysAdminDoc/ClearCut`, commit `8a18de40dd474bbc01f43fd5a6d8e1356ce5600f` (3.81.0). Esta alteração modifica o editor Monstro existente, preservando interface, pacote instalado, Chaos FX, shaders, Gemini, Vosk e controles. Não embute um segundo editor.

## Implementado diretamente no Monstro

- Projetos nomeados: novo, abrir, busca, renomear, duplicar, lixeira e restauração na aba Projetos.
- Documentos versionados, geração anterior válida, escrita atômica com sincronização antes de renomear e recuperação de arquivo truncado. Migração do projeto atual de SharedPreferences sem apagar suas mídias.
- Backup/importação da edição em JSON, com limite de tamanho, validação de versão e durações e nova identidade a cada importação. As mídias continuam referenciadas; não é um arquivo que embute os vídeos.
- Importação de marcadores e intervalos por timecode com validação e desfazer.
- Registros locais de falhas, exportação de diagnóstico com remoção de URIs/caminhos sensíveis e avisos de licença no aplicativo.
- Recuperação do diário imediato de edição após interrupção antes do autosave. Regras de backup limitadas aos documentos e configurações.
- Foco de áudio gerenciado pelo player e pausa ao desconectar fones.
- Verificação de espaço, codec, dimensão e FPS antes de exportar. MP4 temporário só é promovido após leitura da faixa de vídeo, primeiro quadro e duração; exportação com erro mantém o último MP4 concluído.
- Limites para metadados de duração e leitura de documentos.
- Ordenação natural dos clipes pelo nome, com desfazer.
- Exportação SRT, VTT com tempos por palavra e ASS com cor/posição/outline básicos pelo painel Legendas.

Código adaptado e licenças: `app/src/main/java/com/monstro/v18/clearcut/`, `third_party/clearcut/LICENSE`. Os avisos upstream são preservados como referência; esta alteração não adiciona FFmpeg ou DeepFilterNet ao APK.

## Validação

O workflow existente executa os testes unitários, lint, compilação, testes instrumentados de prévia/exportação/legendas e renderização EGL dos 1.000 presets. Foram acrescentadas regressões de escrita interrompida, cópia válida, lixeira, duplicação, gravação fora de ordem, limites de importação e legendas.

## Escopo restante

Esta é uma etapa de integração, **não paridade completa**. Ainda precisam de adaptação e testes específicos: transições completas, múltiplas faixas de vídeo/PiP, proxy workflow, exportação em serviço de segundo plano/fila, arquivo de projeto com mídias embutidas, LUTs/plugins/intercâmbio, ferramentas de IA e processamento nativo adicionais do ClearCut. Alguns recursos upstream são planejados ou exigem modelos/dependências; copiar arquivos não torna esses recursos disponíveis automaticamente. Os formatos de projeto dos dois motores são diferentes.

# Monstro V18 Studio

Editor Android nativo em Kotlin/Compose, Media3 e OpenGL ES. Versão 18.5-Studio.

## Edição no celular

- Uma prévia principal integrada à timeline; reprodução passa pelos clipes em ordem.
- Cinco faixas: montagem de vídeo com thumbnails, áudio, textos, legendas e FX. Toque num bloco para abrir seu inspector. Arraste a régua/playhead para buscar; arraste a faixa para rolar; use dois dedos para ampliar a escala de tempo.
- Importação, corte, divisão, reordenação e remoção de clipes; projeto salvo no aparelho.
- Áudios adicionais com posição, corte e volume, ou áudio original desligado. Faixas sonoras são misturadas no MP4.
- Texto e legenda com fonte, tamanho, cor HEX/paleta, stroke, sombra, glow, posição, opacidade e animações Pop/Fade/Digitar. Toque no elemento na prévia ou timeline para selecionar.
- Keyframes lineares de zoom, posição/escala do texto e intensidade de FX. Crie pontos nos tempos desejados e ajuste os valores. Limpar remove a curva daquela propriedade.
- Velocity: velocidade constante de 0,25× a 4×, presets Montanha/Hero/Bullet e pontos editáveis. A curva é amostrada em segmentos constantes de pelo menos 200 ms, até 120 segmentos por clipe. A mesma tabela mapeia a prévia, tempos da fala e exportação; o áudio original muda de duração preservando pitch.
- Motion Blur temporal após os efeitos espaciais, sem acumular recursivamente flashes ou ganho de cor.

## Legendas

**SRT:** mantém importação UTF-8/UTF-16. Os tempos são da timeline final; SRT comum não fornece tempos por palavra, então Word Sync usa divisão aproximada. Frases podem ser corrigidas e reposicionadas pelo inspector.

**Automáticas:** na aba Legenda, baixe uma vez o modelo de português (31 MB), depois toque em **Legendar fala**. Na aba Áudio, **Legendar este áudio** reconhece também uma faixa importada, respeitando seu corte e posição. Vosk reconhece o áudio localmente, com tempos por palavra; não grava o microfone nem envia áudio. Requer internet somente para baixar o modelo. A qualidade depende da dicção, música e ruído; revise as frases antes de exportar. O modelo pequeno prioriza uso móvel, não precisão de modelos grandes. Cancelar preserva as legendas anteriores.

Trap Lyrics apresenta a frase completa pequena e a palavra atual em destaque neon. Pausas entre palavras reconhecidas permanecem sem destaque. A opção **Legenda leve** reduz a textura e desativa glow/pop. Textos e legendas usam o mesmo painter na prévia e no MP4.

## Biblioteca Chaos

1.000 presets: **20 algoritmos × 5 receitas espaciais × 10 envelopes temporais**. Não são 1.000 engines independentes. Categorias Glitch, RGB, Shake, Trap, Motion, Distortion, Anime, Retro, VHS, Cinematic, Light e Blur. Pesquisa, favoritos e recentes persistidos, intensidade e velocidade; direção nos algoritmos direcionais. Os oito Chaos FX anteriores continuam disponíveis por clipe.

Motores: fragmentação por faixas, separação RGB, shake, zoom, rotação, onda, ondulação radial, vórtice, espelho polar, mosaico, retícula, posterização, scanlines, tracking VHS, grão, duotone, light leak, prisma radial, blur direcional e túnel. O teste EGL renderiza quatro instantes de cada receita e exige **1.000 assinaturas visuais diferentes**, além de verificar que não são identidades.

## Exportação e cores

H.264/AAC, 9:16 ou 16:9, imagem inteira com fundo ampliado/desfocado. Leve: 540×960 / 960×540, 30 fps, 2,5 Mbps solicitados. Alta qualidade: 1080×1920 / 1920×1080, 30 fps, 10 Mbps solicitados. O codec pode ajustar bitrate. Não há interpolação óptica: slow motion prolonga os quadros disponíveis.

YUV/faixa/transferência são convertidos pelo input sampler Media3 usando ColorInfo antes dos presets RGB. Matrizes Neon/Trap/Dark/Blockbuster não aplicam uma segunda conversão YUV. HDR é convertido para SDR na exportação. A recuperação de erro da GPU preserva o projeto e usa reprodução sem FX se o aparelho rejeitar o processador; a mensagem informa essa diferença e o MP4 mantém os efeitos. Não existe painel de prévia separado.

## Instalar

Actions → **Android - testar e gerar APK** → artefato **MonstroV18-APK**, extraia e instale `app-debug.apk`. Android 7+. Esta edição usa `com.monstro.v18.studio`: instala ao lado das edições anteriores, sem apagar seus projetos. Importe as mídias novamente na nova edição. APK de desenvolvimento assinado pelo build; não é versão publicada em loja.

Mantenha os arquivos originais acessíveis. Os tempos de textos, legendas, músicas e FX são absolutos na timeline; mudanças posteriores na montagem podem exigir reposicioná-los ou gerar novamente as legendas. Há uma faixa principal de vídeo sequencial, não composição de vários vídeos simultâneos/PiP. Não inclui desfazer, transições entre clipes, múltiplos projetos ou renderização em serviço de segundo plano. Mantenha o aplicativo aberto durante reconhecimento/exportação. Cancelar/falhar conserva o último MP4 concluído.

## Compilar e testar

JDK 17, Gradle 8.4, SDK Android 34:

```sh
gradle :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
python tools/check_shaders.py
```

Testes unitários: trims, SRT, word timing, color presets, catálogo, keyframes e mapeamento das velocidades. Instrumentados: prévia e recuperação da GPU, exportação dos oito FX, legendas/fundo/cores/1080p, velocidade com áudio adicional e FX/textos, reconhecimento de uma fala sintética em português. O workflow gera uma fala AAC/MP4 com eSpeak NG e FFmpeg antes dos testes de reconhecimento; o primeiro teste baixa o modelo oficial Vosk. Relatórios e captura da tela são publicados como artefatos. Testes em emulador não substituem a verificação dos codecs/GPU no aparelho físico.

## Referências e licenças

- [Media3 Transformer](https://developer.android.com/media/media3/transformer) — API Android, Apache 2.0.
- [Vosk Android](https://alphacephei.com/vosk/android) e [modelos](https://alphacephei.com/vosk/models). `vosk-model-small-pt-0.3`, Apache 2.0, atribuição Alpha Cephei/contribuidores. Modelo baixado separado, não embutido no APK.
- Vosk Android 0.3.45; JNA 5.13.0 (licenciamento LGPL 2.1/Apache 2.0). Licenças de dependências preservadas no empacotamento.
- [SpeedChangeEffect](https://developer.android.com/reference/androidx/media3/effect/SpeedChangeEffect): a implementação 1.2.1 divide timestamps absolutos. Studio normaliza cada segmento no próprio shader para preservar junções na timeline.

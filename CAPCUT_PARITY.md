# Monstro V18 — mapa de paridade funcional com editores mobile profissionais

> Registro histórico do Monstro 18.6, anterior à integração do ClearCut. O estado atual está em [CLEARCUT_INTEGRATION.md](CLEARCUT_INTEGRATION.md) e no [registro de capacidades](scripts/capability_registry.json). A lista de pendências abaixo descreve a base antiga preservada em `legacy/monstro-v18`.

Objetivo: oferecer no Monstro fluxos e capacidades equivalentes aos principais recursos documentados do CapCut,
mantendo identidade, código, assets e marca próprios. Nenhum recurso proprietário do CapCut é copiado.

## Já funcional no Monstro

- Timeline multi-faixa com vídeo, áudio, imagem, texto, legenda e FX
- Playhead arrastável, pinch-to-zoom, trim handles, snap magnético e feedback háptico
- Undo / Redo
- Importação de vídeo, imagem, áudio e SRT
- Legendas automáticas Gemini + modo Offline Vosk
- Word Sync / Trap Lyrics com palavra atual destacada
- Editor completo de legenda/texto com posição, escala, fonte, cor, stroke, shadow, glow e keyframes
- Texto para fala / narração Gemini
- Voiceover pelo microfone
- Áudio: volume, fade in/out, pitch/voz e Auto-Beats
- 1.000 VFX com categorias, busca, favoritos, recentes, intensidade, velocidade, direção e keyframe
- GPU shaders, Motion Blur, filtros/presets e ajustes de cor
- Keyframes de zoom, posição X/Y e rotação
- Speed Curve / Velocity
- Imagens em camada com posição, escala, rotação, opacidade e keyframes
- Aspect ratios 9:16, 16:9, 1:1 e 4:5; fundo blur, sólido ou padrão
- Export H.264 / HEVC quando suportado, bitrate baixo/recomendado/alto, 540p/1080p
- Tela cheia
- Firebase AI Logic + App Check
- Conta Firebase por e-mail/senha, criação de conta, redefinição e Logout
- IA Auto Edit multimodal: analisa frames + legenda/fala + beats e aplica cor, proporção, keyframes, velocity, FX e marcadores
- Auto-save local do estado do projeto
- CI Android com testes, shader check, APK e teste de exportação; builds obsoletos são cancelados

## Paridade prioritária em implementação

### Edição
- Transições diretamente entre cortes
- Freeze Frame real
- Reverse real
- Crop / rotate interativo no canvas
- Gestos diretos no canvas para mover / escalar / girar camadas
- Video overlay em múltiplas faixas
- Máscaras: linear, circular, retangular, espelhada
- Blend modes: Normal, Overlay, Multiply, Screen
- Opacidade por clipe
- Curvas Bézier editáveis para keyframes
- Curvas de velocidade extras: Montage, Hero, Bullet Time, Jump Cut, Flash In/Out

### Áudio
- Redução de ruído
- Normalização de loudness
- Isolamento vocal
- Eco / telefone / robot / efeitos adicionais de voz
- Detecção de beats com edição manual da sensibilidade
- Biblioteca de música e SFX

### IA
- Smart Cut / Long Video to Shorts com seleção de melhores trechos
- Remoção de filler words e silêncios
- Auto Reframe com tracking do assunto
- Background Removal por IA
- Chroma Key com eyedropper e intensidade
- Motion Tracking para texto/imagem/sticker
- Estabilização
- Relight / correção inteligente de iluminação
- Enhance / upscale
- Optical Flow para slow motion
- Lip Sync
- Tradução/dublagem multilíngue
- Geração de sticker por IA
- Geração de imagem/vídeo por IA
- Script-to-video

### Texto / ativos
- Stickers animados
- GIFs
- Fontes adicionais
- Templates de texto
- Templates de projeto
- Biblioteca de stock
- Asset Bin completo com pastas/favoritos

### Projeto / conta / cloud
- Tela inicial com Projetos Recentes e thumbnails
- Vários projetos locais
- Cloud sync por usuário
- Compartilhamento / colaboração
- Workspace / equipe
- Brand kit
- Login Google (requer OAuth client configurado no Firebase)
- Login Facebook/TikTok (requer apps OAuth externos e credenciais dos provedores)
- Perfil / avatar / gerenciamento de conta

### Export / publicação
- 24/30/60 fps selecionável
- 2K / 4K quando hardware permitir
- Formatos adicionais quando suportados
- Compartilhamento rápido para apps instalados
- Publicação direta para plataformas quando as APIs oficiais e autorização estiverem configuradas

### Extras
- Teleprompter
- Compressor de vídeo
- Templates compartilháveis
- Dashboard / home com ferramentas rápidas

## Regras de implementação

1. Nenhum botão entra como placebo: preview e export devem usar o mesmo estado sempre que aplicável.
2. Recursos pesados rodam fora da UI thread.
3. Imagens e previews usam cache limitado.
4. Ações destrutivas entram no Undo.
5. IA nunca apaga recursos existentes sem uma ação explícita do usuário.
6. Recursos cloud/OAuth precisam de providers e credenciais válidas; não são simulados.
7. O editor continua utilizável offline para as ferramentas locais.

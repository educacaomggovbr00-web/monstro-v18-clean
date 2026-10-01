# Integração completa do código ClearCut no Monstro

Upstream: SysAdminDoc/ClearCut, 3.81.0, commit 8a18de40dd474bbc01f43fd5a6d8e1356ce5600f.

O motor e a interface passaram a usar uma única base. Todos os arquivos upstream são importados e versionados, incluindo composição, transições, PiP, proxies, exportação em serviço/fila, LUTs, plugins, arquivos com mídia, ferramentas de áudio/IA, estabilização e rastreamento, banco Room, modelos opcionais, testes, documentação e a dependência FFmpeg com checksum e licenças. O registro `scripts/capability_registry.json` distingue recursos implementados, condicionais e planejados do próprio upstream.

Identidade instalada: com.monstro.v18.studio. Namespace: com.monstro.v18. Versão 18.7-ClearCut, código 25, Android 8+.

Recursos próprios adaptados: Chaos/Studio FX (1.000 receitas endereçáveis e seleção curada), cores Monstro, pacotes Monstro/Premiere, Gemini Auto Edit, legendas Gemini com fallback Vosk, tradução, narração e conta. Mesma timeline e mesmo pipeline de efeitos para prévia/exportação.

Migração: converte cópias dos documentos 18.6 para a persistência canônica; nunca apaga os originais. Clipes, cortes, áudio, textos, imagens, legendas, marcadores, curvas de velocidade e FX são adaptados. As convenções visuais e de interpolação diferem entre os motores; a composição precisa ser revisada antes de exportar. Dados originais continuam disponíveis para reconversão. Erros constam do relatório local monstro-migration-report.json.

Código antigo preservado em legacy/monstro-v18, fora dos sourceSets Android, sem um segundo editor instalado. MIT e avisos nativos preservados. Nenhuma promessa de recursos planejados ou dependentes de modelos/credenciais além do que o ClearCut realmente implementa.

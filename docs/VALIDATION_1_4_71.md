# Validação 1.4.71+81 — 2026-10-17

## Objetivo
Corrigir histórico/Novidades defasados e informar diferença entre versões dos dois celulares após resposta autenticada, sem interromper transmissão ou biblioteca remota.

## Evidências locais
- 177 testes Python (`python3 -m unittest discover -s tests -p 'test_*.py' -q`) aprovados, incluindo cinco regressões novas da 1.4.71.
- 64 fontes Kotlin analisadas por parser PSI: nenhum erro sintático.
- 7 cenários Kotlin/JVM de versão igual, diferente, ausente, inválida e entrada malformada passaram isoladamente.
- Teste de protocolo Kotlin/JVM compilado e executado até as regressões do estado de tela apagada; no teste HTTP em loopback, confirmou `X-App-Version: 1.4.71` no `/control/status`, `/control/mcrp/info` e `/control/mcrp/media`, `app_version` no JSON de informações e recusa de token inválido.
- O conjunto completo `scripts/test_protocol.sh` excedeu o tempo disponível nos grupos finais. Não consta como integralmente aprovado.
- Versão atual e metadados sincronizados (`app/build.gradle.kts`, `AGENTS.md`, `README.md`, `CHANGELOG.md`, `docs/VERSIONING.md`, `docs/RELEASE_NOTES.md`, `strings.xml`, `Sobre`, `Novidades`).
- Preflights de recursos, imports, strings Kotlin, versão e referências de controles aprovados.

## Limitações e teste real pendente
- APK Android Release não compilado neste ambiente, por ausência de Gradle instalado.
- Ainda não validados aparelho-a-aparelho o posicionamento do aviso Snackbar, conectividade simultânea e experiência real com versões anteriores.
- Servidor antigo sem `X-App-Version` continua operacional; não é possível afirmar sua versão, portanto a mensagem informa versão desconhecida, não incompatibilidade.

## Roteiro no aparelho
1. Instalar a versão nova nos dois aparelhos. Abrir Sobre e confirmar 1.4.71 e os três releases 1.4.71/70/69. Abrir Novidades e conferir alterações da 1.4.71.
2. Conectar câmera transmitida por outro aparelho 1.4.71: não deve aparecer aviso de diferença.
3. Conectar em aparelho com versão 1.4.70: deve aparecer aviso informativo de versão não anunciada, sem interromper o vídeo nem bloquear os comandos.
4. Após atualizar apenas o transmissor a versão mais recente e manter receptor em uma versão antiga sem suporte ao aviso, a transmissão deve continuar (aviso requer implementação no receptor).
5. Em duas versões futuras diferentes com anúncio `X-App-Version`, validar o aviso com ambos os números e funcionamento do MJPEG e da biblioteca remota.
6. Testar abrir a mesma conexão várias vezes, reconectar e trocar de aparelho; não deve haver alertas repetidos por consultas periódicas de telemetria.

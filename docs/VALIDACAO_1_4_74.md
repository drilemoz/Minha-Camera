# Validação e testes de campo da 1.4.74 (+84)

## Mudanças
- Captura MJPEG Activity/serviço: remove teto artificial de 90 ms em NORMAL, respeitando a meta de 5, 10, 15, 24 ou 30 FPS; o desempenho efetivo depende de câmera, cópia NV21, conversão JPEG, rede e receptor. WARM: mínimo 66 ms; ECO: mínimo 200 ms; HOT: mínimo 300 ms.
- Lanterna traseira via `Camera.Parameters.FLASH_MODE_TORCH` da câmera em uso; POST autenticado `/control/<token>/torch/{off,30,120,record}`. OFF ao interromper sessão, alternar câmera, atingir limite temporal ou estado HOT. No modo `record`, ilumina apenas enquanto o estado de gravação estiver ativo. Só funciona se o driver declarar suporte a TORCH na câmera traseira; não é brilho ajustável.
- Status remoto informa suporte, modo e estado do flash, e o receptor mostra um comando de escolha.
- H264 experimental permanece sujeito a falhas, agora com razões por codec rejeitado no diagnóstico.

## Verificações concluídas fora do Android
- Python: `python -m unittest discover -s tests -p 'test_*.py' -q` -> 185 aprovados.
- Kotlin/JVM: suíte central `ProtocolSmokeKt` compilada e aprovada (controles HTTP e rejeição de token/métodos inadequados).
- Kotlin/JVM: `bash scripts/test_torch.sh` aprovado (TORCH, temporizadores, modo REC, troca de câmera, HOT, modo inválido).
- XML/ZIP: validação estrutural programática.

## Limitações honestas
- Gradle/SDK Android ausentes no ambiente da revisão; `assembleRelease` não foi executado, tampouco testes físicos ou geração de APK.
- 30 FPS é uma meta liberada pela lógica, não taxa comprovada; medir FPS real e latência com dois aparelhos.
- A lanterna pode não funcionar em alguns drivers Camera1 mesmo quando a API anuncia modos de flash; validar após instalar APK com assinatura estável.

## Testes físicos recomendados
1. Redmi A3 com tela apagada e MJPEG 720p/30, Eco desligado: medir FPS transmitido/recebido, calor e queda da bateria por 10 a 30 minutos.
2. Mesmo teste em 1080p/30; alternar 15 e 24 FPS para verificar capacidade sustentável.
3. Lanterna por 30 s e 120 s, depois modo somente REC; encerrar e reiniciar gravação; conferir desligamento real ao expirar, parar serviço, trocar para frontal e diante de aquecimento.
4. Verificar câmera frontal: comando TORCH deve estar indisponível; conferir queda segura para MJPEG quando H264 falhar.
5. Verificar controles com URL/token incorretos (não executar ações), modo com Activity e modo de serviço, rede sem internet.

## Segurança
- Token de controle é segredo; links privados não devem ser compartilhados publicamente. Rede local HTTP não oferece criptografia contra intermediários na mesma LAN. A opção ROTATIVA `record` nunca liga o flash sem uma gravação solicitada.
- Não incluir keystore ou secrets no ZIP; assinatura de release permanece obrigatória conforme AGENTS.md.

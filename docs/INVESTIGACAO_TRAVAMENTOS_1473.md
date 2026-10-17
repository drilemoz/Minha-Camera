# Investigação do congelamento de quadros (1.4.73+83)

O monitor de captura acompanha os callbacks, quadros aceitos, início de trabalho de codificação e quadros publicados, no modo Activity MJPEG e no serviço MJPEG/H.264. Os contadores não persistem a cada frame. Em congelamentos acima de 12 segundos, com a investigação ativada, é salvo um snapshot privado que inclui idade de cada etapa, uso de heap e estados/nomes de métodos de até 12 threads relevantes. Não registra imagens nem URLs ou tokens.

1. Compile o APK e instale no Redmi A3. Abra Transmitir > Diagnóstico e mantenha **Investigação aprofundada: ligada** (padrão).
2. Teste MJPEG em primeiro plano durante 5 minutos e depois Tela apagada por 5 minutos. Não inicie gravação no primeiro teste para isolar a captura.
3. Se congelar, reabra o app e exporte **Diagnóstico local**; o snapshot é persistido pelo processo.
4. Repita com gravação de vídeo, e compare idades de callback, worker e publicação nos snapshots.
5. Se todas as etapas param juntas, gere também **Criar relatório de bugs** nas opções do desenvolvedor do Android. Driver Camera HAL e falhas nativas nem sempre aparecem nos logs internos. Revise informações pessoais antes de compartilhar um bugreport.

Interpretar: callbackHa alto = frames não chegam ao app; callback ativo e workerHa alto = fila bloqueada/sem aceite; worker ativo e publicacaoHa alto = codificação ou envio parado. As contagens são aproximadas sob concorrência, não métricas de FPS. H.264 permanece experimental. Este modo não resolve sozinho problemas de driver nem garante captura de SIGSEGV.

Download remoto: a barra mostra bytes realmente copiados; percentual só é exibido quando há tamanho conhecido pelo HTTP ou índice MCRP. Cancelar interrompe conexão e tenta apagar qualquer arquivo parcial.

Reutilização no Vigia IA: reaproveitar telemetria sem alocação por frame, snapshot local por travamento e transferência com cancelamento, depois de validação em dispositivo.

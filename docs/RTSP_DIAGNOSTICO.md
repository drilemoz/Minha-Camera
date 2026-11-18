# Diagnóstico e abertura do visualizador IP

## O significado da porcentagem

- O limite que o app já usava para cada tentativa continua sendo 15 s. A barra indica o percentual desse **limite consumido**, e não o percentual do vídeo recebido. Quando passa para outro fluxo, transporte ou modo de login, começa outra tentativa.
- Até o primeiro quadro efetivamente exibido, o marcador fica abaixo de 100%. O aparecimento de vídeo encerra a espera imediatamente.
- A estimativa de tempo restante usa a mediana das últimas cinco aberturas bem-sucedidas da mesma combinação de câmera, rota e perfil de tentativa. Não há estimativa antes de haver histórico local. Passada a previsão, o painel informa o atraso e mantém o restante da tentativa.
- São etapas observadas, não sondagens adicionais. O VLC pode informar abertura, buffer e reprodução. Os primeiros dados, decodificação e exibição são observados por amostragem dos contadores, em torno de 250 ms. Nem todo fluxo preenche todos os contadores.
- A política de fallback, cache de rede, autenticação e prioridade TCP permanece a mesma.

## O que o botão Diagnóstico registra

- Abertura da câmera a partir da lista, entrada/saída do visualizador, tentativas, caminho seguro, transporte, mudança de rota, recuperação, erro e tempo até a primeira imagem.
- Modos de imagem e área visível, entrada/saída de tela cheia e PiP. Captura JPEG solicitada, concluída ou com erro.
- Pedido e cancelamento da gravação, confirmação pelo VLC, falha de início, falta de espaço, falha de parada, arquivo vazio ou falha de salvamento, além do tamanho final quando salvo.
- Até 160 eventos por sessão. As duas últimas sessões ficam em armazenamento privado. O relatório também pode recuperar a última exceção não tratada do visualizador Java/Kotlin, sem o texto original da exceção. Não substitui o log de falhas nativas do Android.
- Copiar e exportar TXT usam os dados mais recentes disponíveis. Não inclui vídeo, quadros, senhas ou URLs completas. O endereço do host para diagnóstico e alguns caminhos RTSP padronizados podem aparecer.

## Roteiro de validação em aparelho

1. Abra uma câmera sem amostra anterior. Confira a etapa, a porcentagem com a indicação de limite e a mensagem sem previsão.
2. Abra a mesma câmera de novo e confira a previsão. Teste outra rota para confirmar que a amostra não é reaproveitada indevidamente.
3. Selecione uma lente, ative/sair de tela cheia, capture JPEG e grave/parar. Exporte TXT e confirme as ações e o resultado da gravação.
4. Teste uma tentativa inválida, perda de conexão, troca para alternativa, falha controlada de gravação e abertura de outra câmera. O relatório deve distinguir cada etapa e incluir a sessão anterior.
5. Confirme desenho em retrato/paisagem e Play/PiP, além da preservação do fluxo gravado.

Nesta entrega houve checagem estática e teste de integridade do ZIP. Gradle e o teste em dispositivo ainda estão pendentes.

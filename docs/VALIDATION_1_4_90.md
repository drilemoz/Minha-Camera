# Validação — Minha Câmera 1.4.90+100

## Evidência do aparelho

Os testes externos confirmaram vídeo RTSP, giro horizontal físico pelo ONVIF e escuta na faixa G.711 A-Law do perfil principal. A via de controle local usou a porta 5000 e a rota `/onvif/deviceio_service`. O firmware também respondeu com sucesso a alguns comandos de movimento, mas não confirmou várias paradas. Nada do log externo com credenciais ou endereços foi incluído neste pacote.

## Comportamento da versão

- **Ouvir/Silenciar** altera o volume da reprodução do mesmo player. O áudio pertence à câmera e não ao microfone do telefone. Não há áudio de retorno, novas permissões, nem mudança nas faixas gravadas.
- **Mover câmera** abre um painel ONVIF na rede local. A porta vem preenchida com 5000 e pode ser alterada. A consulta é somente leitura. Perfis PTZ múltiplos são diferenciados pela URI do fluxo ativo. Credenciais e requisições permanecem no endereço IPv4 local configurado, sem seguir redirecionamentos.
- Cada toque pede movimento por 200 ms, em velocidade nominal 0,2, e envia uma parada. Não há fila nem repetição ao segurar a seta. Resposta vazia não é tratada como confirmação. A opção de compatibilidade Jortan/Yoosee seleciona a rota identificada no teste, também selecionada automaticamente quando o firmware conhecido é identificado.
- Não inclui luz branca. O acesso por WAN não é necessário.

## Verificações locais

- Compilação Android `:app:compileDebugKotlin` com SDK 36 aprovada.
- Etapa de preflight do Actions e suíte Python (224 testes) aprovadas.
- Smoke JVM de protocolos aprovou consulta somente leitura, seleção de perfil por URI, sinais das setas, duração explícita, roteamento local, resposta vazia de parada e recusa de login simulada.
- Não foi publicado APK assinado. A escuta e a movimentação em aparelho foram observadas no Onvier, não nesta compilação do Minha Câmera.

## Roteiro no aparelho

1. Abra o perfil principal (`/onvif1`) e toque em **Ouvir**. Use o volume de mídia do telefone. Toque em **Silenciar** e confira que o vídeo não reinicia.
2. Em **Mover câmera**, deixe **5000**, toque em **Conectar** e verifique que conectar não mexe na câmera.
3. Com ela visível fisicamente, teste uma seta por vez e valide que ela gira e para. Se necessário, feche e reabra o painel, marque **Compatibilidade Jortan / Yoosee** e reconecte. Um aceite SOAP não substitui esse teste físico.
4. Repita em tela cheia, alterne as lentes, teste o PiP e faça uma gravação curta para verificar que os controles não interrompem a reprodução nem mudam o arquivo salvo.

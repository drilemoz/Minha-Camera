# Validação — Minha Câmera 1.4.88+98

## Sintoma e causa

Os diagnósticos da câmera ao vivo registraram primeiro quadro exibido e seleção das lentes com proporção `0.0`. A tela preta era a mensagem de recorte ocupando todo o quadro quando a faixa ativa do LibVLC não fornecia dimensões. Isso podia aparecer em retrato e em paisagem, mesmo com a transmissão aberta. A mudança não ajusta o tempo de conexão RTSP.

## Alteração

- A rotação para horizontal entra em tela cheia automaticamente. Ao voltar para vertical, só a tela cheia automática é encerrada. O modo manual fica ativo até sair. A escolha de lente e a sessão permanecem na mesma Activity.
- Leitura primeiro da faixa ativa, depois dos metadados da mídia (faixa identificada ou faixa única) e, por último, da saída com tamanho gerado pelo LibVLC. Evitar usar o tamanho da tela como se fosse tamanho do vídeo. Se não houver dimensão válida, a composição inteira permanece visível com aviso discreto e a escolha não é descartada.
- A orientação usada para dimensionar o LibVLC vem da caixa do vídeo. No fluxo de duas lentes, a caixa da composição pode ser vertical mesmo com o celular horizontal. As barras podem continuar na imagem inteira, para preservar as proporções sem perder bordas.
- Mantidos o RTSP, a captura integral, a gravação do fluxo completo, o PiP e o workflow de um APK direto. Nenhum endpoint ou dado da câmera foi persistido neste relatório.

## Verificações

- Etapa Python de preflight repetida na ordem do Actions. Suíte Python com 224 testes passou, incluindo controle de rotação e ausência de overlay bloqueante.
- Smoke JVM do protocolo (Kotlin 2.2.20) passou. Inclui geometria de duas lentes, retrato/paisagem, retângulo da lente inferior, seleção da fonte da dimensão e tela cheia manual/automática.
- API de dimensionamento verificada contra a biblioteca LibVLC 3.7.7 do projeto.
- Compilação Android local aprovada em `:app:compileDebugKotlin` com SDK 36, Gradle 8.13 e Kotlin 2.2.20, incluindo o processamento dos recursos. Não gera APK assinado.
- Pendente: validar a renderização e a leitura real de dimensões no aparelho com o fluxo da câmera e confirmar build assinado no GitHub Actions.

## Roteiro no aparelho

1. Abra a câmera em retrato, selecione a lente superior e gire para paisagem. Verifique que o mesmo ângulo fica visível e que o log não abre outra tentativa. Repita com a lente inferior.
2. Volte para retrato. Repita partindo de tela cheia manual e confirme que ela permanece ativa até sair.
3. Teste a imagem inteira, PiP, a volta do PiP e rotação durante gravação. Confira se a gravação salva o fluxo completo.
4. Se aparecer o aviso de imagem inteira, exporte o diagnóstico para comparar a fonte das dimensões sem enviar endereço ou credenciais.

# Formulário RTSP e análise de abertura

## Observado em Android 13, app 1.4.79

- Às 23:36:44, o visualizador iniciou o primeiro fluxo, `/onvif1`, em TCP, pela rota padrão do Android, com login na URL.
- Às 23:36:49, houve confirmação de imagem exibida. O relatório usa segundos inteiros, então a diferença de 5 s é aproximada.
- Não houve falhas, tentativas UDP ou uso de `/onvif2`. Nesse caso, reduzir o timeout de 15 s não reduz o tempo de abertura.
- A amostra mostra demux de 721640 bytes, 32 quadros decodificados e 3 exibidos. O contador separado `bytes=0` não significa ausência de dados quando há demux, decodificação e imagem.
- O relatório atual não informa horários de abertura nativa, primeiros dados de mídia ou primeira decodificação. Não atribuir o tempo a GOP, rede, cache ou codec sem medição.

## Próxima investigação do player

1. Fazer cinco aberturas a frio por fluxo, no mesmo aparelho e Wi-Fi, e comparar mediana e pior caso. Testar também no VLC externo com o mesmo fluxo e transporte quando possível.
2. Acrescentar relógio monotônico e métricas para início do LibVLC, abertura, primeira amostra de mídia, vídeo decodificado e primeiro quadro exibido. Não expor credenciais ou detalhes de autenticação no log.
3. Se a mídia chegar rapidamente e o primeiro quadro decodificado demorar, testar GOP/intervalo de quadros-chave menor se o firmware oferecer ajuste. Se decodificar cedo e exibir tarde, testar cache menor em rede estável e comparar travamentos. O cache atual é 500 ms.
4. Segundo fluxo ou H.264 podem ser opções, mas o índice do caminho não comprova codec, qualidade nem latência. Preserve o principal para gravação até testar.

## Alterações e validação

A versão 1.4.80 só altera os formulários (busca, manual e edição). Mantém os mesmos caminhos sugeridos, configurações salvas e política do player.
No aparelho, validar com fonte ampliada e teclado aberto: senha com copiar/colar, sugestão sem teclado, campo personalizado com teclado só ao editar, URL completa e reabertura de câmera previamente salva. No arquivo de prévia, o desenho é ilustrativo e não substitui a validação de layout no Android.

Passaram 195 testes estáticos, incluindo 24 de RTSP, e as checagens de versionamento, recursos, configuração e imports/strings Kotlin. Duas suítes legadas não puderam rodar sem kotlinc. Gradle/SDK Android e validação visual e de desempenho em aparelho permanecem pendentes.

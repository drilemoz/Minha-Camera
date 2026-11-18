# Validação — Minha Câmera 1.4.89+99

## Caso relatado

Na 1.4.88 a seleção chegava ao player, mas o aviso de recorte indisponível deixava o quadro inteiro na tela. A câmera reproduzia as duas imagens empilhadas e o LibVLC não fornecia as dimensões necessárias ao recorte. Trocar entre os endereços RTSP já testados não isolava um ângulo.

## Alteração

- Ao escolher **Lente superior** ou **Lente inferior** sem geometria disponível, o player recorta a metade correspondente de uma composição nominal de duas imagens 16:9 empilhadas (proporção total 8:9). Essa proporção corresponde aproximadamente à composição apresentada no print. É uma predefinição ligada à escolha explícita de lente, sem estimar o vídeo pelo tamanho da tela.
- Dimensões conhecidas prevalecem sobre o padrão. A etiqueta da lente indica quando o recorte usa o padrão. O diagnóstico separa proporção informada e aplicada.
- **Imagem inteira** continua usando a geometria original e não usa o padrão. A seleção ajusta o recorte do mesmo player para retrato, paisagem e PiP, sem mudar endereço, transporte, gravação ou captura integral.
- O padrão de 16:9 por lente pode não corresponder exatamente a outros modelos com proporções diferentes e sem metadados. O modo **Imagem inteira** continua disponível.

## Verificações locais

- Compilação Android `:app:compileDebugKotlin` com SDK 36 aprovada. O APK assinado não foi gerado neste ambiente.
- Os 32 comandos de preflight do GitHub Actions e a suíte Python com 224 testes passaram.
- Smoke Kotlin de protocolos passou. Inclui recorte superior e inferior quando não há geometria, dimensões reais prevalecendo e modo inteiro sem estimativa.
- Pendente: renderização no aparelho com a câmera do relato e publicação da Release assinada.

## Teste no aparelho

Abra a mesma câmera e alterne **Imagem inteira → Lente superior → Lente inferior → Imagem inteira** sem reiniciar o vídeo. Repita em paisagem. Verifique se em cada etapa aparece o enquadramento correspondente. Caso a câmera não informe dimensões, o diagnóstico deve indicar a proporção aplicada de 8:9 para a composição e a etiqueta deve mostrar o recorte padrão 16:9.

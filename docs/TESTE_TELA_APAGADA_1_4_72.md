# Teste real no Redmi A3 — 1.4.72+82

1. Instale o build Release comprovado pelo GitHub Actions no Redmi A3 e no receptor.
2. Escolha MJPEG, 1080p, 30 FPS. Inicie transmissão, confirme quadros, ative **Tela apagada** e depois desligue a tela física do transmissor. Mantenha por 5 minutos.
3. Observe o FPS recebido e exporte diagnóstico. Se ocorrer parada de quadros >12 s, procure evento `Watchdog: publicação parada` com perfil reduzido. Verifique se o fluxo volta sem reiniciar manualmente.
4. Repita em 720p/15 FPS, com e sem gravação MP4. O watchdog evita reconfigurar durante gravação para não corromper vídeo; registre se houver parada.
5. Somente depois teste H.264 experimental. Se houver `Encoder H264: IllegalStateException`, confirme retorno a MJPEG e rejeição de novas tentativas na mesma sessão.
6. Teste o título com nomes curtos e longos, codec e bug na ActionBar, zoom por pinça, botões −/1×/+, pressão longa no número e alternância vertical/horizontal.

> Observação: recuperação por watchdog não prova que a câmera do aparelho continuará funcionando com tela fisicamente apagada; falhas de driver e restrições de bateria Android precisam de testes físicos. Os registros já existentes mostram uma falha real sem quadros e erro EGL=0, mas não sua causa definitiva.

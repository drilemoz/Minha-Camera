# Validação — Minha Câmera 1.4.87+97

## Build APK #84

A 1.4.86+96 passou na checagem de versão e nos testes iniciais. O comando isolado `python3 -m unittest discover -s tests -p test_github_updater_1481.py -q` falhou com `ModuleNotFoundError: No module named 'release_apk_policy'`, antes de preparar a assinatura, compilar ou publicar o APK.

O teste carregava `prepare_github_release.py` diretamente pelo caminho, fora do pacote. Na descoberta global, outro teste adicionava a pasta `scripts` ao caminho de imports e mascarava a falha.

## Correção e verificações

- O teste importa o preparador pelo pacote. Os scripts continuam executáveis tanto por caminho (como na CI) quanto com `python3 -m scripts.…`.
- Foram reproduzidos localmente, na mesma ordem e com Python iniciado separadamente em cada chamada, os 32 comandos da etapa de preflight. O comando que falhou no Build APK #84 passou isoladamente e na sequência.
- Os testes novos verificam os dois modos de invocação, incluindo a CLI fora da pasta do projeto.
- A suíte completa também passou, com 222 testes. A reprodução independente do preflight é a evidência principal para o erro do #84.
- O limite de 200 MiB, a compactação nativa, o certificado fixado, as quatro ABIs e a publicação de um único APK universal foram preservados.

## Pendente

Executar novo workflow no repositório oficial para compilar, assinar, medir e publicar a 1.4.87. Confirmar a detecção e instalação da Release em aparelho com uma versão anterior compatível. O log #84 não comprova o tamanho final, pois a compilação não chegou a iniciar.

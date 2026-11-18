# Validação — Minha Câmera 1.4.86+96

## Origem da falha

O job de build do Build APK #83 concluiu a compilação, assinatura e verificação dos cinco APKs da 1.4.85+95. O job de publicação criou a Release v1.4.85, mas a confirmação da API falhou nas quatro tentativas porque o universal tinha 218.391.269 bytes. O máximo aceito pelo atualizador desde a 1.4.81 é 209.715.200 bytes (200 MiB).

## Correção e evidência

- Ativado o empacotamento JNI compactado com `packaging.jniLibs.useLegacyPackaging = true`. Mantidas as quatro ABIs e a chave de Release já configurada. O Gradle é responsável pelo empacotamento antes da assinatura, sem modificar APK já assinado.
- O verificador lê o tamanho do universal e a compressão efetiva de cada biblioteca nativa no ZIP do APK. O lote é conferido de novo antes do upload. A confirmação no GitHub também verifica tamanho, SHA-256 e anexo único.
- O AAR LibVLC 3.7.7 obtido do Maven Central contém as 12 bibliotecas esperadas. São 210.053.544 bytes sem compressão e 92.327.176 bytes compactados no AAR. Ao aplicar essa diferença ao APK real da 1.4.85, a estimativa para o novo universal é de ~100,7 MB, não uma medição do APK 1.4.86 assinado.
- Passaram 221 testes da suíte Python (incluindo simulação de publicação e reprodução do tamanho do #83), 6 casos JUnit do atualizador com Kotlin 2.2.20, além do versionamento, recursos Android e sintaxe do script de publicação.
- Pendentes: empacotar e assinar a Release real na CI, confirmar o tamanho no relatório, verificar o único asset da Release e testar detecção e instalação em aparelho com uma versão anterior assinada pelo mesmo certificado.

## Teste no GitHub e no aparelho

1. Importar o código da 1.4.86 e compilar na main do repositório oficial com os Secrets de Release existentes.
2. Conferir o tamanho e ABI no relatório do Actions. Verificar se a Release v1.4.86 contém só `MinhaCamera-1.4.86-universal.apk` como anexo.
3. Em um aparelho com a 1.4.81 ou posterior e mesmo certificado, usar **Configurações → Atualizações → Verificar agora**. A versão 1.4.86 deve ser oferecida sem obrigar a instalação da 1.4.85.

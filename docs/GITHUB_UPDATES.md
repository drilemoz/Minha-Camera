# Atualizações do Minha Câmera

A 1.4.81 é a primeira versão com atualizador interno. Instale-a uma vez pelo mesmo meio de distribuição atual, com a chave de Release permanente. A partir dela, versões futuras são procuradas automaticamente.

## No aplicativo

- Fonte fixa: [drilemoz/Minha-Camera Releases](https://github.com/drilemoz/Minha-Camera/releases). O app não pede repositório, usuário nem token.
- A consulta é iniciada ao entrar, respeitando 60 segundos entre consultas automáticas na reentrada, e repetida a cada 15 minutos de uso. Não é um serviço que acorda o aparelho com o app fechado. Consulta e transferência simultâneas são evitadas. Uma pausa deliberada também suspende novas consultas automáticas até retomar ou parar o download.
- O APK universal é baixado automaticamente por padrão, inclusive se a única internet disponível for móvel. Em **Configurações → Atualizações**, desative **Baixar automaticamente** para escolher quando baixar.
- A tela inclui a verificação manual, estado, tamanho, progresso, velocidade, pausar/retomar, parar/reiniciar, notas das versões puladas e link oficial. Uma pausa preserva o arquivo parcial; se o CDN não aceitar o intervalo solicitado, a retomada baixa desde o início.
- Abertura do instalador só ocorre após tocar em **Instalar APK**. Em Android 8+, talvez seja necessário autorizar o Minha Câmera como fonte e tocar de novo. Instalações antigas ou Debug com outra assinatura não podem receber esse APK por cima.
- O atualizador usa um caminho de internet Android validado sem desvincular o Wi-Fi local que pode estar ativo exclusivamente para as câmeras.

## Publicação

Um artefato de Actions por si só não aparece para o atualizador. No repositório oficial, execute **Actions → Build APK → Run workflow** na branch **main** com **Publicar os APKs verificados em GitHub Releases** marcado. A CI preserva os artefatos normais do build e usa um segundo job de publicação com permissão de escrita somente nesta execução. O job:

1. Recebe apenas o lote que já passou por verificação de identidade, ABIs, hash e certificado permanente.
2. Confere novamente o arquivo de cada APK contra o relatório, sincroniza `vX.Y.Z` com o código e copia as notas dessa versão.
3. Publica a Release no commit que foi compilado, sem substituir tags ou Releases existentes.

O atualizador procura um APK `MinhaCamera-X.Y.Z-universal.apk` na Release publicada. O servidor precisa informar o SHA-256 do asset. Antes de entregar o arquivo ao instalador, o app confere hash e tamanho, nome do pacote, versão, versionCode superior e assinatura fixada tanto do APK recebido quanto da instalação.

## Validação e migração

Referência de comportamento: `GitHubUpdateCore.kt`, `GitHubUpdateManager.kt` e `GitHubUpdateUi.kt` do Uai Cortes 0.1.307. O parser, seleção do APK universal, Range e notas foram adaptados. O Uai Cortes de referência exigia toque para baixar, enquanto o Minha Câmera habilita o download automático e acrescenta consultas periódicas em primeiro plano.

Roteiro em aparelho: instalar 1.4.81 assinada; disponibilizar uma Release posterior pelo mesmo certificado; testar aviso, download e instalação; testar pausa/Range, rede local sem internet, controle de download automático desativada e app assinado com chave diferente. A migração ao Vigia IA deve usar seu próprio pacote, certificado, formato de asset e repositório.

Não foi publicada uma Release nem foi testada a instalação em aparelho nesta entrega.

Passaram 199 checagens Python, incluindo os cenários de preparação de publicação e adulteração simulada, além da validação YAML, dos recursos e do versionamento. Os testes Kotlin/JUnit, Gradle, assinatura real, verificação do APK e instalação no Android ainda dependem da CI e de aparelho.

# Atualizações do Minha Câmera

A 1.4.81 é a primeira versão com atualizador interno. Instale-a uma vez pelo mesmo meio de distribuição atual, com a chave de Release permanente. A partir dela, versões futuras são procuradas automaticamente.

## No aplicativo

- Fonte fixa: [drilemoz/Minha-Camera Releases](https://github.com/drilemoz/Minha-Camera/releases). O app não pede repositório, usuário nem token.
- A consulta é iniciada ao entrar, respeitando 60 segundos entre consultas automáticas na reentrada, e repetida a cada 2 minutos de uso desde a 1.4.84. Não é um serviço que acorda o aparelho com o app fechado. Consulta e transferência simultâneas são evitadas. Uma pausa deliberada também suspende novas consultas automáticas até retomar ou parar o download.
- O APK universal é baixado automaticamente por padrão, inclusive se a única internet disponível for móvel. Em **Configurações → Atualizações**, desative **Baixar automaticamente** para escolher quando baixar.
- A tela inclui a verificação manual, estado, tamanho, progresso, velocidade, pausar/retomar, parar/reiniciar, notas das versões puladas e link oficial. Uma pausa preserva o arquivo parcial; se o CDN não aceitar o intervalo solicitado, a retomada baixa desde o início.
- Abertura do instalador só ocorre após tocar em **Instalar APK**. Em Android 8+, talvez seja necessário autorizar o Minha Câmera como fonte e tocar de novo. Instalações antigas ou Debug com outra assinatura não podem receber esse APK por cima.
- O atualizador usa um caminho de internet Android validado sem desvincular o Wi-Fi local que pode estar ativo exclusivamente para as câmeras.

## Publicação

Um artefato de Actions por si só não aparece para o atualizador. Até a 1.4.83, a publicação estava restrita a uma execução manual. A partir da 1.4.84, um push na **main** do repositório oficial publica a Release depois de compilar, assinar e verificar os APKs. No modo manual, **Actions → Build APK → Run workflow**, a opção de publicação vem marcada, mas pode ser desmarcada para compilar sem publicar. O build continua com permissão de leitura e os artefatos permanecem disponíveis no Actions. O segundo job, com permissão de publicação, é responsável por:

1. Recebe apenas o lote que já passou por verificação de identidade, ABIs, hash e certificado permanente.
2. Confere novamente os APKs contra o relatório e bloqueia o universal acima de 200 MiB antes de criar a Release. Sincroniza `vX.Y.Z` com o código e copia as notas dessa versão.
3. Publicar a Release no commit compilado, desde que ainda seja a cabeça da `main`, sem substituir tags ou APKs já publicados. Desde a 1.4.85, só é anexado o arquivo direto `MinhaCamera-X.Y.Z-universal.apk`. Não são anexados ZIP, checksum avulso ou APKs por arquitetura.
4. Confirmar que a API do GitHub informa exatamente um anexo, com URL, tamanho e SHA-256 esperados, e publicar o link direto no resumo. Uma Release já existente e íntegra é mantida sem sobrescrever arquivos.

Os cinco binários e relatórios continuam nos artefatos do Actions. O GitHub gera separadamente links `Source code (zip)` e `Source code (tar.gz)` para cada tag. São arquivos do código-fonte, não anexos do workflow ou APKs para instalar.

O Build APK #83 criou a Release 1.4.85, mas seu APK de 218.391.269 bytes foi recusado pela confirmação e ultrapassa o limite do atualizador. A 1.4.86 compacta bibliotecas nativas sem mudar esse limite. O Build APK #84 parou em um teste antes de compilar a 1.4.86. A 1.4.87 corrige essa etapa e mantém o empacotamento compacto. Quando publicada com o mesmo certificado, instalações da 1.4.81 ou superiores poderão atualizar direto para a 1.4.87, sem precisar instalar as versões intermediárias. Use **Verificar agora** para não esperar o próximo ciclo da versão instalada.

O atualizador procura um APK `MinhaCamera-X.Y.Z-universal.apk` na Release publicada. O servidor precisa informar o SHA-256 do asset. Antes de entregar o arquivo ao instalador, o app confere hash e tamanho, nome do pacote, versão, versionCode superior e assinatura fixada tanto do APK recebido quanto da instalação.

## Validação e migração

Referência de comportamento: `GitHubUpdateCore.kt`, `GitHubUpdateManager.kt` e `GitHubUpdateUi.kt` do Uai Cortes 0.1.307. O parser, seleção do APK universal, Range e notas foram adaptados. O Uai Cortes de referência exigia toque para baixar, enquanto o Minha Câmera habilita o download automático e acrescenta consultas periódicas em primeiro plano.

Roteiro em aparelho: instalar 1.4.81 assinada; disponibilizar uma Release posterior pelo mesmo certificado; testar aviso, download e instalação; testar pausa/Range, rede local sem internet, controle de download automático desativada e app assinado com chave diferente. A migração ao Vigia IA deve usar seu próprio pacote, certificado, formato de asset e repositório.

O novo fluxo foi verificado localmente com simulações de publicação de um único APK, rejeição de anexo extra, reexecução e revisão ultrapassada. A Release real e a instalação em aparelho ainda dependem da execução no repositório oficial.

A correção do Build APK #84 foi validada com o preflight executado em processos separados. A validação do APK assinado e da instalação continua pendente. Veja [validação da 1.4.87](VALIDATION_1_4_87.md) e [diagnóstico do Build APK #83](VALIDATION_1_4_86.md).

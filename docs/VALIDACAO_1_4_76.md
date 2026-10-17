# Build APK 73: falha e correção da 1.4.76+86

## Evidência real
- Job Build APK 73, etapa `Testar protocolos e compilar todos os APKs release`, falha na avaliação do Gradle em `build.gradle.kts:66`.
- Erro: `org.gradle.api.UnknownTaskException: Task with name 'testProtocol' not found in root project 'MinhaCamera'.`
- O bloco `tasks.named("testProtocol") { dependsOn("testTorch") }` existia antes do `tasks.register<JavaExec>("testProtocol")` no script 1.4.75.
- Nenhum compilador Android executou nesta tentativa; não atribuir o erro ao dispositivo, aos codecs, ao flash ou ao código Kotlin do aplicativo.

## Correção
- `tasks.named("testProtocol")` movido para após o registro da tarefa, mantendo a dependência de `testTorch` e isolamento de stubs da versão 1.4.75.
- Criado `tests/test_gradle_registration_1476.py` para garantir que todas as tarefas `tasks.named` do projeto raiz sejam registradas anteriormente no mesmo script, com execução antecipada na CI.
- Sequência 1.4.76 / versionCode 86 e notas de release sincronizadas.

## Limites de validação
- 189 testes Python aprovados via `python -m unittest discover -s tests -p "test_*.py" -q`.
- Teste Kotlin isolado da lanterna aprovado via `bash scripts/test_torch.sh`.
- Validadores locais aprovados: versão, recursos, IDs do receptor/transmissor, configuração de build, fontes de protocolo, imports, Gravity, strings e verificações de layout.
- Estes testes verificam ordem e consistência, mas não substituem avaliação real do Gradle nem compilação Android em SDK.
- Aguarda o próximo workflow para confirmar `testProtocol testReleaseUnitTest assembleRelease` e assinatura.

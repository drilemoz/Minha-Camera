# Validação do Build APK 72: 1.4.75+85

## Evidência do log
- `:compileProtocolTests FAILED`, antes de `assembleRelease`.
- `tests/PhoneTorchControllerSmoke.kt:11`: `unresolved reference PhoneTorchController`.
- `tests/protocol-stubs/SystemClockStub.kt` e `tests/torch-stubs/HandlerStub.kt`: declaração duplicada de `android.os.SystemClock`, causando também erros de `nowMs`.
- Aviso de `jvmTarget` obsoleto não causou a falha.

## Correção
- Conjunto `protocolSources`: exclui `tests/torch-stubs/**` e `PhoneTorchControllerSmoke.kt`.
- Conjunto `torchSources`: inclui apenas stubs próprios `CameraStub.kt`/`HandlerStub.kt`, `PhoneTorchController.kt`, `PhoneEcoPolicy.kt` e teste de lanterna.
- `compileTorchTests` cria seu diretório de classes separado; `testTorch` executa a verificação; `testProtocol` depende dela.
- Mantidos os protocolos, componentes Android, autenticação e proteções térmicas da versão anterior.

## Verificações
- Checagem de versão, fontes, recursos, testes Python e Kotlin/JVM executáveis localmente. O ambiente local não dispõe de Gradle/SDK Android completos para rodar `testProtocol testReleaseUnitTest assembleRelease`; verificar o próximo build no GitHub Actions.
- `test_ci_torch_compilation_1475.py` impede que a fonte duplicada volte à compilação geral.

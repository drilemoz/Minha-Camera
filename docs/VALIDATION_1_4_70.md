# Validação 1.4.70+80 — 2026-10-17

- Origem: ZIP 1.4.69+79; Build APK 68 analisado. Erros: `Unresolved reference Gravity` nas linhas 897 e 903 de `PhoneCameraViewerActivity.kt` (base 1.4.68); solução: `import android.view.Gravity`.
- Reorganização visual: remover nome duplicado, associar único "Ao vivo" ao relógio, mover indicador de carga para antes da temperatura, codec e diagnóstico no cabeçalho e submenu de formato embutido.
- Verificação adicional no workflow: `scripts/check_kotlin_gravity_import.py`.
- Testes de regressão: `tests/test_viewer_header_1470.py`; testes antigos de layout adaptados para o novo desenho. Não ocultar falhas reais de compilação.
- Compile `gradle testProtocol testReleaseUnitTest assembleRelease --build-cache` e instale no Android para validar largura de tela, transições MJPEG/H.264 e eventuais cores verdes em H.264.
- Nenhum teste físico executado no Redmi A3 neste ambiente.

## Resultados locais

- `pytest -q tests`: 195 testes aprovados, 2 subtestes aprovados.
- Parser PSI Kotlin: 62 fontes Android, sem erros sintáticos.
- Recursos: 85 arquivos XML parseados; verificadores de IDs do receptor, imports, strings Kotlin, versionamento e configuração Android aprovados.
- Verificação preventiva de `Gravity` adicionada ao preflight do GitHub Actions.
- **Pendente:** compilação Android completa no GitHub Actions, captura real no Redmi A3 e validação visual em modo paisagem.

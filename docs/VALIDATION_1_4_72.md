# Verificações locais — Minha Câmera 1.4.72+82

- 177 testes Python `unittest discover` passaram (incluindo a nova regressão de ActionBar, zoom compacto e watchdog).
- `scripts/check_version.py`, `check_android_resources.py`, `check_viewer_bindings.py`, `check_transmitter_bindings.py`, `check_kotlin_imports.py`, `check_kotlin_strings.py`, `check_build_config.py` e `check_protocol_sources.py`: aprovados.
- Parsing PSI das 65 fontes Kotlin: zero erros de sintaxe.
- `PhoneBackgroundRecoverySmoke.kt` compilou e executou no Kotlin/JVM, cobrindo as degradações escalonadas de resolução e FPS.
- ZIP integral sem erros CRC.

## Limitações

Não há Gradle/Android SDK pronto no ambiente para compilar o APK. Os testes não validam o pipeline de Camera1, EGL nem o bug físico do Redmi A3. O diagnóstico anexado mostra interrupção dos quadros MJPEG sem falha EGL e outro erro independente H.264 `IllegalStateException`. Recuperação por diminuição de carga não substitui correção definitiva do driver. O workflow do GitHub Actions deve compilar e o teste em aparelho deve verificar o resultado.

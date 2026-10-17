plugins {
    id("com.android.application") version "8.11.1" apply false
    id("org.jetbrains.kotlin.android") version "2.2.20" apply false
}


// Executa a suíte existente sem instalar outro compilador Kotlin no runner.
val protocolCompiler by configurations.creating
val protocolRuntime by configurations.creating
dependencies {
    protocolCompiler("org.jetbrains.kotlin:kotlin-compiler-embeddable:2.2.20")
    protocolRuntime("org.jetbrains.kotlin:kotlin-stdlib:2.2.20")
}
val protocolClasses = layout.buildDirectory.dir("protocol-tests/classes")
val protocolSources = files(
    fileTree("tests") {
        include("**/*.kt")
        // O teste da lanterna usa stubs próprios da Camera1 e de android.os;
        // compilá-los com protocol-stubs declara SystemClock duas vezes.
        exclude("torch-stubs/**", "PhoneTorchControllerSmoke.kt")
    },
    listOf("NetUtil", "OnvifClient", "RtspProbe", "RtspCodec", "WsDiscovery", "RecordingFiles", "VideoGeometry", "OnvifIdentity", "PhoneCameraUrls", "Nv21Frames", "PhoneCameraServer", "PhoneVersionNotice", "PhoneBatteryReading", "PhoneStreamStats", "PhonePublishedFps", "PhoneEcoPolicy", "PhonePinchZoom", "PhoneBackgroundHandoffPolicy", "PhoneBackgroundRecovery", "PhonePipelineTelemetry", "RemoteTransferProgress", "PhoneAvcAnnexB", "PhoneOrientationMode", "RtspPlaybackPolicy", "RtspServiceDetector", "CameraStreamProtocol").map {
        "app/src/main/java/com/minhacamera/$it.kt"
    }
)
val compileProtocolTests by tasks.registering(JavaExec::class) {
    classpath = protocolCompiler
    mainClass.set("org.jetbrains.kotlin.cli.jvm.K2JVMCompiler")
    inputs.files(protocolSources)
    inputs.files(protocolRuntime)
    outputs.dir(protocolClasses)
    doFirst {
        delete(protocolClasses)
        args = listOf("-no-stdlib", "-no-reflect", "-jvm-target", "17",
            "-classpath", protocolRuntime.asPath, "-d", protocolClasses.get().asFile.absolutePath) +
            protocolSources.files.sortedBy { it.path }.map { it.absolutePath }
    }
}
// Smoke da lanterna em classpath independente: evita classe android.os.SystemClock
// duplicada e inclui explicitamente PhoneTorchController, que não é puro.
val torchClasses = layout.buildDirectory.dir("torch-tests/classes")
val torchSources = files(
    fileTree("tests/torch-stubs") { include("**/*.kt") },
    "tests/PhoneTorchControllerSmoke.kt",
    "app/src/main/java/com/minhacamera/PhoneEcoPolicy.kt",
    "app/src/main/java/com/minhacamera/PhoneTorchController.kt"
)
val compileTorchTests by tasks.registering(JavaExec::class) {
    classpath = protocolCompiler
    mainClass.set("org.jetbrains.kotlin.cli.jvm.K2JVMCompiler")
    inputs.files(torchSources)
    inputs.files(protocolRuntime)
    outputs.dir(torchClasses)
    doFirst {
        delete(torchClasses)
        args = listOf("-no-stdlib", "-no-reflect", "-jvm-target", "17",
            "-classpath", protocolRuntime.asPath, "-d", torchClasses.get().asFile.absolutePath) +
            torchSources.files.sortedBy { it.path }.map { it.absolutePath }
    }
}
tasks.register<JavaExec>("testTorch") {
    dependsOn(compileTorchTests)
    classpath = files(torchClasses) + protocolRuntime
    mainClass.set("com.minhacamera.PhoneTorchControllerSmokeKt")
}
tasks.named("testProtocol") { dependsOn("testTorch") }

// Executado também como dependência de testProtocol na CI: valida concorrência sem câmera física.
tasks.register<JavaExec>("testBackgroundHandoff") {
    dependsOn(compileProtocolTests)
    classpath = files(protocolClasses) + protocolRuntime
    mainClass.set("PhoneBackgroundHandoffSmokeKt")
}
tasks.register<JavaExec>("testProtocol") {
    dependsOn(compileProtocolTests)
    classpath = files(protocolClasses) + protocolRuntime
    mainClass.set("com.minhacamera.ProtocolSmokeKt")
}
tasks.named("testProtocol") { dependsOn("testBackgroundHandoff") }


// Mede quadros realmente publicados, inclusive AVC em tela apagada.
tasks.register<JavaExec>("testPublishedFps") {
    dependsOn(compileProtocolTests)
    classpath = files(protocolClasses) + protocolRuntime
    mainClass.set("com.minhacamera.PhonePublishedFpsSmokeKt")
}
tasks.named("testProtocol") { dependsOn("testPublishedFps") }

// Regressão de versão exibida/negociada, ligada ao mesmo preflight dos protocolos.
tasks.register<JavaExec>("testVersionNotice") {
    dependsOn(compileProtocolTests)
    classpath = files(protocolClasses) + protocolRuntime
    mainClass.set("com.minhacamera.PhoneVersionNoticeSmokeKt")
}
tasks.named("testProtocol") { dependsOn("testVersionNotice") }

// Regressão do congelamento observado ao usar tela apagada no Redmi A3.
tasks.register<JavaExec>("testBackgroundRecovery") {
    dependsOn(compileProtocolTests)
    classpath = files(protocolClasses) + protocolRuntime
    mainClass.set("com.minhacamera.PhoneBackgroundRecoverySmokeKt")
}
tasks.named("testProtocol") { dependsOn("testBackgroundRecovery") }

// Captura determinística das etapas do pipeline e progresso remoto.
tasks.register<JavaExec>("testPipelineInvestigation") {
    dependsOn(compileProtocolTests)
    classpath = files(protocolClasses) + protocolRuntime
    mainClass.set("com.minhacamera.PhonePipelineTelemetrySmokeKt")
}
tasks.named("testProtocol") { dependsOn("testPipelineInvestigation") }

import org.jetbrains.kotlin.cli.common.CLIConfigurationKeys
import org.jetbrains.kotlin.cli.common.config.addKotlinSourceRoots
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSourceLocation
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.cli.common.messages.MessageRenderer
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinToJVMBytecodeCompiler
import org.jetbrains.kotlin.cli.jvm.config.addJvmClasspathRoots
import org.jetbrains.kotlin.cli.jvm.config.addJvmSdkRoots
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.config.ApiVersion
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.JVMConfigurationKeys
import org.jetbrains.kotlin.config.JvmTarget
import org.jetbrains.kotlin.config.LanguageVersion
import org.jetbrains.kotlin.config.LanguageVersionSettingsImpl
import org.jetbrains.kotlin.config.languageVersionSettings
import org.jetbrains.kotlin.utils.PathUtil
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

internal class KotlinScriptCompilerTest {

    val func1 = Path.of("src/test/resources/test.kotlin.script/common/Func1.kt")
    val func2 = Path.of("src/test/resources/test.kotlin.script/common/Func2.kt")
    val testScript = Path.of("src/test/resources/test.kotlin.script/Test.kt")

    @TempDir
    lateinit var tempDir: Path
    lateinit var compiledScriptSrcBaseDir: Path

    @BeforeEach
    internal fun setUp() {
        compiledScriptSrcBaseDir = tempDir.resolve("compiled_scripts")
    }

    @Test
    fun success() {
        compile(listOf(func1, func2), compiledScriptSrcBaseDir, listOf()) // <- works fine. See the issue in test below
        compile(testScript, compiledScriptSrcBaseDir, listOf(compiledScriptSrcBaseDir))
    }

    @Test
    fun fail() {
//        compileScript(listOf(sharScript1, sharScript2), compiledScriptSrcBaseDir, listOf())
//      Want to compile func1 and func2 one by one!
        compile(func1, compiledScriptSrcBaseDir, listOf())
        compile(func2, compiledScriptSrcBaseDir, listOf())

        compile(testScript, compiledScriptSrcBaseDir, listOf(compiledScriptSrcBaseDir))
        //  ^^    Catch the exception: "unresolved reference: test1"
    }


    private fun compile(script: Path, outputDirectory: Path, scriptRoots: List<Path>) {
        compile(listOf(script), outputDirectory, scriptRoots)
    }

    private fun compile(scripts: List<Path>, outputDirectory: Path, scriptRoots: List<Path>) {
        val disposable = Disposer.newDisposable()
        try {
            val messageCollector = PrintingMessageCollector()
            val conf = makeCompilerConfiguration(scripts, outputDirectory, scriptRoots, messageCollector)
            val env =
                KotlinCoreEnvironment.createForProduction(disposable, conf, EnvironmentConfigFiles.JVM_CONFIG_FILES)
            val success = KotlinToJVMBytecodeCompiler.compileBunchOfSources(env)
            if (!success)
                throw RuntimeException(messageCollector.errorMessage);
        } finally {
            KotlinCoreEnvironment.disposeApplicationEnvironment()
            Disposer.dispose(disposable)
        }
    }

    private fun makeCompilerConfiguration(
        scriptFiles: Collection<Path>,
        outputDirectory: Path,
        scriptRoots: List<Path>,
        messageCollector: MessageCollector
    ) =
        CompilerConfiguration().apply {
            val kotlinStdlibJar = File(Unit.javaClass.protectionDomain.codeSource.location.toURI())
            addJvmSdkRoots(PathUtil.getJdkClassesRootsFromCurrentJre())
            addJvmClasspathRoots(ArrayList(scriptRoots.map { it.toFile() }).apply { add(kotlinStdlibJar) })
            addKotlinSourceRoots(scriptFiles.map { it.toString() })

            put(CommonConfigurationKeys.MODULE_NAME, "kotlinScripts")
            put(CLIConfigurationKeys.MESSAGE_COLLECTOR_KEY, messageCollector)
            put(JVMConfigurationKeys.RETAIN_OUTPUT_IN_MEMORY, false)
            put(JVMConfigurationKeys.OUTPUT_DIRECTORY, outputDirectory.toFile())
            put(JVMConfigurationKeys.JVM_TARGET, JvmTarget.JVM_11)

            languageVersionSettings =
                LanguageVersionSettingsImpl(LanguageVersion.LATEST_STABLE, ApiVersion.LATEST_STABLE)
        }

    private class PrintingMessageCollector : MessageCollector {
        var errorMessage: String = ""
            private set

        private var hasErrors = false

        override fun clear() {
            hasErrors = false
        }

        override fun hasErrors() = hasErrors
        override fun report(
            severity: CompilerMessageSeverity,
            message: String,
            location: CompilerMessageSourceLocation?
        ) {
            if (severity.isError) {
                hasErrors = true
                errorMessage += MessageRenderer.PLAIN_RELATIVE_PATHS.render(severity, message, location)
            } else if (severity.isWarning) {
                println(MessageRenderer.PLAIN_RELATIVE_PATHS.render(severity, message, location))
            }
        }
    }
}
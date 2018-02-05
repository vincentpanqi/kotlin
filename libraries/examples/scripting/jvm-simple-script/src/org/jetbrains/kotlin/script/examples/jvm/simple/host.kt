package org.jetbrains.kotlin.script.examples.jvm.simple

import org.jetbrains.kotlin.script.util.KotlinJars
import org.jetbrains.kotlin.utils.PathUtil
import java.io.File
import java.net.URL
import java.net.URLClassLoader
import kotlin.script.experimental.api.*
import kotlin.script.experimental.jvm.*
import kotlin.script.experimental.jvm.impl.KJVMCompilerImpl
import kotlin.script.experimental.jvm.runners.BasicJvmScriptRunner

class MySelector : ScriptSelector {
    override val fileExtension: String = ".kts"

    override fun isKnownScript(script: ScriptSource): Boolean = true
}

class MyConfigurator : ScriptConfigurator {

    companion object {
        val stdlibFile: File by lazy {
            KotlinJars.stdlib
                    ?: throw Exception("Unable to find kotlin stdlib, please specify it explicitly via \"kotlin.java.stdlib.jar\" property")
        }

        val selfFile: File by lazy {
            PathUtil.getResourcePathForClass(MyScript::class.java).takeIf(File::exists)
                    ?: throw Exception("Unable to get path to the script base")
        }
    }

    override suspend fun refineConfiguration(
        configuration: ScriptCompileConfiguration,
        processedScriptData: ProcessedScriptData
    ): ResultWithDiagnostics<ScriptCompileConfiguration> =
        configuration.asSuccess()
}

fun ScriptSource.toBasicJvmConfig(): ScriptCompileConfiguration =
    ScriptCompileConfiguration(
        ScriptCompileConfigurationParams.scriptSourceFragments to ScriptSourceFragments(this, null),
        ScriptCompileConfigurationParams.scriptSignature to ScriptSignature(MyScript::class, ProvidedDeclarations()),
        ScriptCompileConfigurationParams.importedPackages to emptyList<String>(),
        ScriptCompileConfigurationParams.restrictions to ResolvingRestrictions(),
        ScriptCompileConfigurationParams.importedScripts to emptyList<String>(),
        ScriptCompileConfigurationParams.dependencies to listOf(
            JvmDependency(listOf(MyConfigurator.stdlibFile)),
            JvmDependency(listOf(MyConfigurator.selfFile))
        ),
        ScriptCompileConfigurationParams.compilerOptions to emptyList<String>(),
        JvmScriptCompileConfigurationParams.javaHomeDir to File(System.getProperty("java.home"))
    )


fun evalFile(scriptFile: File): ResultWithDiagnostics<EvaluationResult> {
    val scriptCompiler = JvmScriptCompiler(KJVMCompilerImpl(), DummyCompiledJvmScriptCache())
    val configurator = MyConfigurator()
    val baseClassLoader = URLClassLoader(
        arrayOf(
            MyConfigurator.stdlibFile.toURI().toURL(),
            MyConfigurator.selfFile.toURI().toURL()
        )
    )
    val host = JvmBasicScriptingHost(
        configurator,
        scriptCompiler,
        BasicJvmScriptRunner<MyScript>()
    )

    val script = object : ScriptSource {
        override val location: URL? get() = scriptFile.toURI().toURL()
        override val text: String? get() = null
    }

    return host.eval(
        script.toBasicJvmConfig(),
        ScriptEvaluationEnvironment(JvmScriptEvaluationEnvironmentParams.baseClassLoader to baseClassLoader)
    )
}

fun main(vararg args: String) {
    if (args.size != 1) {
        println("usage: <app> <script file>")
    } else {
        val scriptFile = File(args[0])
        println("Executing script $scriptFile")

        val res = evalFile(scriptFile)

        res.reports.forEach {
            println(" : ${it.message}" + if (it.exception == null) "" else ": ${it.exception}")
        }
    }
}

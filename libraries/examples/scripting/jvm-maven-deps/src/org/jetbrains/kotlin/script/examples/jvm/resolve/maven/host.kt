package org.jetbrains.kotlin.script.examples.jvm.resolve.maven

import org.jetbrains.kotlin.script.util.DependsOn
import org.jetbrains.kotlin.script.util.FilesAndMavenResolver
import org.jetbrains.kotlin.script.util.KotlinJars
import org.jetbrains.kotlin.script.util.Repository
import org.jetbrains.kotlin.utils.PathUtil
import java.io.File
import java.net.URL
import java.net.URLClassLoader
import kotlin.script.dependencies.ScriptContents
import kotlin.script.dependencies.ScriptDependenciesResolver
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
            PathUtil.getResourcePathForClass(MyScriptWithMavenDeps::class.java).takeIf(File::exists)
                    ?: throw Exception("Unable to get path to the script base")
        }

        val scriptUtilsJarFile: File by lazy {
            PathUtil.getResourcePathForClass(DependsOn::class.java).takeIf(File::exists)
                    ?: throw Exception("Unable to get path to the kotlin-script-util.jar")
        }
    }

    private val resolver by lazy { FilesAndMavenResolver() }

    override suspend fun refineConfiguration(
        config: ScriptCompileConfiguration,
        processedScriptData: ProcessedScriptData
    ): ResultWithDiagnostics<ScriptCompileConfiguration> {
        val annotations = processedScriptData.getOptional(ProcessedScriptDataParams.annotations)?.toList()?.takeIf { it.isNotEmpty() }
                ?: return config.asSuccess()
        val scriptContents = object : ScriptContents {
            override val annotations: Iterable<Annotation> = annotations
            override val file: File? = null
            override val text: CharSequence? = null
        }
        val diagnostics = arrayListOf<ScriptDiagnostic>()
        fun report(severity: ScriptDependenciesResolver.ReportSeverity, message: String, position: ScriptContents.Position?) {
            diagnostics.add(ScriptDiagnostic(message, mapLegacyDiagnosticSeverity(severity), mapLegacyScriptPosition(position)))
        }
        try {
            val newDeps = resolver.resolve(scriptContents, emptyMap(), ::report, null).get()
                    ?: return config.asSuccess(diagnostics)
            val resolvedClasspath = newDeps.classpath.toList().takeIf { it.isNotEmpty() }
                    ?: return config.asSuccess(diagnostics)
            return ScriptCompileConfiguration(
                config.data +
                        (ScriptCompileConfigurationParams.dependencies to
                                config.data.get(ScriptCompileConfigurationParams.dependencies) as List<JvmDependency> + JvmDependency(
                            resolvedClasspath
                        ))
            ).asSuccess(diagnostics)
        } catch (e: Throwable) {
            return ResultWithDiagnostics.Failure(*diagnostics.toTypedArray(), e.asDiagnostics())
        }
    }
}

fun ScriptSource.toBasicJvmConfig(): ScriptCompileConfiguration =
    ScriptCompileConfiguration(
        ScriptCompileConfigurationParams.scriptSourceFragments to ScriptSourceFragments(this, null),
        ScriptCompileConfigurationParams.scriptSignature to ScriptSignature(MyScriptWithMavenDeps::class, ProvidedDeclarations()),
        ScriptCompileConfigurationParams.importedPackages to listOf(DependsOn::class.qualifiedName!!),
        ScriptCompileConfigurationParams.restrictions to ResolvingRestrictions(),
        ScriptCompileConfigurationParams.importedScripts to emptyList<String>(),
        ScriptCompileConfigurationParams.dependencies to listOf(
            JvmDependency(listOf(MyConfigurator.stdlibFile)),
            JvmDependency(listOf(MyConfigurator.selfFile)),
            JvmDependency(listOf(MyConfigurator.scriptUtilsJarFile))
        ),
        ScriptCompileConfigurationParams.compilerOptions to emptyList<String>(),
        JvmScriptCompileConfigurationParams.javaHomeDir to File(System.getProperty("java.home")),
        ScriptCompileConfigurationParams.updateConfigurationOnAnnotations to listOf(DependsOn::class, Repository::class)
    )

fun evalFile(scriptFile: File): ResultWithDiagnostics<EvaluationResult> {
    val scriptCompiler = JvmScriptCompiler(KJVMCompilerImpl(), DummyCompiledJvmScriptCache())
    val configurationExtractor = MyConfigurator()
    val baseClassLoader = URLClassLoader(
        arrayOf(
            MyConfigurator.stdlibFile.toURI().toURL(),
            MyConfigurator.selfFile.toURI().toURL()
        )
    )
    val host = JvmBasicScriptingHost(
        configurationExtractor,
        scriptCompiler,
        BasicJvmScriptRunner<MyScriptWithMavenDeps>()
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

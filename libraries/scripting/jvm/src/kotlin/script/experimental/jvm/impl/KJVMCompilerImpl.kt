/*
 * Copyright 2010-2018 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package kotlin.script.experimental.jvm.impl

import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.CharsetToolkit
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.impl.PsiFileFactoryImpl
import com.intellij.testFramework.LightVirtualFile
import kotlinx.coroutines.experimental.runBlocking
import org.jetbrains.kotlin.cli.common.CLIConfigurationKeys
import org.jetbrains.kotlin.cli.common.messages.AnalyzerWithCompilerReport
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageLocation
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.cli.jvm.compiler.CliLightClassGenerationSupport
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.cli.jvm.compiler.TopDownAnalyzerFacadeForJVM
import org.jetbrains.kotlin.cli.jvm.config.JvmClasspathRoot
import org.jetbrains.kotlin.cli.jvm.config.addJvmClasspathRoots
import org.jetbrains.kotlin.cli.jvm.config.addJvmSdkRoots
import org.jetbrains.kotlin.codegen.ClassBuilderFactories
import org.jetbrains.kotlin.codegen.GeneratedClassLoader
import org.jetbrains.kotlin.codegen.KotlinCodegenFacade
import org.jetbrains.kotlin.codegen.state.GenerationState
import org.jetbrains.kotlin.config.*
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.parsing.KotlinParserDefinition
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.script.KotlinScriptDefinition
import org.jetbrains.kotlin.utils.PathUtil
import java.io.File
import java.util.concurrent.Future
import kotlin.reflect.KClass
import kotlin.script.dependencies.Environment
import kotlin.script.dependencies.KotlinScriptExternalDependencies
import kotlin.script.dependencies.ScriptContents
import kotlin.script.dependencies.ScriptDependenciesResolver
import kotlin.script.experimental.api.*
import kotlin.script.experimental.dependencies.AsyncDependenciesResolver
import kotlin.script.experimental.dependencies.DependenciesResolver
import kotlin.script.experimental.dependencies.ScriptDependencies
import kotlin.script.experimental.dependencies.ScriptReport
import kotlin.script.experimental.host.getMergedScriptText
import kotlin.script.experimental.jvm.*

class KJVMCompiledScript<out ScriptBase : Any>(
    override val configuration: ScriptCompileConfiguration,
    val generationState: GenerationState,
    val scriptClassFQName: String
) : CompiledScript<ScriptBase> {

    override suspend fun instantiate(scriptEvaluationEnvironment: ScriptEvaluationEnvironment): ResultWithDiagnostics<ScriptBase> = try {
        val classLoader = GeneratedClassLoader(generationState.factory, scriptEvaluationEnvironment[JvmScriptEvaluationEnvironmentParams.baseClassLoader])

        val clazz = classLoader.loadClass(scriptClassFQName)
        (clazz as? ScriptBase)?.asSuccess()
                ?: ResultWithDiagnostics.Failure("Compiled class expected to be a subclass of the <ScriptBase>, but got ${clazz.javaClass.name}".asErrorDiagnostics())
    } catch (e: Throwable) {
        ResultWithDiagnostics.Failure(ScriptDiagnostic("Unable to instantiate class $scriptClassFQName", exception = e))
    }
}

class KJVMCompilerImpl : KJVMCompilerProxy {

    override fun compile(
        scriptCompilerConfiguration: ScriptCompileConfiguration,
        configurator: ScriptConfigurator?
    ): ResultWithDiagnostics<CompiledScript<*>> {
        val messageCollector = ScriptDiagnosticsMessageCollector()

        fun failure(vararg diagnostics: ScriptDiagnostic): ResultWithDiagnostics.Failure<CompiledScript<*>> =
            ResultWithDiagnostics.Failure(*messageCollector.diagnostics.toTypedArray(), *diagnostics)

        try {
            var environment: KotlinCoreEnvironment? = null

            fun updateClasspath(classpath: Iterable<File>) {
                environment!!.updateClasspath(classpath.map(::JvmClasspathRoot))
            }

            val disposable = Disposer.newDisposable()
            val kotlinCompilerConfiguration = org.jetbrains.kotlin.config.CompilerConfiguration().apply {
                add(
                    JVMConfigurationKeys.SCRIPT_DEFINITIONS,
                    BridgeScriptDefinition(scriptCompilerConfiguration, configurator, ::updateClasspath)
                )
                put<MessageCollector>(CLIConfigurationKeys.MESSAGE_COLLECTOR_KEY, messageCollector)
                put(JVMConfigurationKeys.RETAIN_OUTPUT_IN_MEMORY, true)
                addJvmSdkRoots(PathUtil.getJdkClassesRootsFromJre(scriptCompilerConfiguration[JvmScriptCompileConfigurationParams.javaHomeDir].canonicalPath))
                addJvmClasspathRoots(scriptCompilerConfiguration[ScriptCompileConfigurationParams.dependencies].flatMap { (it as JvmDependency).classpath })
                put(CommonConfigurationKeys.MODULE_NAME, "kotlin-script")
                languageVersionSettings = LanguageVersionSettingsImpl(
                    LanguageVersion.LATEST_STABLE, ApiVersion.LATEST_STABLE, mapOf(AnalysisFlag.skipMetadataVersionCheck to true)
                )
            }
            environment = KotlinCoreEnvironment.createForProduction(
                disposable,
                kotlinCompilerConfiguration,
                EnvironmentConfigFiles.JVM_CONFIG_FILES
            )

            val analyzerWithCompilerReport = AnalyzerWithCompilerReport(messageCollector)

            val psiFileFactory: PsiFileFactoryImpl = PsiFileFactory.getInstance(environment.project) as PsiFileFactoryImpl
            val scriptText = scriptCompilerConfiguration[ScriptCompileConfigurationParams.scriptSourceFragments].getMergedScriptText()
            val scriptFileName = "script" // TODO: extract from file/url if available
            val virtualFile = LightVirtualFile(
                "$scriptFileName${KotlinParserDefinition.STD_SCRIPT_EXT}",
                KotlinLanguage.INSTANCE,
                StringUtil.convertLineSeparators(scriptText)
            ).apply {
                charset = CharsetToolkit.UTF8_CHARSET
            }
            val psiFile: KtFile = psiFileFactory.trySetupPsiForFile(virtualFile, KotlinLanguage.INSTANCE, true, false) as KtFile?
                    ?: return failure("Unable to make PSI file from script".asErrorDiagnostics())

            val sourceFiles = listOf(psiFile)

            analyzerWithCompilerReport.analyzeAndReport(sourceFiles) {
                val project = environment.project
                val sourcesOnly = TopDownAnalyzerFacadeForJVM.newModuleSearchScope(project, sourceFiles)
                TopDownAnalyzerFacadeForJVM.analyzeFilesWithJavaIntegration(
                    project,
                    sourceFiles,
                    CliLightClassGenerationSupport.NoScopeRecordCliBindingTrace(),
                    environment.configuration,
                    environment::createPackagePartProvider,
                    sourceModuleSearchScope = sourcesOnly
                )
            }
            val analysisResult = analyzerWithCompilerReport.analysisResult

            if (!analysisResult.shouldGenerateCode) return failure("no code to generate".asErrorDiagnostics())
            if (analysisResult.isError() || messageCollector.hasErrors()) return failure()

            val generationState = GenerationState.Builder(
                psiFile.project,
                ClassBuilderFactories.binaries(false),
                analysisResult.moduleDescriptor,
                analysisResult.bindingContext,
                sourceFiles,
                kotlinCompilerConfiguration
            ).build()
            generationState.beforeCompile()
            KotlinCodegenFacade.generatePackage(
                generationState,
                psiFile.script!!.containingKtFile.packageFqName,
                setOf(psiFile.script!!.containingKtFile),
                org.jetbrains.kotlin.codegen.CompilationErrorHandler.THROW_EXCEPTION
            )

            val res = KJVMCompiledScript<Any>(scriptCompilerConfiguration, generationState, scriptFileName.capitalize())

            return ResultWithDiagnostics.Success(res, messageCollector.diagnostics)
        } catch (ex: Throwable) {
            return failure(ex.asDiagnostics())
        }
    }
}

class ScriptDiagnosticsMessageCollector : MessageCollector {

    private val _diagnostics = arrayListOf<ScriptDiagnostic>()

    val diagnostics: List<ScriptDiagnostic> get() = _diagnostics

    override fun clear() {
        _diagnostics.clear()
    }

    override fun hasErrors(): Boolean =
        _diagnostics.any { it.severity == ScriptDiagnostic.Severity.ERROR }


    override fun report(severity: CompilerMessageSeverity, message: String, location: CompilerMessageLocation?) {
        val mappedSeverity = when (severity) {
            CompilerMessageSeverity.EXCEPTION,
            CompilerMessageSeverity.ERROR -> ScriptDiagnostic.Severity.ERROR
            CompilerMessageSeverity.STRONG_WARNING,
            CompilerMessageSeverity.WARNING -> ScriptDiagnostic.Severity.WARNING
            CompilerMessageSeverity.INFO -> ScriptDiagnostic.Severity.INFO
            CompilerMessageSeverity.LOGGING -> ScriptDiagnostic.Severity.DEBUG
            else -> null
        }
        if (mappedSeverity != null) {
            val mappedLocation = location?.let {
                ScriptSource.Location(ScriptSource.Position(it.line, it.column))
            }
            _diagnostics.add(ScriptDiagnostic(message, mappedSeverity, mappedLocation))
        }
    }
}

// A bridge to the current scripting

internal class BridgeDependenciesResolver(
    val scriptCompilerConfiguration: ScriptCompileConfiguration,
    val scriptConfigurator: ScriptConfigurator?,
    val updateClasspath: (List<File>) -> Unit
) : AsyncDependenciesResolver {

    override fun resolve(scriptContents: ScriptContents, environment: Environment): DependenciesResolver.ResolveResult = runBlocking {
        resolveAsync(scriptContents, environment)
    }

    override suspend fun resolveAsync(scriptContents: ScriptContents, environment: Environment): DependenciesResolver.ResolveResult = try {

        val diagnostics = arrayListOf<ScriptReport>()
        val processedScriptData = ProcessedScriptData(ProcessedScriptDataParams.annotations to scriptContents.annotations)

        val refinedConfiguration = scriptConfigurator?.refineConfiguration(scriptCompilerConfiguration, processedScriptData)?.let {

            when (it) {
                is ResultWithDiagnostics.Failure ->
                    return DependenciesResolver.ResolveResult.Success(
                        ScriptDependencies(
                            imports = scriptCompilerConfiguration[ScriptCompileConfigurationParams.importedPackages].toList()
                        ),
                        it.reports.mapScriptReportsToDiagnostics()
                    )
                is ResultWithDiagnostics.Success -> {
                    diagnostics.addAll(it.reports.mapScriptReportsToDiagnostics())
                    it.value
                }
            }
        } ?: scriptCompilerConfiguration

        val newClasspath = refinedConfiguration[ScriptCompileConfigurationParams.dependencies].flatMap { (it as JvmDependency).classpath }
        if (refinedConfiguration != scriptCompilerConfiguration) {
            val oldClasspath =
                scriptCompilerConfiguration[ScriptCompileConfigurationParams.dependencies].flatMap { (it as JvmDependency).classpath }
            if (newClasspath != oldClasspath) {
                updateClasspath(newClasspath)
            }
        }
        DependenciesResolver.ResolveResult.Success(
            ScriptDependencies(
                classpath = newClasspath, // TODO: maybe it should return only increment from the initial config
                imports = refinedConfiguration[ScriptCompileConfigurationParams.importedPackages].toList()
            ),
            diagnostics
        )
    } catch (e: Throwable) {
        DependenciesResolver.ResolveResult.Failure(ScriptReport(e.message ?: "unknown error $e"))
    }
}

private fun List<ScriptDiagnostic>.mapScriptReportsToDiagnostics() =
    map { ScriptReport(it.message, mapToLegacyScriptReportSeverity(it.severity), mapToLegacyScriptReportPosition(it.location)) }

internal class BridgeScriptDefinition(
    scriptCompilerConfiguration: ScriptCompileConfiguration,
    scriptConfigurator: ScriptConfigurator?,
    updateClasspath: (List<File>) -> Unit
) : KotlinScriptDefinition(scriptCompilerConfiguration[ScriptCompileConfigurationParams.scriptSignature].scriptBase as KClass<out Any>) {
    override val acceptedAnnotations =
        scriptCompilerConfiguration.getOptional(ScriptCompileConfigurationParams.updateConfigurationOnAnnotations)?.toList() ?: emptyList()

    override val dependencyResolver: DependenciesResolver =
        BridgeDependenciesResolver(scriptCompilerConfiguration, scriptConfigurator, updateClasspath)
}

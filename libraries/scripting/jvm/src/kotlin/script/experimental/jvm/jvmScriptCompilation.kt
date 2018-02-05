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

@file:Suppress("unused")

package kotlin.script.experimental.jvm

import kotlin.script.experimental.api.*

open class JvmScriptCompiler(
    val compilerProxy: KJVMCompilerProxy,
    val cache: CompiledJvmScriptsCache
) : ScriptCompiler {

    override suspend fun compile(configuration: ScriptCompileConfiguration, configurator: ScriptConfigurator?): ResultWithDiagnostics<CompiledScript<*>> {
        val refinedConfiguration = configurator?.refineConfiguration(configuration)?.let {
            when (it) {
                is ResultWithDiagnostics.Failure -> return it.convert()
                is ResultWithDiagnostics.Success -> it.value
                        ?: return ResultWithDiagnostics.Failure("Null script compile configuration received".asErrorDiagnostics())
            }
        } ?: configuration
        val cached = cache[refinedConfiguration[ScriptCompileConfigurationParams.scriptSourceFragments]]

        if (cached != null) return cached.asSuccess()

        return compilerProxy.compile(refinedConfiguration, configurator).also {
            if (it is ResultWithDiagnostics.Success) {
                cache.store(it.value as CompiledScript<*>)
            }
        }
    }
}

interface CompiledJvmScriptsCache {
    operator fun get(script: ScriptSourceFragments): CompiledScript<*>?
    fun store(compiledScript: CompiledScript<*>)
}

interface KJVMCompilerProxy {
    fun compile(
        scriptCompilerConfiguration: ScriptCompileConfiguration,
        configurator: ScriptConfigurator?
    ): ResultWithDiagnostics<CompiledScript<*>>
}

class DummyCompiledJvmScriptCache : CompiledJvmScriptsCache {
    override operator fun get(script: ScriptSourceFragments): CompiledScript<*>? = null
    override fun store(compiledScript: CompiledScript<*>) {}
}


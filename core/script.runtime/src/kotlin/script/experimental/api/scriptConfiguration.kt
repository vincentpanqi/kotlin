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

package kotlin.script.experimental.api

import kotlin.reflect.KClass

object ScriptCompileConfigurationParams {

    val scriptSourceFragments by typedKey<ScriptSourceFragments>()

    val scriptSignature by typedKey<ScriptSignature>()

    val importedPackages by typedKey<Iterable<String>>()

    val restrictions by typedKey<ResolvingRestrictions>()

    val importedScripts by typedKey<Iterable<ScriptSource>>()

    val dependencies by typedKey<Iterable<ScriptDependency>>()

    val compilerOptions by typedKey<Iterable<String>>() // Q: CommonCompilerOptions instead?

    val updateConfigurationOnAnnotations by typedKey<Iterable<KClass<out Annotation>>>()

    val updateConfigurationOnSections by typedKey<Iterable<String>>()
}

typealias ScriptCompileConfiguration = HeterogeneousMap

fun ScriptSource.toScriptCompileConfiguration(vararg pairs: Pair<TypedKey<*>, Any?>) =
    ScriptCompileConfiguration(ScriptCompileConfigurationParams.scriptSourceFragments to ScriptSourceFragments(this, null), *pairs)

object ProcessedScriptDataParams {
    val annotations by typedKey<Iterable<Annotation>>()

    val fragments by typedKey<Iterable<ScriptSourceNamedFragment>>()
}

typealias ProcessedScriptData = HeterogeneousMap


interface ScriptConfigurator {

    suspend fun refineConfiguration(
        configuration: ScriptCompileConfiguration,
        processedScriptData: ProcessedScriptData = ProcessedScriptData()
    ): ResultWithDiagnostics<ScriptCompileConfiguration>
}


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

import java.net.URL
import kotlin.reflect.KClass
import kotlin.reflect.KType

interface ScriptSource {
    val location: URL?
    val text: String?

    data class Position(val line: Int, val col: Int, val absolutePos: Int? = null)
    data class Range(val start: Position, val end: Position)
    data class Location(val start: Position, val end: Position? = null)
}

data class ScriptSourceNamedFragment(val name: String?, val range: ScriptSource.Range)

open class ScriptSourceFragments(
    val originalSource: ScriptSource,
    val fragments: List<ScriptSourceNamedFragment>?)

open class ProvidedDeclarations(
    val implicitReceivers: List<KType> = emptyList(), // previous scripts, etc.
    val contextVariables: Map<String, KType> = emptyMap() // external variables
    // Q: do we need context constants and/or types here, e.g.
    // val contextConstants: Map<String, Any?> // or with KType as well
    // val contextTypes: List<KType> // additional (to the classpath) types provided by the environment
    // alternatively:
    // val contextDeclarations: List<Tuple<DeclarationKind, String?, KType, Any?> // kind, name, type, value
    // OR: it should be a HeterogeneousMap too
)

open class ScriptSignature(
    val scriptBase: KClass<*>,
    val providedDeclarations: ProvidedDeclarations
)

open class ResolvingRestrictions {
    data class Rule(
        val allow: Boolean,
        val pattern: String // FQN wildcard
    )

    val rules: Iterable<Rule> = arrayListOf()
}

interface ScriptDependency {
    // Q: anything generic here?
}

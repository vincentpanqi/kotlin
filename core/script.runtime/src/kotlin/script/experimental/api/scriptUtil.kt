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

package kotlin.script.experimental.api

import kotlin.reflect.KProperty

data class TypedKey<T>(val name: String)

class TypedKeyDelegate<T> {
    operator fun getValue(thisRef: Any?, property: KProperty<*>): TypedKey<T> = TypedKey(property.name)
}

fun <T> typedKey() = TypedKeyDelegate<T>()

class HeterogeneousMap(val data: Map<TypedKey<*>, Any?> = hashMapOf()) {
    constructor(vararg pairs: Pair<TypedKey<*>, Any?>) : this(hashMapOf(*pairs))
}

fun HeterogeneousMap.cloneWith(vararg pairs: Pair<TypedKey<*>, Any?>) = HeterogeneousMap(HashMap(data).apply { putAll(pairs) })

operator fun <T> HeterogeneousMap.get(key: TypedKey<T>): T = data[key] as T

fun <T> HeterogeneousMap.getOptional(key: TypedKey<T>): T? = data[key] as T?


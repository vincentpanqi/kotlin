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

data class ScriptDiagnostic(
    val message: String,
    val severity: Severity = Severity.ERROR,
    val location: ScriptSource.Location? = null,
    val exception: Throwable? = null
) {
    enum class Severity { ERROR, WARNING, INFO, DEBUG }
}

sealed class ResultWithDiagnostics<out R : Any?> {
    abstract val reports: List<ScriptDiagnostic>

    data class Success<out R : Any?>(
        val value: R?,
        override val reports: List<ScriptDiagnostic> = listOf()
    ) : ResultWithDiagnostics<R>()

    data class Failure<out R : Any?>(
        override val reports: List<ScriptDiagnostic>
    ) : ResultWithDiagnostics<R>() {
        constructor(vararg reports: ScriptDiagnostic) : this(reports.asList())

        fun <T> convert(): ResultWithDiagnostics.Failure<T> = ResultWithDiagnostics.Failure(reports)
    }
}

operator fun <R : Any?> List<ScriptDiagnostic>.plus(res: ResultWithDiagnostics<R>): ResultWithDiagnostics<R> = when (res) {
    is ResultWithDiagnostics.Success -> ResultWithDiagnostics.Success(res.value, this + res.reports)
    is ResultWithDiagnostics.Failure -> ResultWithDiagnostics.Failure(this + res.reports)
}

fun <R : Any> R.asSuccess(reports: List<ScriptDiagnostic> = listOf()): ResultWithDiagnostics.Success<R> =
    ResultWithDiagnostics.Success(this, reports)

fun Throwable.asDiagnostics(customMessage: String? = null, location: ScriptSource.Location? = null): ScriptDiagnostic =
    ScriptDiagnostic(customMessage ?: message ?: "$this", ScriptDiagnostic.Severity.ERROR, location, this)

fun String.asErrorDiagnostics(location: ScriptSource.Location? = null): ScriptDiagnostic =
    ScriptDiagnostic(this, ScriptDiagnostic.Severity.ERROR, location)

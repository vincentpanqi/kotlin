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

import kotlin.script.experimental.api.ScriptDiagnostic
import kotlin.script.experimental.api.ScriptSource
import kotlin.script.dependencies.ScriptContents
import kotlin.script.dependencies.ScriptDependenciesResolver
import kotlin.script.experimental.dependencies.ScriptReport

fun mapLegacyDiagnosticSeverity(severity: ScriptDependenciesResolver.ReportSeverity): ScriptDiagnostic.Severity = when (severity) {
    ScriptDependenciesResolver.ReportSeverity.ERROR -> ScriptDiagnostic.Severity.ERROR
    ScriptDependenciesResolver.ReportSeverity.WARNING -> ScriptDiagnostic.Severity.WARNING
    ScriptDependenciesResolver.ReportSeverity.INFO -> ScriptDiagnostic.Severity.INFO
    ScriptDependenciesResolver.ReportSeverity.DEBUG -> ScriptDiagnostic.Severity.DEBUG
}

fun mapToLegacyScriptReportSeverity(severity: ScriptDiagnostic.Severity): ScriptReport.Severity = when (severity) {
    ScriptDiagnostic.Severity.ERROR -> ScriptReport.Severity.ERROR
    ScriptDiagnostic.Severity.WARNING -> ScriptReport.Severity.WARNING
    ScriptDiagnostic.Severity.INFO -> ScriptReport.Severity.INFO
    ScriptDiagnostic.Severity.DEBUG -> ScriptReport.Severity.DEBUG
}

fun mapLegacyScriptPosition(pos: ScriptContents.Position?): ScriptSource.Location? =
    pos?.let { ScriptSource.Location(ScriptSource.Position(pos.line, pos.col)) }

fun mapToLegacyScriptReportPosition(pos: ScriptSource.Location?): ScriptReport.Position? =
    pos?.let { ScriptReport.Position(pos.start.line, pos.start.col) }

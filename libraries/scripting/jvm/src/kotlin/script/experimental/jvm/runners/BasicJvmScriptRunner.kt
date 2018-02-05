package kotlin.script.experimental.jvm.runners

import kotlin.script.experimental.api.*

open class BasicJvmScriptRunner<in ScriptBase : Any> : ScriptRunner<ScriptBase> {

    override suspend fun run(compiledScript: CompiledScript<ScriptBase>, scriptEvaluationEnvironment: ScriptEvaluationEnvironment): ResultWithDiagnostics<EvaluationResult> =
        try {
            val obj = compiledScript.instantiate(scriptEvaluationEnvironment)
            when (obj) {
                is ResultWithDiagnostics.Failure -> obj.convert()
                is ResultWithDiagnostics.Success -> {
                    // in the future, when (if) we'll stop to compile everything into constructor
                    // run as SAM
                    // return res
                    val scriptObject = obj.value
                    if (scriptObject !is Class<*>)
                        ResultWithDiagnostics.Failure(ScriptDiagnostic("expecting class in this implementation, got ${scriptObject?.javaClass}"))
                    else {
                        scriptObject.getConstructor().newInstance()

                        ResultWithDiagnostics.Success(EvaluationResult(null, scriptEvaluationEnvironment))
                    }
                }
            }
        }
        catch(e: Exception) {
            ResultWithDiagnostics.Failure(ScriptDiagnostic(e.message ?: "$e"))
        }
}
package org.jetbrains.kotlin.script.examples.jvm.resolve.maven

import kotlin.script.experimental.api.ScriptDefinition
import kotlin.script.experimental.jvm.runners.BasicJvmScriptRunner

@ScriptDefinition(
    "My script with maven dependencies resolving",
    MySelector::class,
    MyConfigurator::class,
    BasicJvmScriptRunner::class
)
abstract class MyScriptWithMavenDeps {
//    abstract fun body(vararg args: String): Int
}

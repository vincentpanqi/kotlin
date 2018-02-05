package org.jetbrains.kotlin.script.examples.jvm.simple

import kotlin.script.experimental.api.ScriptDefinition
import kotlin.script.experimental.jvm.runners.BasicJvmScriptRunner

@ScriptDefinition("My script", MySelector::class, MyConfigurator::class, BasicJvmScriptRunner::class)
abstract class MyScript {
//    abstract fun body(vararg args: String): Int
}

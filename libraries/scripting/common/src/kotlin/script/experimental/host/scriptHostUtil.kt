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

package kotlin.script.experimental.host

import kotlin.script.experimental.api.ScriptSource
import kotlin.script.experimental.api.ScriptSourceFragments
import kotlin.script.experimental.api.ScriptSourceNamedFragment

fun ScriptSourceFragments.isWholeFile(): Boolean = fragments?.isEmpty() ?: true

fun ScriptSource.getScriptText(): String = when {
    text != null -> text!!
    location != null ->
        location!!.openStream().bufferedReader().readText()
    else -> throw RuntimeException("unable to get text from null script")
}

fun ScriptSourceFragments.getMergedScriptText(): String {
    val originalScriptText = originalSource.getScriptText()
    return if (isWholeFile()) {
        originalScriptText
    } else {
        val sb = StringBuilder(originalScriptText.length)
        var prevFragment: ScriptSourceNamedFragment? = null
        for (fragment in fragments!!) {
            val fragmentStartPos = fragment.range.start.absolutePos
            val fragmentEndPos = fragment.range.end.absolutePos
            if (fragmentStartPos == null || fragmentEndPos == null)
                throw RuntimeException("Script fragments require absolute positions (received: $fragment)")
            val curPos = if (prevFragment == null) 0 else prevFragment.range.end.absolutePos!!
            if (prevFragment != null && prevFragment.range.end.absolutePos!! > fragmentStartPos) throw RuntimeException("Unsorted or overlapping fragments: previous: $prevFragment, current: $fragment")
            if (curPos < fragmentStartPos) {
                sb.append(
                    originalScriptText.subSequence(
                        curPos,
                        fragmentStartPos
                    ).map { if (it == '\r' || it == '\n') it else ' ' }) // preserving lines layout
            }
            sb.append(originalScriptText.subSequence(fragmentStartPos, fragmentEndPos))
            prevFragment = fragment
        }
        sb.toString()
    }
}


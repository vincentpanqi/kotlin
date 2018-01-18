/*
 * Copyright 2010-2016 JetBrains s.r.o.
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

package org.jetbrains.kotlin.codegen

import org.jetbrains.kotlin.codegen.binding.CodegenBinding
import org.jetbrains.kotlin.codegen.state.GenerationState
import org.jetbrains.kotlin.psi.KtDeclarationWithBody
import org.jetbrains.kotlin.psi.KtThisExpression
import org.jetbrains.kotlin.resolve.jvm.jvmSignature.JvmMethodSignature

class ClosureGenerationStrategy(
    state: GenerationState,
    declaration: KtDeclarationWithBody
) : FunctionGenerationStrategy.FunctionDefault(state, declaration) {

    override fun doGenerateBody(codegen: ExpressionCodegen, signature: JvmMethodSignature) {
        initializeVariablesForDestructuredLambdaParameters(codegen, codegen.context.functionDescriptor.valueParameters)

        if (codegen.isInlineSuspendLambdaContext) {
            // There are two kinds of `this` in suspend inline lambdas: closure and continuation.
            // 0) closure is used to store captured variables
            // 1) continuation is used to store locals and state machine label
            // Also, there are two kinds of continuation is suspend functions:
            // 0) continuation, which is used to continue execution after suspension
            // 1) completion, which is passed as the last parameter and used to continue execution of the caller suspend function after
            // callee's return.
            // In case of lambdas the continuation is `this`
            // Thus, we need to replace `this` two times to get rid of `ALOAD 0`s, which freak the inliner out.
            // First let closure `this` point to the suspend inline closure and not runtime suspend closure.
            // See CodegenAnnotatingVisitor::visitLambdaExpression for more info.
            // Second, let continuation `this` point to completion.
            // See ExpressionCodegen.invokeMethodWithArguments for continuation replacement.

            // let closure `this` point to the suspend inline closure and not runtime suspend closure.
            val thisExpression = codegen.tempVariables.keys.filterIsInstance<KtThisExpression>().singleOrNull()
            if (thisExpression != null) {
                val oldThis = codegen.tempVariables[thisExpression] ?: error("this is null!")
                val classDescriptor = codegen.bindingContext[CodegenBinding.CLASS_FOR_CALLABLE, codegen.context.functionDescriptor]
                        ?: error("No class descriptor generated for inline suspend closure, check CodegenAnnotatingVisitor::visitLambdaExpression")
                codegen.tempVariables[thisExpression] = StackValue.thisOrOuter(codegen, classDescriptor, false, true)

                super.doGenerateBody(codegen, signature)

                codegen.tempVariables[thisExpression] = oldThis

                return
            }
        }

        super.doGenerateBody(codegen, signature)
    }
}

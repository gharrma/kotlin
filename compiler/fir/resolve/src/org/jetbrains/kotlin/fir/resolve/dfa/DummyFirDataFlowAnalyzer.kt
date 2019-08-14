/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.resolve.dfa

import org.jetbrains.kotlin.fir.declarations.FirNamedFunction
import org.jetbrains.kotlin.fir.expressions.*
import org.jetbrains.kotlin.fir.resolve.dfa.cfg.ControlFlowGraph
import org.jetbrains.kotlin.fir.types.ConeKotlinType

class DummyFirDataFlowAnalyzer : FirDataFlowAnalyzer() {
    override fun getTypeUsingSmartcastInfo(qualifiedAccessExpression: FirQualifiedAccessExpression): ConeKotlinType? {
        return null
    }

    override fun enterNamedFunction(namedFunction: FirNamedFunction) {}

    override fun exitNamedFunction(namedFunction: FirNamedFunction): ControlFlowGraph {
        return ControlFlowGraph()
    }

    override fun enterBlock(block: FirBlock) {}

    override fun exitBlock(block: FirBlock) {}

    override fun exitTypeOperatorCall(typeOperatorCall: FirTypeOperatorCall) {}

    override fun exitJump(jump: FirJump<*>) {}

    override fun enterWhenExpression(whenExpression: FirWhenExpression) {}

    override fun enterWhenBranchCondition(whenBranch: FirWhenBranch) {}

    override fun exitWhenBranchCondition(whenBranch: FirWhenBranch) {}

    override fun exitWhenBranchResult(whenBranch: FirWhenBranch) {}

    override fun exitWhenExpression(whenExpression: FirWhenExpression) {}

    override fun enterWhileLoop(loop: FirLoop) {}

    override fun exitWhileLoopCondition(loop: FirLoop) {}

    override fun exitWhileLoop(loop: FirLoop) {}

    override fun enterDoWhileLoop(loop: FirLoop) {}

    override fun enterDoWhileLoopCondition(loop: FirLoop) {}

    override fun exitDoWhileLoop(loop: FirLoop) {}

    override fun enterTryExpression(tryExpression: FirTryExpression) {}

    override fun exitTryMainBlock(tryExpression: FirTryExpression) {}

    override fun enterCatchClause(catch: FirCatch) {}

    override fun exitCatchClause(catch: FirCatch) {}

    override fun enterFinallyBlock(tryExpression: FirTryExpression) {}

    override fun exitFinallyBlock(tryExpression: FirTryExpression) {}

    override fun exitTryExpression(tryExpression: FirTryExpression) {}

    override fun exitQualifiedAccessExpression(qualifiedAccessExpression: FirQualifiedAccessExpression) {}

    override fun enterFunctionCall(functionCall: FirFunctionCall) {}

    override fun exitFunctionCall(functionCall: FirFunctionCall) {}

    override fun exitConstExpresion(constExpression: FirConstExpression<*>) {}

    override fun exitVariableDeclaration(variable: FirVariable<*>) {}

    override fun exitVariableAssignment(assignment: FirVariableAssignment) {}

    override fun exitThrowExceptionNode(throwExpression: FirThrowExpression) {}
}
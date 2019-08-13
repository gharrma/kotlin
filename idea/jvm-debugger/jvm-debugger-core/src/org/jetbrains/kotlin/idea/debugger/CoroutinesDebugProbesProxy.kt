/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */
package org.jetbrains.kotlin.idea.debugger

import com.intellij.debugger.engine.DebugProcess
import com.intellij.openapi.util.Key
import com.sun.jdi.*
import com.sun.tools.jdi.StringReferenceImpl
import javaslang.control.Either
import org.jetbrains.kotlin.idea.debugger.evaluate.ExecutionContext
import org.jetbrains.kotlin.psi.UserDataProperty

object CoroutinesDebugProbesProxy {
    private const val DEBUG_PACKAGE = "kotlinx.coroutines.debug"
    private var DebugProcess.references by UserDataProperty(Key.create<ProcessReferences>("COROUTINES_DEBUG_REFERENCES"))

    @Suppress("unused")
    fun install(context: ExecutionContext) {
        val debugProbes = context.findClass("$DEBUG_PACKAGE.DebugProbes") as ClassType
        val instance = with(debugProbes) { getValue(fieldByName("INSTANCE")) as ObjectReference }
        val install = debugProbes.concreteMethodByName("install", "()V")
        context.invokeMethod(instance, install, emptyList())
    }

    @Suppress("unused")
    fun uninstall(context: ExecutionContext) {
        val debugProbes = context.findClass("$DEBUG_PACKAGE.DebugProbes") as ClassType
        val instance = with(debugProbes) { getValue(fieldByName("INSTANCE")) as ObjectReference }
        val uninstall = debugProbes.concreteMethodByName("uninstall", "()V")
        context.invokeMethod(instance, uninstall, emptyList())
    }

    /**
     * Invokes DebugProbes from debugged process's classpath and returns states of coroutines
     * Should be invoked on debugger manager thread
     */
    fun dumpCoroutines(context: ExecutionContext): Either<Throwable, List<CoroutineState>> {
        try {
            if (context.debugProcess.references == null) {
                context.debugProcess.references = ProcessReferences(context)
            }
            val refs = context.debugProcess.references!! // already instantiated if it's null

            // get dump
            val infoList = context.invokeMethod(refs.instance, refs.dumpMethod, emptyList()) as ObjectReference
            val size = (context.invokeMethod(infoList, refs.getSize, emptyList()) as IntegerValue).value()

            return Either.right(List(size) {
                val index = context.vm.mirrorOf(it)
                val elem = context.invokeMethod(infoList, refs.getElement, listOf(index)) as ObjectReference
                val name = getName(context, elem, refs)
                val state = getState(context, elem, refs)
                val thread = getLastObservedThread(elem, refs.threadRef)
                CoroutineState(
                    name, state, thread, getStackTrace(elem, refs, context),
                    elem.getValue(refs.continuation) as? ObjectReference
                )
            })
        } catch (e: Throwable) {
            return Either.left(e)
        }
    }

    private fun getName(
        context: ExecutionContext, // Execution context to invoke methods
        info: ObjectReference, // CoroutineInfo instance
        refs: ProcessReferences
    ): String {
        // equals to `coroutineInfo.context.get(CoroutineName).name`
        val coroutineContextInst = context.invokeMethod(info, refs.getContext, emptyList()) as ObjectReference
        val coroutineName = context.invokeMethod(
            coroutineContextInst,
            refs.getContextElement, listOf(refs.nameKey)
        ) as? ObjectReference
        // If the coroutine doesn't have a given name, CoroutineContext.get(CoroutineName) returns null
        val name = if (coroutineName != null) (context.invokeMethod(
            coroutineName,
            refs.getName, emptyList()
        ) as StringReferenceImpl).value() else "coroutine"
        val id = (info.getValue(refs.idField) as LongValue).value()
        return "$name#$id"
    }

    private fun getState(
        context: ExecutionContext, // Execution context to invoke methods
        info: ObjectReference, // CoroutineInfo instance
        refs: ProcessReferences
    ): String {
        //  equals to stringState = coroutineInfo.state.toString()
        val state = context.invokeMethod(info, refs.getState, emptyList()) as ObjectReference
        return (context.invokeMethod(state, refs.toString, emptyList()) as StringReferenceImpl).value()
    }

    private fun getLastObservedThread(
        info: ObjectReference, // CoroutineInfo instance
        threadRef: Field // reference to lastObservedThread
    ): ThreadReference? = info.getValue(threadRef) as ThreadReference?

    /**
     * Returns list of stackTraceElements for the given CoroutineInfo's [ObjectReference]
     */
    private fun getStackTrace(
        info: ObjectReference,
        refs: ProcessReferences,
        context: ExecutionContext
    ): List<StackTraceElement> {
        val frameList = context.invokeMethod(info, refs.lastObservedStackTrace, emptyList()) as ObjectReference
        val mergedFrameList = context.invokeMethod(
            refs.debugProbesImpl,
            refs.enhanceStackTraceWithThreadDump, listOf(info, frameList)
        ) as ObjectReference
        val size = (context.invokeMethod(mergedFrameList, refs.getSize, emptyList()) as IntegerValue).value()

        val list = ArrayList<StackTraceElement>()
        for (it in size - 1 downTo 0) {
            val frame = context.invokeMethod(
                mergedFrameList, refs.getElement,
                listOf(context.vm.virtualMachine.mirrorOf(it))
            ) as ObjectReference
            val clazz = (frame.getValue(refs.className) as StringReference).value()

            if (clazz.contains(DEBUG_PACKAGE)) break // cut off debug intrinsic stacktrace
            list.add(
                0, // add in the beginning
                StackTraceElement(
                    clazz,
                    (frame.getValue(refs.methodName) as StringReference).value(),
                    (frame.getValue(refs.fileName) as StringReference?)?.value(),
                    (frame.getValue(refs.line) as IntegerValue).value()
                )
            )
        }
        return list
    }

    /**
     * Holds ClassTypes, Methods, ObjectReferences and Fields for a particular jvm
     */
    private class ProcessReferences(context: ExecutionContext) {
        // kotlinx.coroutines.debug.DebugProbes instance and methods
        val debugProbes = context.findClass("$DEBUG_PACKAGE.DebugProbes") as ClassType
        val probesImplType = context.findClass("$DEBUG_PACKAGE.internal.DebugProbesImpl") as ClassType
        val debugProbesImpl = with(probesImplType) { getValue(fieldByName("INSTANCE")) as ObjectReference }
        val enhanceStackTraceWithThreadDump: Method = probesImplType
            .methodsByName("enhanceStackTraceWithThreadDump").single()

        val dumpMethod: Method = debugProbes.concreteMethodByName("dumpCoroutinesInfo", "()Ljava/util/List;")
        val instance = with(debugProbes) { getValue(fieldByName("INSTANCE")) as ObjectReference }

        // CoroutineInfo
        val info = context.findClass("$DEBUG_PACKAGE.CoroutineInfo") as ClassType
        val getState: Method = info.concreteMethodByName("getState", "()Lkotlinx/coroutines/debug/State;")
        val getContext: Method = info.concreteMethodByName("getContext", "()Lkotlin/coroutines/CoroutineContext;")
        val idField: Field = info.fieldByName("sequenceNumber")
        val lastObservedStackTrace: Method = info.methodsByName("lastObservedStackTrace").single()
        val coroutineContext = context.findClass("kotlin.coroutines.CoroutineContext") as InterfaceType
        val getContextElement: Method = coroutineContext.methodsByName("get").single()
        val coroutineName = context.findClass("kotlinx.coroutines.CoroutineName") as ClassType
        val getName: Method = coroutineName.methodsByName("getName").single()
        val nameKey = coroutineName.getValue(coroutineName.fieldByName("Key")) as ObjectReference
        val toString: Method = (context.findClass("java.lang.Object") as ClassType)
            .methodsByName("toString").single()

        val threadRef: Field = info.fieldByName("lastObservedThread")
        val continuation: Field = info.fieldByName("lastObservedFrame")

        // Methods for list
        val listType = context.findClass("java.util.List") as InterfaceType
        val getSize: Method = listType.methodsByName("size").single()
        val getElement: Method = listType.methodsByName("get").single()
        val element = context.findClass("java.lang.StackTraceElement") as ClassType

        // for StackTraceElement
        val methodName: Field = element.fieldByName("methodName")
        val className: Field = element.fieldByName("declaringClass")
        val fileName: Field = element.fieldByName("fileName")
        val line: Field = element.fieldByName("lineNumber")
    }
}
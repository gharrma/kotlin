/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.backend.common.serialization

import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.descriptors.IrBuiltIns

class DescriptorTable {
    private val descriptors = mutableMapOf<DeclarationDescriptor, Long>()
    fun put(descriptor: DeclarationDescriptor, uniqId: UniqId) {
        descriptors.getOrPut(descriptor) { uniqId.index }
    }
    fun get(descriptor: DeclarationDescriptor) = descriptors[descriptor]
}

// TODO: We don't manage id clashes anyhow now.
abstract class GlobalDeclarationTable(private val mangler: KotlinMangler) {
    private val table = mutableMapOf<IrDeclaration, UniqId>()

    protected open fun loadKnownBuiltins(builtIns: IrBuiltIns, startIndex: Long): Long {
        var index = startIndex
        builtIns.knownBuiltins.forEach {
            table[it.owner] = UniqId(index++, false)
        }
        return index
    }

    open fun computeUniqIdByDeclaration(declaration: IrDeclaration): UniqId {
        return table.getOrPut(declaration) {
            UniqId(mangler.hashedMangleImpl(declaration), false)
        }
    }

    fun isExportedDeclaration(declaration: IrDeclaration): Boolean = mangler.isExportedImpl(declaration)
}

class DeclarationTable(
    private val descriptorTable: DescriptorTable,
    private val globalDeclarationTable: GlobalDeclarationTable,
    startIndex: Long
) {
    private val table = mutableMapOf<IrDeclaration, UniqId>()

    private fun IrDeclaration.isLocalDeclaration(): Boolean {
        return origin == IrDeclarationOrigin.FAKE_OVERRIDE || !isExportedDeclaration(this) || this is IrVariable || this is IrValueParameter || this is IrAnonymousInitializer || this is IrLocalDelegatedProperty
    }

    private var localIndex = startIndex

    fun isExportedDeclaration(declaration: IrDeclaration) = globalDeclarationTable.isExportedDeclaration(declaration)

    private fun computeUniqIdByDeclaration(declaration: IrDeclaration): UniqId {
        return if (declaration.isLocalDeclaration()) {
            table.getOrPut(declaration) { UniqId(localIndex++, true) }
        } else globalDeclarationTable.computeUniqIdByDeclaration(declaration)
    }

    fun uniqIdByDeclaration(declaration: IrDeclaration): UniqId {
        val uniqId = computeUniqIdByDeclaration(declaration)
        descriptorTable.put(declaration.descriptor, uniqId)
        return uniqId
    }
}

// This is what we pre-populate tables with
val IrBuiltIns.knownBuiltins
    get() = irBuiltInsSymbols

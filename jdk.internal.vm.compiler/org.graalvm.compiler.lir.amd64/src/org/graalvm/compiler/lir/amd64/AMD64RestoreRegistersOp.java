/*
 * Copyright (c) 2013, 2018, Oracle and/or its affiliates. All rights reserved.
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 */


package org.graalvm.compiler.lir.amd64;

import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.STACK;
import static jdk.vm.ci.code.ValueUtil.asStackSlot;
import static jdk.vm.ci.code.ValueUtil.isStackSlot;

import java.util.Arrays;

import org.graalvm.compiler.asm.amd64.AMD64MacroAssembler;
import org.graalvm.compiler.lir.LIRInstructionClass;
import org.graalvm.compiler.lir.LIRValueUtil;
import org.graalvm.compiler.lir.Opcode;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;

import jdk.vm.ci.amd64.AMD64Kind;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.StackSlot;
import jdk.vm.ci.meta.AllocatableValue;

/**
 * Restores registers from stack slots.
 */
@Opcode("RESTORE_REGISTER")
public class AMD64RestoreRegistersOp extends AMD64LIRInstruction {
    public static final LIRInstructionClass<AMD64RestoreRegistersOp> TYPE = LIRInstructionClass.create(AMD64RestoreRegistersOp.class);

    /**
     * The slots from which the registers are restored.
     */
    @Use(STACK) protected final AllocatableValue[] slots;

    /**
     * The operation that saved the registers restored by this operation.
     */
    private final AMD64SaveRegistersOp save;

    public AMD64RestoreRegistersOp(AllocatableValue[] values, AMD64SaveRegistersOp save) {
        this(TYPE, values, save);
    }

    protected AMD64RestoreRegistersOp(LIRInstructionClass<? extends AMD64RestoreRegistersOp> c, AllocatableValue[] values, AMD64SaveRegistersOp save) {
        super(c);
        assert Arrays.asList(values).stream().allMatch(LIRValueUtil::isVirtualStackSlot);
        this.slots = values;
        this.save = save;
    }

    protected Register[] getSavedRegisters() {
        return save.savedRegisters;
    }

    protected void restoreRegister(CompilationResultBuilder crb, AMD64MacroAssembler masm, Register result, StackSlot input) {
        AMD64Move.stack2reg((AMD64Kind) input.getPlatformKind(), crb, masm, result, input);
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
        Register[] savedRegisters = getSavedRegisters();
        for (int i = 0; i < savedRegisters.length; i++) {
            if (savedRegisters[i] != null) {
                assert isStackSlot(slots[i]) : "not a StackSlot: " + slots[i];
                restoreRegister(crb, masm, savedRegisters[i], asStackSlot(slots[i]));
            }
        }
    }
}

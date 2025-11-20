package org.example;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import static org.objectweb.asm.Opcodes.*;

public class Abc {
    public int a;
    public int b;
    public int c;

    public Abc(int a, int b, int c) {
        this.a = a;
        this.b = b;
        this.c = c;
    }

    public Abc add(Abc o) {
        return new Abc(a + o.a, b + o.b, c + o.c);
    }

    static Abc countAbc(MethodNode m) {
        int a = 0, b = 0, cCnt = 0;
        for (AbstractInsnNode insn = m.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            int op = insn.getOpcode();
            if (op < 0) continue;

            if (op == Opcodes.ISTORE || op == LSTORE || op == Opcodes.FSTORE
                    || op == Opcodes.DSTORE || op == Opcodes.ASTORE
                    || op == Opcodes.IINC) {
                a++;
            }

            if (insn instanceof JumpInsnNode || insn instanceof TableSwitchInsnNode
                    || insn instanceof LookupSwitchInsnNode || op == Opcodes.ATHROW) {
                b++;
            }

            if (isIfOpcode(op) || insn instanceof TableSwitchInsnNode || insn instanceof LookupSwitchInsnNode) {
                cCnt++;
            }
        }
        return new Abc(a, b, cCnt);
    }

    static boolean isIfOpcode(int op) {
        return (op >= Opcodes.IFEQ && op <= Opcodes.IF_ACMPNE)
                || op == Opcodes.IFNULL || op == Opcodes.IFNONNULL;
    }
}

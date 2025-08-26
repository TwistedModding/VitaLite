package com.tonic.injector.util.expreditor.impls;

import com.tonic.injector.util.expreditor.ExprEditor;
import com.tonic.injector.util.expreditor.MethodCall;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.LdcInsnNode;

public class RuntimeMaxMemoryReplacer extends ExprEditor {
    private final long hardcodedValue;
    private AbstractInsnNode pendingRuntimeCall = null;

    public RuntimeMaxMemoryReplacer(long value) {
        this.hardcodedValue = value;
    }

    @Override
    public void edit(MethodCall call) {
        if (isRuntimeGetRuntime(call)) {
            pendingRuntimeCall = call.getInstruction();
        } else if (isMaxMemoryCall(call) && pendingRuntimeCall != null) {
            if (isDirectlyAfter(pendingRuntimeCall, call.getInstruction())) {
                replaceRuntimeMaxMemoryPattern(call);
            }
            pendingRuntimeCall = null;
        }
    }

    private boolean isRuntimeGetRuntime(MethodCall call) {
        return call.getMethodOwner().equals("java/lang/Runtime") &&
                call.getMethodName().equals("getRuntime") &&
                !call.returnsVoid();
    }

    private boolean isMaxMemoryCall(MethodCall call) {
        return call.getMethodOwner().equals("java/lang/Runtime") &&
                call.getMethodName().equals("maxMemory") &&
                call.getReturnType().equals(Type.LONG_TYPE);
    }

    private void replaceRuntimeMaxMemoryPattern(MethodCall maxMemoryCall) {
        InsnList replacement = new InsnList();
        replacement.add(new LdcInsnNode(hardcodedValue));
        maxMemoryCall.getMethod().instructions.remove(pendingRuntimeCall);
        maxMemoryCall.replace(replacement);
        //String className = maxMemoryCall.getClassNode().name.replace('/', '.');
        //String methodName = maxMemoryCall.getMethod().name + maxMemoryCall.getMethod().desc;
        //System.out.printf("Replaced Runtime.getRuntime().maxMemory() with %d bytes in %s.%s%n", hardcodedValue, className, methodName);
    }

    private boolean isDirectlyAfter(AbstractInsnNode first, AbstractInsnNode second) {
        AbstractInsnNode current = first.getNext();
        while (current != null && current != second) {
            if (current.getType() == AbstractInsnNode.LABEL ||
                    current.getType() == AbstractInsnNode.LINE ||
                    current.getType() == AbstractInsnNode.FRAME) {
                current = current.getNext();
            } else {
                return false;
            }
        }
        return current == second;
    }
}

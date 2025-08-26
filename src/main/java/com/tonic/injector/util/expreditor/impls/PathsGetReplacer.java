package com.tonic.injector.util.expreditor.impls;

import com.tonic.injector.util.expreditor.ExprEditor;
import com.tonic.injector.util.expreditor.MethodCall;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import java.util.ArrayList;
import java.util.List;

public class PathsGetReplacer extends ExprEditor {
    private MethodNode lastProcessedMethod = null;
    private boolean replacedInCurrentMethod = false;
    
    @Override
    public void edit(MethodCall call) {
        if (lastProcessedMethod != call.getMethod()) {
            lastProcessedMethod = call.getMethod();
            replacedInCurrentMethod = false;
        }

        if (!replacedInCurrentMethod) {
            if (call.getMethodOwner().equals("java/nio/file/Path") && 
                call.getMethodName().equals("toString") && 
                call.getMethodDesc().equals("()Ljava/lang/String;")) {
                if (isPathsGetChain(call)) {
                    replacePathsGetChain(call);
                    replacedInCurrentMethod = true;
                }
            }
        }
    }
    
    private boolean isPathsGetChain(MethodCall toStringCall) {
        List<MethodInsnNode> methodChain = getMethodChain(toStringCall);

        if (methodChain.size() < 3) {
            return false;
        }

        MethodInsnNode toString = methodChain.get(0);
        MethodInsnNode getFileName = methodChain.get(1);
        MethodInsnNode pathsGet = null;

        for (int i = 2; i < methodChain.size(); i++) {
            MethodInsnNode method = methodChain.get(i);
            if (method.owner.equals("java/nio/file/Paths") && 
                method.name.equals("get") &&
                method.desc.startsWith("(Ljava/lang/String;")) {
                pathsGet = method;
                break;
            }
        }
        
        return toString.owner.equals("java/nio/file/Path") &&
               toString.name.equals("toString") &&
               getFileName.owner.equals("java/nio/file/Path") &&
               getFileName.name.equals("getFileName") &&
               pathsGet != null;
    }
    
    private List<MethodInsnNode> getMethodChain(MethodCall startCall) {
        List<MethodInsnNode> chain = new ArrayList<>();
        AbstractInsnNode current = startCall.getInstruction();

        while (current != null && chain.size() < 10) {
            if (current instanceof MethodInsnNode) {
                chain.add((MethodInsnNode) current);
            }
            current = current.getPrevious();

            while (current != null && 
                   (current.getType() == AbstractInsnNode.LABEL ||
                    current.getType() == AbstractInsnNode.LINE ||
                    current.getType() == AbstractInsnNode.FRAME)) {
                current = current.getPrevious();
            }
        }
        
        return chain;
    }
    
    private void replacePathsGetChain(MethodCall toStringCall) {
        InsnList stackManipulation = new InsnList();
        stackManipulation.add(new InsnNode(Opcodes.POP));
        stackManipulation.add(new LdcInsnNode("RuneLite.exe"));
        toStringCall.insertAfter(stackManipulation);
    }
}
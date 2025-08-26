package com.tonic.injector.util.expreditor.impls;

import com.tonic.injector.util.expreditor.ExprEditor;
import com.tonic.injector.util.expreditor.MethodCall;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.util.ArrayList;
import java.util.List;

public class PathsGetReplacer extends ExprEditor {
    private MethodCall currentCall; // Store reference for helper methods
    private MethodNode lastProcessedMethod = null; // Track which method we're in
    private boolean replacedInCurrentMethod = false; // Track if we've already replaced in this method
    
    @Override
    public void edit(MethodCall call) {
        this.currentCall = call; // Store reference
        
        // Check if we're in a new method
        if (lastProcessedMethod != call.getMethod()) {
            lastProcessedMethod = call.getMethod();
            replacedInCurrentMethod = false; // Reset flag for new method
        }
        
        // Only process if we haven't already replaced in this method
        if (!replacedInCurrentMethod) {
            // Look for the final toString() call in the chain
            if (call.getMethodOwner().equals("java/nio/file/Path") && 
                call.getMethodName().equals("toString") && 
                call.getMethodDesc().equals("()Ljava/lang/String;")) {
                
                // Check if this is part of the Paths.get chain
                if (isPathsGetChain(call)) {
                    replacePathsGetChain(call);
                    replacedInCurrentMethod = true; // Mark that we've replaced in this method
                }
            }
        }
    }
    
    private boolean isPathsGetChain(MethodCall toStringCall) {
        List<MethodInsnNode> methodChain = getMethodChain(toStringCall);
        
        // We expect at least 3 method calls in the chain:
        // 1. Paths.get()
        // 2. getFileName() 
        // 3. toString()
        if (methodChain.size() < 3) {
            return false;
        }
        
        // Check if the chain matches our expected pattern
        // Working backwards from toString()
        MethodInsnNode toString = methodChain.get(0);
        MethodInsnNode getFileName = methodChain.get(1);
        MethodInsnNode pathsGet = null;
        
        // Find Paths.get() in the chain
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
        
        // Walk backwards collecting method calls
        while (current != null && chain.size() < 10) { // Limit to prevent infinite loops
            if (current instanceof MethodInsnNode) {
                chain.add((MethodInsnNode) current);
            }
            current = current.getPrevious();
            
            // Skip labels, line numbers, frames
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
        // Stack-safe approach: let the method execute, then pop result and push our string
        InsnList stackManipulation = new InsnList();
        stackManipulation.add(new InsnNode(Opcodes.POP));  // Pop the actual result off stack
        stackManipulation.add(new LdcInsnNode("RuneLite.exe")); // Push our hardcoded string
        
        toStringCall.insertAfter(stackManipulation);
        
        String className = toStringCall.getClassNode().name.replace('/', '.');
        String methodName = toStringCall.getMethod().name + toStringCall.getMethod().desc;
        System.out.printf("Replaced Paths.get().getFileName().toString() result with \"RuneLite.exe\" in %s.%s%n", 
            className, methodName);
    }
    
}
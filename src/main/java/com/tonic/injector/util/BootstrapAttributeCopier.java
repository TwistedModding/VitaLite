package com.tonic.injector.util;

import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import java.util.*;

public class BootstrapAttributeCopier {

    /**
     * Updates all invokedynamic callsites in ClassNode A to use B's class name,
     * and also copies these updated callsites to corresponding methods in ClassNode B.
     * This modifies nodeA in-place so it's ready to be copied by an injector.
     * @param nodeA Source ClassNode (will be modified in-place)
     * @param nodeB Target ClassNode (will receive copied callsites)
     */
    public static void copyBootstrapAttributesAndCallsites(ClassNode nodeA, ClassNode nodeB) {
        if (nodeA.methods == null) return;

        // Update all invokedynamic instructions in nodeA in-place
        for (MethodNode methodA : nodeA.methods) {
            if (methodA.instructions == null) continue;

            List<InvokeDynamicInsnNode> indysToUpdate = new ArrayList<>();

            // Collect all invokedynamic instructions
            for (AbstractInsnNode insn : methodA.instructions) {
                if (insn instanceof InvokeDynamicInsnNode) {
                    indysToUpdate.add((InvokeDynamicInsnNode) insn);
                }
            }

            // Update each invokedynamic instruction in-place
            for (InvokeDynamicInsnNode dynInsn : indysToUpdate) {
                updateInvokeDynamicInPlace(dynInsn, nodeA.name, nodeB.name);
            }

            // Also copy to corresponding method in nodeB if it exists
            if (nodeB.methods != null) {
                MethodNode methodB = findMethod(nodeB, methodA.name, methodA.desc);
                if (methodB != null && !indysToUpdate.isEmpty()) {
                    if (methodB.instructions == null) {
                        methodB.instructions = new InsnList();
                    }

                    // Add copies of the updated invokedynamic instructions
                    for (InvokeDynamicInsnNode dynInsn : indysToUpdate) {
                        // Create a copy (already updated)
                        InvokeDynamicInsnNode copy = new InvokeDynamicInsnNode(
                                dynInsn.name,
                                dynInsn.desc,
                                dynInsn.bsm,
                                dynInsn.bsmArgs
                        );
                        methodB.instructions.add(copy);
                    }
                }
            }
        }

        // Also update all other instruction types in nodeA that might reference the class
        updateAllInstructionsInPlace(nodeA, nodeA.name, nodeB.name);
    }

    /**
     * Updates an InvokeDynamicInsnNode in-place to use the new class name
     */
    private static void updateInvokeDynamicInPlace(InvokeDynamicInsnNode dynInsn,
                                                   String oldClassName, String newClassName) {
        // Update the bootstrap method handle
        dynInsn.bsm = translateHandle(dynInsn.bsm, oldClassName, newClassName);

        // Update the descriptor
        dynInsn.desc = translateDescriptor(dynInsn.desc, oldClassName, newClassName);

        // Update bootstrap arguments
        dynInsn.bsmArgs = translateBootstrapArguments(dynInsn.bsmArgs, oldClassName, newClassName);
    }

    /**
     * Updates all instructions in a ClassNode in-place
     */
    private static void updateAllInstructionsInPlace(ClassNode node, String oldClassName, String newClassName) {
        if (node.methods == null) return;

        for (MethodNode method : node.methods) {
            if (method.instructions == null) continue;

            // Update method descriptor and signature
            method.desc = translateDescriptor(method.desc, oldClassName, newClassName);
            if (method.signature != null) {
                method.signature = translateSignature(method.signature, oldClassName, newClassName);
            }

            // Update exceptions
            if (method.exceptions != null) {
                for (int i = 0; i < method.exceptions.size(); i++) {
                    String exception = method.exceptions.get(i);
                    if (exception.equals(oldClassName)) {
                        method.exceptions.set(i, newClassName);
                    }
                }
            }

            // Update all instructions
            for (AbstractInsnNode insn : method.instructions) {
                updateInstructionInPlace(insn, oldClassName, newClassName);
            }

            // Update try-catch blocks
            if (method.tryCatchBlocks != null) {
                for (TryCatchBlockNode tcb : method.tryCatchBlocks) {
                    if (tcb.type != null && tcb.type.equals(oldClassName)) {
                        tcb.type = newClassName;
                    }
                }
            }

            // Update local variables
            if (method.localVariables != null) {
                for (LocalVariableNode lvn : method.localVariables) {
                    lvn.desc = translateDescriptor(lvn.desc, oldClassName, newClassName);
                    if (lvn.signature != null) {
                        lvn.signature = translateSignature(lvn.signature, oldClassName, newClassName);
                    }
                }
            }

            // Update annotations
            updateAnnotations(method.visibleAnnotations, oldClassName, newClassName);
            updateAnnotations(method.invisibleAnnotations, oldClassName, newClassName);
        }

        // Update field descriptors and signatures
        if (node.fields != null) {
            for (FieldNode field : node.fields) {
                field.desc = translateDescriptor(field.desc, oldClassName, newClassName);
                if (field.signature != null) {
                    field.signature = translateSignature(field.signature, oldClassName, newClassName);
                }
                updateAnnotations(field.visibleAnnotations, oldClassName, newClassName);
                updateAnnotations(field.invisibleAnnotations, oldClassName, newClassName);
            }
        }

        // Update class-level items
        node.superName = node.superName.equals(oldClassName) ? newClassName : node.superName;
        if (node.interfaces != null) {
            for (int i = 0; i < node.interfaces.size(); i++) {
                if (node.interfaces.get(i).equals(oldClassName)) {
                    node.interfaces.set(i, newClassName);
                }
            }
        }

        if (node.signature != null) {
            node.signature = translateSignature(node.signature, oldClassName, newClassName);
        }

        // Update class annotations
        updateAnnotations(node.visibleAnnotations, oldClassName, newClassName);
        updateAnnotations(node.invisibleAnnotations, oldClassName, newClassName);
    }

    /**
     * Updates a single instruction in-place
     */
    private static void updateInstructionInPlace(AbstractInsnNode insn, String oldClassName, String newClassName) {
        if (insn instanceof MethodInsnNode) {
            MethodInsnNode minsn = (MethodInsnNode) insn;
            if (minsn.owner.equals(oldClassName)) {
                minsn.owner = newClassName;
            }
            minsn.desc = translateDescriptor(minsn.desc, oldClassName, newClassName);

        } else if (insn instanceof FieldInsnNode) {
            FieldInsnNode finsn = (FieldInsnNode) insn;
            if (finsn.owner.equals(oldClassName)) {
                finsn.owner = newClassName;
            }
            finsn.desc = translateDescriptor(finsn.desc, oldClassName, newClassName);

        } else if (insn instanceof TypeInsnNode) {
            TypeInsnNode tinsn = (TypeInsnNode) insn;
            if (tinsn.desc.equals(oldClassName)) {
                tinsn.desc = newClassName;
            }

        } else if (insn instanceof LdcInsnNode) {
            LdcInsnNode ldc = (LdcInsnNode) insn;
            Object cst = ldc.cst;

            if (cst instanceof Type) {
                Type type = (Type) cst;
                String desc = translateDescriptor(type.getDescriptor(), oldClassName, newClassName);
                ldc.cst = Type.getType(desc);
            } else if (cst instanceof Handle) {
                ldc.cst = translateHandle((Handle) cst, oldClassName, newClassName);
            } else if (cst instanceof String) {
                String str = (String) cst;
                if (str.equals(oldClassName.replace('/', '.'))) {
                    ldc.cst = newClassName.replace('/', '.');
                }
            }

        } else if (insn instanceof MultiANewArrayInsnNode) {
            MultiANewArrayInsnNode mainsn = (MultiANewArrayInsnNode) insn;
            mainsn.desc = translateDescriptor(mainsn.desc, oldClassName, newClassName);
        }
    }

    /**
     * Updates a list of annotations in-place
     */
    private static void updateAnnotations(List<AnnotationNode> annotations, String oldClassName, String newClassName) {
        if (annotations == null) return;

        for (AnnotationNode ann : annotations) {
            ann.desc = translateDescriptor(ann.desc, oldClassName, newClassName);

            if (ann.values != null) {
                for (int i = 1; i < ann.values.size(); i += 2) {
                    Object value = ann.values.get(i);
                    ann.values.set(i, translateAnnotationValue(value, oldClassName, newClassName));
                }
            }
        }
    }

    /**
     * Translates an annotation value
     */
    private static Object translateAnnotationValue(Object value, String oldClassName, String newClassName) {
        if (value instanceof Type) {
            Type type = (Type) value;
            return Type.getType(translateDescriptor(type.getDescriptor(), oldClassName, newClassName));
        } else if (value instanceof AnnotationNode) {
            AnnotationNode ann = (AnnotationNode) value;
            ann.desc = translateDescriptor(ann.desc, oldClassName, newClassName);
            if (ann.values != null) {
                for (int i = 1; i < ann.values.size(); i += 2) {
                    ann.values.set(i, translateAnnotationValue(ann.values.get(i), oldClassName, newClassName));
                }
            }
            return ann;
        } else if (value instanceof List) {
            List<?> list = (List<?>) value;
            List<Object> translated = new ArrayList<>();
            for (Object item : list) {
                translated.add(translateAnnotationValue(item, oldClassName, newClassName));
            }
            return translated;
        } else if (value instanceof String) {
            String str = (String) value;
            if (str.equals(oldClassName.replace('/', '.'))) {
                return newClassName.replace('/', '.');
            }
        }
        return value;
    }

    /**
     * Translates a Handle by replacing class references
     */
    private static Handle translateHandle(Handle handle, String oldClassName, String newClassName) {
        if (handle == null) return null;

        String owner = handle.getOwner();
        String name = handle.getName();
        String desc = handle.getDesc();

        // Replace class name in owner if it matches
        if (owner.equals(oldClassName)) {
            owner = newClassName;
        }

        // Replace class names in descriptor
        desc = translateDescriptor(desc, oldClassName, newClassName);

        return new Handle(
                handle.getTag(),
                owner,
                name,
                desc,
                handle.isInterface()
        );
    }

    /**
     * Translates class names in a method/field descriptor
     */
    private static String translateDescriptor(String descriptor, String oldClassName, String newClassName) {
        if (descriptor == null) return null;

        // Convert internal names (with /) to descriptor format (with L and ;)
        String oldDescriptor = "L" + oldClassName + ";";
        String newDescriptor = "L" + newClassName + ";";

        return descriptor.replace(oldDescriptor, newDescriptor);
    }

    /**
     * Translates a signature (generic signature)
     */
    private static String translateSignature(String signature, String oldClassName, String newClassName) {
        if (signature == null) return null;

        // Replace both descriptor format and binary name format
        signature = signature.replace("L" + oldClassName + ";", "L" + newClassName + ";");
        signature = signature.replace(oldClassName.replace('/', '.'), newClassName.replace('/', '.'));

        return signature;
    }

    /**
     * Translates bootstrap method arguments
     */
    private static Object[] translateBootstrapArguments(Object[] args, String oldClassName, String newClassName) {
        if (args == null) return null;

        Object[] translated = new Object[args.length];
        for (int i = 0; i < args.length; i++) {
            Object arg = args[i];

            if (arg instanceof Type) {
                Type type = (Type) arg;
                String desc = type.getDescriptor();
                String translatedDesc = translateDescriptor(desc, oldClassName, newClassName);
                translated[i] = Type.getType(translatedDesc);
            } else if (arg instanceof Handle) {
                translated[i] = translateHandle((Handle) arg, oldClassName, newClassName);
            } else if (arg instanceof String) {
                String str = (String) arg;
                if (str.equals(oldClassName)) {
                    translated[i] = newClassName;
                } else if (str.equals(oldClassName.replace('/', '.'))) {
                    translated[i] = newClassName.replace('/', '.');
                } else {
                    translated[i] = str;
                }
            } else {
                // For Integer, Float, Long, Double, etc., keep as-is
                translated[i] = arg;
            }
        }
        return translated;
    }

    /**
     * Finds a method in a ClassNode by name and descriptor
     */
    private static MethodNode findMethod(ClassNode node, String name, String desc) {
        if (node.methods == null) return null;

        for (MethodNode method : node.methods) {
            if (method.name.equals(name) && method.desc.equals(desc)) {
                return method;
            }
        }
        return null;
    }
}
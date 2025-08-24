package com.tonic.injector.pipeline;

import com.tonic.dto.JClass;
import com.tonic.dto.JMethod;
import com.tonic.injector.Injector;
import com.tonic.injector.MappingProvider;
import com.tonic.injector.annotations.At;
import com.tonic.injector.annotations.AtTarget;
import com.tonic.injector.annotations.Insert;
import com.tonic.injector.annotations.Mixin;
import com.tonic.injector.annotations.Shift;
import com.tonic.injector.types.InstructionMatcher;
import com.tonic.injector.util.AnnotationUtil;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Transformer that injects method calls after specific instruction patterns.
 * 
 * This transformer:
 * 1. Injects the mixin method into the target class
 * 2. Finds all instructions matching the specified pattern
 * 3. Inserts calls to the injected method after each match (or specific ordinal)
 */
public class InsertTransformer {
    
    /**
     * Process an @Insert annotated method from a mixin.
     *
     * @param mixin  The mixin class containing the insert method
     * @param method The method annotated with @Insert
     */
    public static void patch(ClassNode mixin, MethodNode method) {
        try {
            String gamepackName = AnnotationUtil.getAnnotation(mixin, Mixin.class, "value");
            String targetMethodName = AnnotationUtil.getAnnotation(method, Insert.class, "method");
            
            JClass jClass = MappingProvider.getClass(gamepackName);
            if (jClass == null) {
                System.err.println("Could not find class mapping for: " + gamepackName);
                return;
            }
            
            JMethod jMethod = MappingProvider.getMethod(jClass, targetMethodName);
            if (jMethod == null) {
                System.err.println("Could not find method mapping for: " + gamepackName + "." + targetMethodName);
                return;
            }
            
            ClassNode gamepackClass = Injector.gamepack.get(jMethod.getOwnerObfuscatedName());
            if (gamepackClass == null) {
                System.err.println("Could not find gamepack class: " + jMethod.getOwnerObfuscatedName());
                return;
            }
            
            // Inject the mixin method into the gamepack class
            InjectTransformer.patch(gamepackClass, mixin, method);
            
            // Find the target method to insert into
            MethodNode targetMethod = gamepackClass.methods.stream()
                    .filter(m -> m.name.equals(jMethod.getObfuscatedName()) && m.desc.equals(jMethod.getDescriptor()))
                    .findFirst()
                    .orElse(null);
                    
            if (targetMethod == null) {
                System.err.println("Could not find target method: " + jMethod.getObfuscatedName() + jMethod.getDescriptor());
                return;
            }
            
            // Get insertion configuration from annotation
            Insert insertAnnotation = getInsertAnnotation(method);
            if (insertAnnotation == null) {
                System.err.println("No @Insert annotation found on method: " + method.name);
                return;
            }
            
            // Find matching instructions with mapping context
            List<InstructionMatcher.MatchResult> matches = InstructionMatcher.findMatches(
                    targetMethod, 
                    insertAnnotation.at(), 
                    insertAnnotation.slice(),
                    jClass // Pass the mapping context for name resolution
            );

            // Filter matches to current class context if target is a bare field name
            matches = filterMatchesByContext(matches, insertAnnotation.at(), gamepackClass);
            
            if (matches.isEmpty()) {
                System.err.println("No matching instructions found for pattern in method: " + targetMethod.name);
                return;
            }

            // Determine which matches to inject at
            List<InstructionMatcher.MatchResult> targetMatches;
            if (insertAnnotation.all()) {
                targetMatches = matches;
            } else {
                int ordinal = insertAnnotation.ordinal();
                if (ordinal >= 0 && ordinal < matches.size()) {
                    targetMatches = List.of(matches.get(ordinal));
                } else {
                    return;
                }
            }
            
            // Insert calls at each target location (reverse order to maintain indices)
            for (int i = targetMatches.size() - 1; i >= 0; i--) {
                InstructionMatcher.MatchResult match = targetMatches.get(i);
                insertMethodCall(targetMethod, match.instruction, gamepackClass, method, insertAnnotation.at());
            }
            
            System.out.println("Successfully inserted " + targetMatches.size() + " call(s) to " + method.name + " in " + targetMethod.name);
            
        } catch (Exception e) {
            System.err.println("Error processing @Insert on method " + method.name + ": " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static Insert getInsertAnnotation(MethodNode method) {
        if (method.visibleAnnotations == null) return null;
        
        for (AnnotationNode annotation : method.visibleAnnotations) {
            if (annotation.desc.equals("L" + Insert.class.getName().replace('.', '/') + ";")) {
                return AnnotationUtil.createAnnotationProxy(Insert.class, annotation);
            }
        }
        return null;
    }
    
    private static void insertMethodCall(MethodNode targetMethod, AbstractInsnNode insertPoint, 
                                       ClassNode gamepackClass, MethodNode injectedMethod, At pattern) {
        
        InsnList callInstructions = new InsnList();
        
        boolean isInjectedStatic = (injectedMethod.access & Opcodes.ACC_STATIC) != 0;
        boolean isTargetStatic = (targetMethod.access & Opcodes.ACC_STATIC) != 0;
        
        Type injectedMethodType = Type.getMethodType(injectedMethod.desc);
        Type[] injectedParams = injectedMethodType.getArgumentTypes();
        
        // Load 'this' reference if injected method is not static
        if (!isInjectedStatic) {
            if (isTargetStatic) {
                System.err.println("Cannot call non-static injected method from static target method");
                return;
            }
            callInstructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        }
        
        // Load parameters for the injected method
        // For now, we assume the injected method takes no parameters beyond 'this'
        // This could be extended to support parameter injection based on local variables or stack state
        
        // Generate the method call
        int invokeOpcode = isInjectedStatic ? Opcodes.INVOKESTATIC : Opcodes.INVOKEVIRTUAL;
        callInstructions.add(new MethodInsnNode(
                invokeOpcode,
                gamepackClass.name,
                injectedMethod.name,
                injectedMethod.desc,
                false
        ));
        
        // Handle return value if present
        Type returnType = injectedMethodType.getReturnType();
        if (returnType.getSort() != Type.VOID) {
            // Pop the return value since we're just inserting, not replacing
            if (returnType.getSize() == 2) {
                callInstructions.add(new InsnNode(Opcodes.POP2));
            } else {
                callInstructions.add(new InsnNode(Opcodes.POP));
            }
        }
        
        // Insert the call at the appropriate location
        Shift shiftType = getShiftType(pattern);
        if (shiftType == Shift.HEAD) {
            // Insert before the matched instruction
            targetMethod.instructions.insertBefore(insertPoint, callInstructions);
        } else {
            // Insert after the matched instruction (default)
            targetMethod.instructions.insert(insertPoint, callInstructions);
        }
    }
    
    /**
     * Get the shift type, supporting both enum and legacy string values.
     */
    private static Shift getShiftType(At pattern) {
        // If legacy string value is provided, use it for backwards compatibility
        if (!pattern.shiftString().isEmpty()) {
            try {
                return Shift.valueOf(pattern.shiftString().toUpperCase());
            } catch (IllegalArgumentException e) {
                System.err.println("Invalid shift type: " + pattern.shiftString());
                return Shift.TAIL; // Default fallback
            }
        }
        
        // Handle the case where proxy returns String instead of enum (due to annotation parsing)
        try {
            Object shiftObj = pattern.shift();
            if (shiftObj instanceof String) {
                return Shift.valueOf(((String) shiftObj).toUpperCase());
            } else if (shiftObj instanceof Shift) {
                return (Shift) shiftObj;
            }
        } catch (Exception e) {
            System.err.println("Error getting shift type: " + e.getMessage());
        }
        
        // Default fallback
        return Shift.TAIL;
    }
    
    /**
     * Get the target type as string, supporting both enum and legacy string values.
     */
    private static String getTargetTypeString(At pattern) {
        // If legacy string value is provided, use it for backwards compatibility
        if (!pattern.stringValue().isEmpty()) {
            return pattern.stringValue().toUpperCase();
        }
        
        // Try to get the raw annotation value without triggering the proxy cast
        try {
            Object valueObj = getRawAnnotationValue(pattern, "value");
            if (valueObj instanceof String) {
                return ((String) valueObj).toUpperCase();
            }
        } catch (Exception e) {
            System.err.println("Error getting target type string: " + e.getMessage());
        }
        
        // Default fallback
        return "INVOKE";
    }
    
    /**
     * Attempt to get raw annotation value without triggering proxy casting
     */
    private static Object getRawAnnotationValue(Object proxy, String methodName) {
        try {
            if (java.lang.reflect.Proxy.isProxyClass(proxy.getClass())) {
                java.lang.reflect.InvocationHandler handler = java.lang.reflect.Proxy.getInvocationHandler(proxy);
                // Try to access the underlying values map via reflection
                java.lang.reflect.Field[] fields = handler.getClass().getDeclaredFields();
                for (java.lang.reflect.Field field : fields) {
                    field.setAccessible(true);
                    Object value = field.get(handler);
                    if (value instanceof java.util.Map) {
                        @SuppressWarnings("unchecked")
                        java.util.Map<String, Object> map = (java.util.Map<String, Object>) value;
                        return map.get(methodName);
                    }
                }
            }
        } catch (Exception e) {
            // Ignore and fallback
        }
        return null;
    }
    
    /**
     * Filter matches by context to avoid ambiguity with bare field names.
     * This ensures that field access patterns without class qualification
     * only match fields belonging to the target class.
     */
    private static List<InstructionMatcher.MatchResult> filterMatchesByContext(
            List<InstructionMatcher.MatchResult> matches, 
            At pattern, 
            ClassNode targetClass) {
        
        String value = getTargetTypeString(pattern);
        String target = pattern.target();
        
        // If target contains class qualification, no filtering needed
        if (target.contains(".")) {
            return matches;
        }
        
        // For field access patterns, only filter if NO owner was specified
        if ((value.equals("GETFIELD") || value.equals("PUTFIELD") || 
             value.equals("GETSTATIC") || value.equals("PUTSTATIC")) &&
            pattern.owner().isEmpty()) {
            
            return matches.stream()
                    .filter(match -> {
                        if (match.instruction instanceof FieldInsnNode) {
                            FieldInsnNode fieldInsn = (FieldInsnNode) match.instruction;
                            
                            // Only include matches where the field belongs to the target class
                            // or its superclasses (for inherited fields)
                            return fieldInsn.owner.equals(targetClass.name) ||
                                   isInheritedField(fieldInsn.owner, targetClass);
                        }
                        return true;
                    })
                    .collect(Collectors.toList());
        }
        
        return matches;
    }
    
    /**
     * Check if a field owner is a superclass of the target class.
     * This handles cases where the field is inherited.
     */
    private static boolean isInheritedField(String fieldOwner, ClassNode targetClass) {
        // Check if fieldOwner is the superclass
        if (targetClass.superName != null && targetClass.superName.equals(fieldOwner)) {
            return true;
        }
        
        // Check interfaces (fields can be inherited from interfaces)
        if (targetClass.interfaces != null) {
            return targetClass.interfaces.contains(fieldOwner);
        }
        
        return false;
    }
}
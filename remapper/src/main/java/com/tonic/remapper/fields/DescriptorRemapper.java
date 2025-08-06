package com.tonic.remapper.fields;

import com.tonic.remapper.classes.ClassMatch;
import org.objectweb.asm.Type;
import java.util.HashMap;
import java.util.Map;

/**
 * Utilities for remapping type descriptors using class mappings.
 * Handles the fact that obfuscated class names change between versions.
 */
public class DescriptorRemapper {

    /**
     * Remap an old descriptor to use new class names based on class mappings.
     * For example, if "eb" maps to "dn", then "Leb;" becomes "Ldn;"
     *
     * @param oldDescriptor The descriptor from the old version
     * @param classMapping Map from old class internal names to new class internal names
     * @return The descriptor with class names remapped, or the original if no mapping exists
     */
    public static String remapDescriptor(String oldDescriptor, Map<String, String> classMapping) {
        if (classMapping == null || classMapping.isEmpty()) {
            return oldDescriptor;
        }

        // Handle primitive types and arrays of primitives - no remapping needed
        Type type = Type.getType(oldDescriptor);
        if (type.getSort() < Type.ARRAY) {
            // Primitive type
            return oldDescriptor;
        }

        if (type.getSort() == Type.ARRAY) {
            // Array type - remap the element type
            Type elementType = type.getElementType();
            if (elementType.getSort() == Type.OBJECT) {
                String remappedElement = remapDescriptor(elementType.getDescriptor(), classMapping);
                return "[".repeat(type.getDimensions()) + remappedElement;
            }
            // Array of primitives - no remapping needed
            return oldDescriptor;
        }

        if (type.getSort() == Type.OBJECT) {
            // Object type - remap the class name
            String className = type.getInternalName();
            String newClassName = classMapping.getOrDefault(className, className);
            return "L" + newClassName + ";";
        }

        return oldDescriptor;
    }

    /**
     * Check if two descriptors are equivalent after remapping.
     * This is the key method for field type comparison.
     *
     * @param oldDesc Descriptor from old version
     * @param newDesc Descriptor from new version
     * @param oldToNewClassMap Mapping from old class names to new class names
     * @return true if the descriptors represent the same type after remapping
     */
    public static boolean descriptorsMatch(String oldDesc, String newDesc,
                                           Map<String, String> oldToNewClassMap) {
        // First try exact match
        if (oldDesc.equals(newDesc)) {
            return true;
        }

        // Remap the old descriptor and compare
        String remappedOldDesc = remapDescriptor(oldDesc, oldToNewClassMap);
        return remappedOldDesc.equals(newDesc);
    }

    /**
     * Create a simple class mapping from ClassMatch results.
     * Maps old internal names to new internal names.
     */
    public static Map<String, String> createClassMapping(
            Map<String, ClassMatch> classMatchByOldOwner) {

        Map<String, String> mapping = new HashMap<>();

        for (Map.Entry<String, ClassMatch> entry : classMatchByOldOwner.entrySet()) {
            String oldClass = entry.getKey();
            ClassMatch match = entry.getValue();
            if (match != null && match.similarity > 0.3) {  // Only include confident matches
                mapping.put(oldClass, match.newFp.internalName);
            }
        }

        return mapping;
    }
}
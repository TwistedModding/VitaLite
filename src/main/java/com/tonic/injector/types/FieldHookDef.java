package com.tonic.injector.types;

import com.tonic.dto.JField;
import com.tonic.injector.Injector;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;

@RequiredArgsConstructor
@Getter
public class FieldHookDef
{
    private final JField target;
    private final String hookMethod;
    private final String hookClass;
    private final String hookDesc;

    public boolean isStatic()
    {
        return target.isStatic();
    }

    @Override
    public String toString()
    {
        return target.getOwnerObfuscatedName() + "." + target.getName() + " " + target.getDescriptor() + " -> " + hookClass + "." + hookMethod + hookDesc;
    }
}

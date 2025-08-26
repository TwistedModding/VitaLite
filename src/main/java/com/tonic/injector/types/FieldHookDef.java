package com.tonic.injector.types;

import com.tonic.util.dto.JField;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

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

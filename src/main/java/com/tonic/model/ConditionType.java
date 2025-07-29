package com.tonic.model;

import lombok.Getter;

import static org.objectweb.asm.Opcodes.*;

public enum ConditionType
{
    EQUALS(IF_ICMPEQ, IF_ICMPNE),
    NOT_EQUALS(IF_ICMPNE,IF_ICMPEQ),
    GREATER(IF_ICMPGT, IF_ICMPLE),
    LESS(IF_ICMPLT, IF_ICMPGE),
    GREATER_OR_EQUALS(IF_ICMPGE, IF_ICMPLT),
    LESS_OR_EQUALS(IF_ICMPLE, IF_ICMPGT);

    @Getter
    private final int opcode;
    private final int inverse;

    ConditionType(int opcode, int inverse)
    {
        this.opcode = opcode;
        this.inverse = inverse;
    }

    public ConditionType invert()
    {
        for (ConditionType type : values())
        {
            if (type.opcode == inverse)
            {
                return type;
            }
        }
        return EQUALS; //default
    }
}
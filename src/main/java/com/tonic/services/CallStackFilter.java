package com.tonic.services;

import lombok.Getter;

import java.util.Set;

public class CallStackFilter
{
    @Getter
    private static Set<String> ignored = Set.of(
            "com.tonic",
            "com.example" //ethans api
    );
}

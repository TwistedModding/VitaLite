package com.tonic.util.optionsparser;

import com.tonic.util.optionsparser.annotations.CLIArgument;
import lombok.Getter;

@Getter
public class VitaLiteOptions extends OptionsParser
{
    @CLIArgument(
            name = "rsdump",
            description = "[Optional] Path to dump the gamepack to"
    )
    private String rsdump = null;
}

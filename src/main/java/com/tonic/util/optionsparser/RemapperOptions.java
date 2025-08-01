package com.tonic.util.optionsparser;

import com.tonic.util.optionsparser.annotations.CLIArgument;
import lombok.Getter;

@Getter
public class RemapperOptions extends OptionsParser
{
    @CLIArgument(
            name = "oldJar",
            description = "Path to the older revision"
    )
    private String oldJar = null;

    @CLIArgument(
            name = "newJar",
            description = "Path to the older revision"
    )
    private String newJar = null;
}

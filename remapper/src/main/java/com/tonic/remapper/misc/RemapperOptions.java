package com.tonic.remapper.misc;

import com.tonic.optionsparser.OptionsParser;
import com.tonic.optionsparser.annotations.CLIArgument;
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

    @CLIArgument(
            name = "oldMappings",
            description = "[Optional] Path to the older revisions mappings"
    )
    private String oldMappings = null;

    @CLIArgument(
            name = "newMappings",
            description = "[Optional] Path to save the new mappings"
    )
    private String newMappings = null;

    @CLIArgument(
            name = "verbose",
            description = "[Optional] Path to save the new mappings"
    )
    private boolean verbose = false;

    @CLIArgument(
            name = "editor",
            description = "[Optional] Open up the mappings editor"
    )
    private boolean editor = false;
}

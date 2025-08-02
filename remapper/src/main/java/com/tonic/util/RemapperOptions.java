package com.tonic.util;

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
            name = "newMapping",
            description = "[Optional] Path to save the new mappings"
    )
    private String newMapping = null;

    @CLIArgument(
            name = "editor",
            description = "[Optional] Open up the mappings editor"
    )
    private boolean editor = false;
}

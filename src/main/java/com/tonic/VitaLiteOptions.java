package com.tonic;

import com.tonic.optionsparser.OptionsParser;
import com.tonic.optionsparser.annotations.CLIArgument;
import lombok.Getter;

@Getter
public class VitaLiteOptions extends OptionsParser
{
    @CLIArgument(
            name = "rsdump",
            description = "[Optional] Path to dump the gamepack to"
    )
    private String rsdump = null;

    @CLIArgument(
            name = "noPlugins",
            description = "[Optional] Disables loading of core plugins"
    )
    private boolean noPlugins = false;

    @CLIArgument(
            name = "incognito",
            description = "[Optional] Visually display as 'RuneLite' instead of 'VitaLite'"
    )
    private boolean incognito = false;

    @CLIArgument(
            name = "safeLaunch",
            description = "Flag to ensure proper Launching from Launcher class."
    )
    private boolean safeLaunch = false;
}

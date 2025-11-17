package com.enterprise.framework.cli

import com.enterprise.framework.cli.commands.*
import picocli.CommandLine
import picocli.CommandLine.Command
import picocli.CommandLine.HelpCommand
import java.util.concurrent.Callable

/**
 * Main CLI entry point for Enterprise Service Framework Control Plane
 */
@Command(
    name = "esf",
    description = ["Enterprise Service Framework Control Plane CLI"],
    mixinStandardHelpOptions = true,
    version = ["Enterprise Service Framework CLI 1.0.0"],
    subcommands = [
        HelpCommand::class,
        AuthCommand::class,
        AuthzCommand::class,
        PolicyCommand::class,
        BootstrapCommand::class,
        ConfigCommand::class
    ]
)
class CLIMain : Callable<Int> {
    
    override fun call(): Int {
        // If no subcommand is provided, show help
        CommandLine(this).usage(System.out)
        return 0
    }
    
    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            val exitCode = CommandLine(CLIMain()).execute(*args)
            System.exit(exitCode)
        }
    }
}


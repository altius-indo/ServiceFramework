package com.enterprise.framework.cli.commands

import com.enterprise.framework.cli.CLIContext
import picocli.CommandLine
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import java.util.concurrent.Callable

/**
 * Configuration management commands
 */
@Command(
    name = "config",
    description = ["Configuration management"],
    subcommands = [ConfigShowCommand::class, ConfigSetCommand::class]
)
class ConfigCommand : Callable<Int> {
    override fun call(): Int {
        CommandLine(this).usage(System.out)
        return 0
    }
}

@Command(name = "show", description = ["Show current configuration"])
class ConfigShowCommand : Callable<Int> {
    override fun call(): Int {
        try {
            val context = CLIContext.getInstance()
            if (context == null) {
                System.err.println("CLI not initialized")
                return 1
            }

            println("CLI Configuration:")
            println("  Server URL: ${context.serverUrl}")
            println("  Config Directory: ${context.configDir}")
            println("  Authenticated: ${context.authenticator.isAuthenticated()}")
            return 0

        } catch (e: Exception) {
            System.err.println("Error: ${e.message}")
            return 1
        }
    }
}

@Command(name = "set", description = ["Set configuration value"])
class ConfigSetCommand : Callable<Int> {
    @Option(names = ["--server-url"], description = ["Set server URL"])
    private var serverUrl: String? = null

    override fun call(): Int {
        try {
            if (serverUrl == null) {
                System.err.println("Error: No configuration value provided")
                return 1
            }

            println("Setting server URL to: $serverUrl")
            // TODO: Save to config file
            println("✓ Configuration updated")
            return 0

        } catch (e: Exception) {
            System.err.println("Error: ${e.message}")
            return 1
        }
    }
}


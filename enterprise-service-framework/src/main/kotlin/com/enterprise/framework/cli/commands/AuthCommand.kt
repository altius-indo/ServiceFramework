package com.enterprise.framework.cli.commands

import com.enterprise.framework.cli.CLIContext
import kotlinx.coroutines.runBlocking
import picocli.CommandLine
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import java.util.concurrent.Callable

/**
 * Authentication command
 */
@Command(
    name = "auth",
    description = ["Authentication commands"],
    subcommands = [AuthLoginCommand::class, AuthLogoutCommand::class, AuthStatusCommand::class]
)
class AuthCommand : Callable<Int> {
    override fun call(): Int {
        CommandLine(this).usage(System.out)
        return 0
    }
}

@Command(
    name = "login",
    description = ["Login to the system"]
)
class AuthLoginCommand : Callable<Int> {

    @Option(
        names = ["--username", "-u"],
        description = ["Username"],
        required = true
    )
    private var username: String? = null

    @Option(
        names = ["--password", "-p"],
        description = ["Password (will prompt if not provided)"],
        interactive = true,
        arity = "0..1"
    )
    private var password: String? = null

    @Option(
        names = ["--server-url"],
        description = ["Server URL"],
        defaultValue = "http://localhost:8080"
    )
    private var serverUrl: String = "http://localhost:8080"

    override fun call(): Int {
        try {
            val context = CLIContext.initialize(serverUrl)

            if (password == null) {
                print("Enter password: ")
                password = System.console()?.readPassword()?.joinToString("") 
                    ?: readLine() ?: ""
            }

            if (username.isNullOrBlank() || password.isNullOrBlank()) {
                System.err.println("Error: Username and password are required")
                return 1
            }

            println("Authenticating...")
            val result = runBlocking {
                context.authenticator.authenticate(username!!, password!!)
            }

            if (result.success) {
                println("✓ Authentication successful!")
                println("  User: $username")
                println("  Token saved to: ${context.configDir}/token.json")
                return 0
            } else {
                System.err.println("✗ Authentication failed: ${result.message}")
                return 1
            }

        } catch (e: Exception) {
            System.err.println("Error: ${e.message}")
            return 1
        }
    }
}

@Command(
    name = "logout",
    description = ["Logout from the system"]
)
class AuthLogoutCommand : Callable<Int> {
    override fun call(): Int {
        try {
            val context = CLIContext.getInstance()
            if (context == null) {
                System.err.println("Not authenticated")
                return 1
            }

            runBlocking {
                context.authenticator.logout()
            }
            println("✓ Logged out successfully")
            return 0

        } catch (e: Exception) {
            System.err.println("Error: ${e.message}")
            return 1
        }
    }
}

@Command(
    name = "status",
    description = ["Check authentication status"]
)
class AuthStatusCommand : Callable<Int> {
    override fun call(): Int {
        try {
            val context = CLIContext.getInstance()
            if (context == null) {
                println("Not initialized")
                return 1
            }

            val isAuth = context.authenticator.isAuthenticated()
            if (isAuth) {
                println("✓ Authenticated")
                val token = runBlocking {
                    context.authenticator.getToken()
                }
                if (token != null) {
                    println("  Token: ${token.take(20)}...")
                }
                return 0
            } else {
                println("✗ Not authenticated")
                println("  Run 'esf auth login' to authenticate")
                return 1
            }

        } catch (e: Exception) {
            System.err.println("Error: ${e.message}")
            return 1
        }
    }
}


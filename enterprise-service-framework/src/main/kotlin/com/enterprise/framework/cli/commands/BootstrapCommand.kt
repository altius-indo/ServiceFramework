package com.enterprise.framework.cli.commands

import com.enterprise.framework.cli.BootstrapManager
import com.enterprise.framework.cli.CLIContext
import kotlinx.coroutines.runBlocking
import picocli.CommandLine
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import java.util.concurrent.Callable

/**
 * Bootstrap command for initial system setup
 */
@Command(
    name = "bootstrap",
    description = ["Bootstrap the system with initial admin user and roles"]
)
class BootstrapCommand : Callable<Int> {

    @Option(
        names = ["--username", "-u"],
        description = ["Admin username"],
        required = true
    )
    private var username: String? = null

    @Option(
        names = ["--password", "-p"],
        description = ["Admin password"],
        required = true,
        interactive = true,
        arity = "0..1"
    )
    private var password: String? = null

    @Option(
        names = ["--email", "-e"],
        description = ["Admin email"],
        required = true
    )
    private var email: String? = null

    @Option(
        names = ["--force"],
        description = ["Force bootstrap even if already completed"]
    )
    private var force: Boolean = false

    @Option(
        names = ["--server-url"],
        description = ["Server URL (default: http://localhost:8080)"],
        defaultValue = "http://localhost:8080"
    )
    private var serverUrl: String = "http://localhost:8080"

    override fun call(): Int {
        try {
            val context = CLIContext.initialize(serverUrl)
            
            if (password == null) {
                print("Enter admin password: ")
                password = System.console()?.readPassword()?.joinToString("") 
                    ?: readLine() ?: ""
            }

            if (username.isNullOrBlank() || password.isNullOrBlank() || email.isNullOrBlank()) {
                System.err.println("Error: Username, password, and email are required")
                return 1
            }

            println("Starting bootstrap process...")
            println("Username: $username")
            println("Email: $email")
            println("Server: $serverUrl")
            println()

            // Note: In a real implementation, this would call the server API
            // For now, we'll use direct database access via context
            val result = runBlocking {
                if (force) {
                    context.bootstrapManager.forceBootstrap(username!!, password!!, email!!)
                } else {
                    context.bootstrapManager.bootstrap(username!!, password!!, email!!)
                }
            }

            if (result.success) {
                println("✓ Bootstrap completed successfully!")
                println("  Admin user: $username")
                result.adminUserId?.let { println("  User ID: $it") }
                result.adminRoleId?.let { println("  Role ID: $it") }
                println()
                println("You can now use this user to authenticate and manage the system.")
                return 0
            } else {
                System.err.println("✗ Bootstrap failed: ${result.message}")
                return 1
            }

        } catch (e: Exception) {
            System.err.println("Error: ${e.message}")
            e.printStackTrace()
            return 1
        }
    }
}


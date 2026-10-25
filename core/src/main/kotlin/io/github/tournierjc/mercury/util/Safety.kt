package io.github.tournierjc.mercury.util

/**
 * Commands that can destroy data or stop a service on the host. The terminal screen
 * asks for a second confirmation before sending these, and nothing else: a longer
 * denylist would only teach the owner to click through it.
 */
private val destructivePatterns = listOf(
    "rm -rf", "rm -fr", "rm -f /", "mkfs", "dd if=", "dd of=", "shutdown", "reboot",
    "docker rm", "docker rmi", "docker system prune", "docker volume rm", "docker stop",
    "git reset --hard", "git clean -fd", "git push --force", "git push -f",
    "truncate -s", "chmod -r 777", "> /dev/sd",
)

fun isDestructiveCommand(command: String): Boolean {
    val normalized = command.lowercase().replace(Regex("\\s+"), " ")
    return destructivePatterns.any { normalized.contains(it) }
}

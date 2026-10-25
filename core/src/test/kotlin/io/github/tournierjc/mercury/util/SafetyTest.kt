package io.github.tournierjc.mercury.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SafetyTest {

    @Test
    fun `common destructive commands are flagged`() {
        assertTrue(isDestructiveCommand("rm -rf /work/*"))
        assertTrue(isDestructiveCommand("docker   rm -f mercury"))
        assertTrue(isDestructiveCommand("git reset --hard HEAD~1"))
        assertTrue(isDestructiveCommand("GIT PUSH -F origin main"))
        assertTrue(isDestructiveCommand("docker system prune -a"))
        assertTrue(isDestructiveCommand("shutdown -h now"))
    }

    @Test
    fun `ordinary inspection commands are not flagged`() {
        assertFalse(isDestructiveCommand("docker ps"))
        assertFalse(isDestructiveCommand("git status"))
        assertFalse(isDestructiveCommand("ls -la /work"))
        assertFalse(isDestructiveCommand("cat README.md"))
        assertFalse(isDestructiveCommand("df -h"))
        assertFalse(isDestructiveCommand(""))
    }

    @Test
    fun `whitespace differences do not hide a destructive command`() {
        assertTrue(isDestructiveCommand("docker    rm   mercury"))
        assertTrue(isDestructiveCommand("rm    -rf   data"))
    }
}

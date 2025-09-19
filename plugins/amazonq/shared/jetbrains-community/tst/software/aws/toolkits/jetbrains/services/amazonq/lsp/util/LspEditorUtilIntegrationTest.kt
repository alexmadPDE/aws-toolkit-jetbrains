// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package software.aws.toolkits.jetbrains.services.amazonq.lsp.util

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.ApplicationExtension
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS
import org.junit.jupiter.api.extension.ExtendWith
import java.io.File
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.io.path.createTempFile

/**
 * Integration tests for LspEditorUtil focusing on the Windows URI construction bug fix.
 * These tests use real file system operations and cross-platform scenarios.
 */
@ExtendWith(ApplicationExtension::class)
class LspEditorUtilIntegrationTest {

    private fun createMockVirtualFile(path: String, mockProtocol: String = "file", mockIsDirectory: Boolean = false): VirtualFile =
        mockk<VirtualFile> {
            every { fileSystem } returns mockk {
                every { protocol } returns mockProtocol
            }
            every { url } returns path
            every { isDirectory } returns mockIsDirectory
        }

    @Test
    fun `integration test - URI generation with real temp files`() {
        val tempFile = createTempFile("integration-test", ".kt")
        try {
            val virtualFile = createMockVirtualFile(tempFile.toAbsolutePath().toString())
            val result = LspEditorUtil.toUriString(virtualFile)
            
            assertThat(result).isNotNull()
            assertThat(result).startsWith("file:///")
            
            // Should be parseable as a valid URI
            val uri = URI.create(result!!)
            assertThat(uri.scheme).isEqualTo("file")
            assertThat(uri.path).contains("integration-test")
            assertThat(uri.path).endsWith(".kt")
            
            // Cross-check with Path.toUri() directly
            val expectedUri = tempFile.toAbsolutePath().normalize().toUri()
            assertThat(result).isEqualTo(expectedUri.toASCIIString())
            
        } finally {
            Files.deleteIfExists(tempFile)
        }
    }

    @Test
    fun `integration test - URI generation with real directories`() {
        val tempDir = createTempDirectory("integration-test-dir")
        try {
            val virtualFile = createMockVirtualFile(tempDir.toAbsolutePath().toString(), mockIsDirectory = true)
            val result = LspEditorUtil.toUriString(virtualFile)
            
            assertThat(result).isNotNull()
            assertThat(result).startsWith("file:///")
            assertThat(result).contains("integration-test-dir")
            
            // Directory URIs should not end with slash after processing
            assertThat(result).doesNotEndWith("/")
            assertThat(result).doesNotEndWith("\\")
            
        } finally {
            Files.deleteIfExists(tempDir)
        }
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    fun `integration test - Windows MCP workspace scenario`() {
        // Simulate the exact scenario that caused the Windows bug
        val tempDir = createTempDirectory("test-workspace")
        val mcpDir = tempDir.resolve(".amazonq").resolve("agents")
        Files.createDirectories(mcpDir)
        
        try {
            val virtualFile = createMockVirtualFile(mcpDir.toAbsolutePath().toString(), mockIsDirectory = true)
            val uriString = LspEditorUtil.toUriString(virtualFile)
            
            assertThat(uriString).isNotNull()
            assertThat(uriString).startsWith("file:///")
            
            // Simulate the problematic path construction that was failing
            val ideBasePath = "C:\\Program Files\\JetBrains\\PyCharm Community Edition 2024.1"
            val problematicPath = "$ideBasePath\\$uriString"
            
            // The bug would create: C:\Program Files\JetBrains\PyCharm Community Edition 2024.1\file:\C:\...
            // Verify this doesn't happen
            assertThat(problematicPath).doesNotContain("\\file:\\")
            assertThat(problematicPath).doesNotContain("\\file:/")
            
            // URI should be well-formed
            assertThat(uriString).matches("file:///[A-Za-z]:/.*\\.amazonq.*agents.*")
            
        } finally {
            Files.walk(tempDir).sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
        }
    }

    @Test
    fun `integration test - cross-platform path handling`() {
        val tempFile = createTempFile("cross-platform-test", ".json")
        try {
            val absolutePath = tempFile.toAbsolutePath().toString()
            val virtualFile = createMockVirtualFile(absolutePath)
            val result = LspEditorUtil.toUriString(virtualFile)
            
            assertThat(result).isNotNull()
            assertThat(result).startsWith("file:///")
            
            // Test URI can be converted back to Path consistently
            val uri = URI.create(result!!)
            val reconstructedPath = Path.of(uri)
            assertThat(reconstructedPath.toAbsolutePath().normalize()).isEqualTo(tempFile.toAbsolutePath().normalize())
            
        } finally {
            Files.deleteIfExists(tempFile)
        }
    }

    @Test
    fun `integration test - workspace with special characters`() {
        // Create a workspace with special characters that could cause URI issues
        val tempDir = createTempDirectory("test workspace with spaces & symbols")
        val projectFile = tempDir.resolve("My Project (2024).kt")
        Files.createFile(projectFile)
        
        try {
            val virtualFile = createMockVirtualFile(projectFile.toAbsolutePath().toString())
            val result = LspEditorUtil.toUriString(virtualFile)
            
            assertThat(result).isNotNull()
            assertThat(result).startsWith("file:///")
            
            // Special characters should be properly encoded
            assertThat(result).contains("%20") // Space
            assertThat(result).contains("%28") // (
            assertThat(result).contains("%29") // )
            
            // URI should be parseable
            val uri = URI.create(result!!)
            assertThat(uri.path).contains("My Project (2024).kt")
            
        } finally {
            Files.walk(tempDir).sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
        }
    }

    @Test
    fun `integration test - nested project structure`() {
        // Create a realistic project structure
        val tempDir = createTempDirectory("aws-toolkit-test")
        val srcDir = tempDir.resolve("src").resolve("main").resolve("kotlin")
        val packageDir = srcDir.resolve("software").resolve("aws").resolve("toolkits")
        Files.createDirectories(packageDir)
        
        val testFile = packageDir.resolve("TestClass.kt")
        Files.createFile(testFile)
        
        try {
            val virtualFile = createMockVirtualFile(testFile.toAbsolutePath().toString())
            val result = LspEditorUtil.toUriString(virtualFile)
            
            assertThat(result).isNotNull()
            assertThat(result).startsWith("file:///")
            assertThat(result).contains("aws-toolkit-test")
            assertThat(result).contains("TestClass.kt")
            assertThat(result).endsWith(".kt")
            
            // Deep path should be properly normalized
            assertThat(result.substring(7)).doesNotContain("//") // No double slashes after scheme
            
        } finally {
            Files.walk(tempDir).sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
        }
    }

    @Test
    fun `integration test - concurrent URI generation with real files`() {
        val tempFiles = (1..10).map { createTempFile("concurrent-test-$it", ".kt") }
        
        try {
            val results = tempFiles.parallelStream().map { tempFile ->
                val virtualFile = createMockVirtualFile(tempFile.toAbsolutePath().toString())
                LspEditorUtil.toUriString(virtualFile)
            }.toList()
            
            // All results should be valid and unique
            assertThat(results).allMatch { it != null && it.startsWith("file:///") }
            assertThat(results.toSet()).hasSize(tempFiles.size) // All unique
            
            // Each result should correspond to its file
            results.forEachIndexed { index, result ->
                assertThat(result).contains("concurrent-test-${index + 1}")
                assertThat(result).endsWith(".kt")
            }
            
        } finally {
            tempFiles.forEach { Files.deleteIfExists(it) }
        }
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    fun `integration test - Windows drive letter variations`() {
        // Test different Windows drive scenarios
        val drivePaths = listOf("C:", "D:", "E:", "F:")
        
        drivePaths.forEach { drive ->
            val simulatedPath = "$drive\\Users\\TestUser\\project\\file.kt"
            val normalizedPath = simulatedPath.replace("\\", "/")
            
            val virtualFile = createMockVirtualFile(normalizedPath)
            val result = LspEditorUtil.toUriString(virtualFile)
            
            assertThat(result).isNotNull()
            assertThat(result).startsWith("file:///")
            assertThat(result).matches("file:///${drive[0]}:/.*")
            
            // Should not contain problematic patterns
            assertThat(result).doesNotContain("file:\\")
            assertThat(result).doesNotContain("\\\\")
        }
    }

    @Test
    fun `integration test - comparison with Java File.toURI()`() {
        val tempFile = createTempFile("comparison-test", ".java")
        
        try {
            val javaFile = tempFile.toFile()
            val javaUri = javaFile.toURI()
            val pathUri = tempFile.toUri()
            
            val virtualFile = createMockVirtualFile(tempFile.toAbsolutePath().toString())
            val lspUri = LspEditorUtil.toUriString(virtualFile)
            
            assertThat(lspUri).isNotNull()
            
            // Our implementation should match Path.toUri() (not File.toURI())
            assertThat(lspUri).isEqualTo(pathUri.toASCIIString())
            
            // All should represent the same file
            val lspUriObject = URI.create(lspUri!!)
            assertThat(Path.of(lspUriObject)).isEqualTo(Path.of(javaUri))
            assertThat(Path.of(lspUriObject)).isEqualTo(Path.of(pathUri))
            
        } finally {
            Files.deleteIfExists(tempFile)
        }
    }

    @Test
    fun `integration test - verify fix prevents ENOENT errors`() {
        // This test simulates the exact error condition that was occurring
        val tempDir = createTempDirectory("enoent-test")
        val mcpAgentsDir = tempDir.resolve(".amazonq").resolve("agents")
        Files.createDirectories(mcpAgentsDir)
        
        try {
            val virtualFile = createMockVirtualFile(mcpAgentsDir.toAbsolutePath().toString(), mockIsDirectory = true)
            val uriString = LspEditorUtil.toUriString(virtualFile)
            
            assertThat(uriString).isNotNull()
            
            // Simulate how the URI was being used in path construction
            val baseDirectory = File(System.getProperty("java.io.tmpdir"))
            val attemptedPath = File(baseDirectory, uriString!!)
            
            // The old bug would create paths like: /tmp/file:/C:/Users/...
            // which would fail with ENOENT. Verify the path is now reasonable.
            assertThat(attemptedPath.path).doesNotContain("file:")
            
            // The URI should be a proper URI, not something that gets mixed into filesystem paths
            assertThat(uriString).startsWith("file:///")
            val uri = URI.create(uriString)
            assertThat(uri.scheme).isEqualTo("file")
            
        } finally {
            Files.walk(tempDir).sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
        }
    }
}
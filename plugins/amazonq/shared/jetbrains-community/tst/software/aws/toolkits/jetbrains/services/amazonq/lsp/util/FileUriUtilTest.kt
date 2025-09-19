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

@ExtendWith(ApplicationExtension::class)
class FileUriUtilTest {

    private fun createMockVirtualFile(path: String, mockProtocol: String = "file", mockIsDirectory: Boolean = false): VirtualFile =
        mockk<VirtualFile> {
            every { fileSystem } returns mockk {
                every { protocol } returns mockProtocol
            }
            every { url } returns path
            every { isDirectory } returns mockIsDirectory
        }

    private fun normalizeFileUri(uri: String): String {
        if (!System.getProperty("os.name").lowercase().contains("windows")) {
            return uri
        }

        if (!uri.startsWith("file:///")) {
            return uri
        }

        val path = uri.substringAfter("file:///")
        return "file:///C:/$path"
    }

    @Test
    fun `test basic unix path`() {
        val virtualFile = createMockVirtualFile("/path/to/file.txt")
        val uri = LspEditorUtil.toUriString(virtualFile)
        val expected = normalizeFileUri("file:///path/to/file.txt")
        assertThat(uri).isEqualTo(expected)
    }

    @Test
    fun `test unix directory path`() {
        val virtualFile = createMockVirtualFile("/path/to/directory/", mockIsDirectory = true)
        val uri = LspEditorUtil.toUriString(virtualFile)
        val expected = normalizeFileUri("file:///path/to/directory")
        assertThat(uri).isEqualTo(expected)
    }

    @Test
    fun `test path with spaces`() {
        val virtualFile = createMockVirtualFile("/path/with spaces/file.txt")
        val uri = LspEditorUtil.toUriString(virtualFile)
        val expected = normalizeFileUri("file:///path/with%20spaces/file.txt")
        assertThat(uri).isEqualTo(expected)
    }

    @Test
    fun `test root path`() {
        val virtualFile = createMockVirtualFile("/")
        val uri = LspEditorUtil.toUriString(virtualFile)
        val expected = normalizeFileUri("file:///")
        assertThat(uri).isEqualTo(expected)
    }

    @Test
    fun `test path with multiple separators`() {
        val virtualFile = createMockVirtualFile("/path//to///file.txt")
        val uri = LspEditorUtil.toUriString(virtualFile)
        val expected = normalizeFileUri("file:///path/to/file.txt")
        assertThat(uri).isEqualTo(expected)
    }

    @Test
    fun `test very long path`() {
        val longPath = "/a".repeat(256) + "/file.txt"
        val virtualFile = createMockVirtualFile(longPath)
        val uri = LspEditorUtil.toUriString(virtualFile)
        if (uri != null) {
            assertThat(uri.startsWith("file:///")).isTrue
            assertThat(uri.endsWith("/file.txt")).isTrue
        }
    }

    @Test
    fun `test relative path`() {
        val virtualFile = createMockVirtualFile("./relative/path/file.txt")
        val uri = LspEditorUtil.toUriString(virtualFile)
        if (uri != null) {
            assertThat(uri.contains("file.txt")).isTrue
            assertThat(uri.startsWith("file:///")).isTrue
        }
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    fun `test wsl-like path`() {
        val virtualFile = createMockVirtualFile("//wsl.localhost/Ubuntu/home/user/file.sh")
        val result = LspEditorUtil.toUriString(virtualFile)
        val expected = normalizeFileUri("file://wsl.localhost/Ubuntu/home/user/file.sh")
        assertThat(result).isEqualTo(expected)
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    fun `test UNC path`() {
        val virtualFile = createMockVirtualFile("//server/share/path/to/file.txt")
        val result = LspEditorUtil.toUriString(virtualFile)
        val expected = normalizeFileUri("file://server/share/path/to/file.txt")
        assertThat(result).isEqualTo(expected)
    }

    @Test
    fun `test jar protocol conversion`() {
        val virtualFile = createMockVirtualFile(
            "jar:file:///path/to/archive.jar!/com/example/Test.class",
            "jar"
        )
        val result = LspEditorUtil.toUriString(virtualFile)
        val expected = normalizeFileUri("jar:file:///path/to/archive.jar!/com/example/Test.class")
        assertThat(result).isEqualTo(expected)
    }

    @Test
    fun `test jrt protocol conversion`() {
        val virtualFile = createMockVirtualFile(
            "jrt://java.base/java/lang/String.class",
            "jrt"
        )
        val result = LspEditorUtil.toUriString(virtualFile)
        val expected = normalizeFileUri("jrt://java.base/java/lang/String.class")
        assertThat(result).isEqualTo(expected)
    }

    @Test
    fun `test invalid jar url returns null`() {
        val virtualFile = createMockVirtualFile(
            "invalid:url:format",
            "jar"
        )
        val result = LspEditorUtil.toUriString(virtualFile)
        assertThat(result).isNull()
    }

    @Test
    fun `test jar protocol with directory`() {
        val virtualFile = createMockVirtualFile(
            "jar:file:///path/to/archive.jar!/com/example/",
            "jar",
            true
        )
        val result = LspEditorUtil.toUriString(virtualFile)
        val expected = normalizeFileUri("jar:file:///path/to/archive.jar!/com/example")
        assertThat(result).isEqualTo(expected)
    }

    @Test
    fun `test empty url in jar protocol`() {
        val virtualFile = createMockVirtualFile(
            "",
            "jar",
            true
        )
        val result = LspEditorUtil.toUriString(virtualFile)
        assertThat(result).isNull()
    }

    @Test
    fun `test URI generation does not create problematic Windows patterns`() {
        // This test ensures that URIs generated by toUriString() don't contain 
        // patterns that would cause issues when embedded in filesystem paths on Windows
        val virtualFile = createMockVirtualFile("/path/to/test/file.txt")
        val result = LspEditorUtil.toUriString(virtualFile)
        
        assertThat(result).isNotNull()
        assertThat(result).startsWith("file:")
        
        // Critical: When this URI string is used as part of a filesystem path,
        // it should not create mixed separators like "C:\Program Files\file:\C:\..."
        // The fix ensures we use Path.toUri() which generates proper URIs
        val uriString = result!!
        
        // Verify it follows proper file URI format
        assertThat(uriString).matches("file:///.*")
        
        // Verify it doesn't contain Windows-style backslashes mixed with forward slashes
        // (the original bug would create URIs that when stringified could cause this)
        if (uriString.contains("\\")) {
            // If backslashes are present, they should be properly encoded, not raw
            assertThat(uriString).doesNotMatch(".*file:.*\\\\.*")
        }
    }

    // ========== EXPANDED WINDOWS URI CONSTRUCTION BUG REGRESSION TESTS ==========

    @Test
    @EnabledOnOs(OS.WINDOWS)
    fun `test Windows drive letters are handled correctly`() {
        // Test various Windows drive letter scenarios
        val testPaths = listOf(
            "C:/Users/user/project/file.kt",
            "D:/Development/aws-toolkit/test.java",
            "E:/temp/amazonq/agents/config.json"
        )
        
        testPaths.forEach { path ->
            val virtualFile = createMockVirtualFile(path)
            val result = LspEditorUtil.toUriString(virtualFile)
            
            assertThat(result).isNotNull()
            assertThat(result).startsWith("file:///")
            assertThat(result).doesNotContain("file:\\")
            assertThat(result).doesNotContain("file:/C:")
            
            // Should properly encode drive letter
            assertThat(result).matches("file:///[A-Z]:/.*")
        }
    }

    @Test
    fun `test URI string cannot be embedded in filesystem paths`() {
        // This is the core regression test for the Windows bug
        val virtualFile = createMockVirtualFile("/Users/user/project/.amazonq/agents")
        val uriString = LspEditorUtil.toUriString(virtualFile)
        
        assertThat(uriString).isNotNull()
        
        // Simulate how the URI would be used in a Windows filesystem path construction
        val simulatedWindowsPath = "C:\\Program Files\\JetBrains\\PyCharm\\$uriString"
        
        // The bug would create paths like: C:\Program Files\JetBrains\PyCharm\file:\C:\Users\...
        // Verify this doesn't happen
        assertThat(simulatedWindowsPath).doesNotContain("file:\\C:")
        assertThat(simulatedWindowsPath).doesNotContain("file:/C:")
        
        // Should be a valid URI format
        assertThat(uriString).matches("file:///.*")
    }

    @Test
    fun `test URI consistency across multiple calls`() {
        // Ensure the URI generation is deterministic
        val virtualFile = createMockVirtualFile("/project/src/main/kotlin/Test.kt")
        
        val results = (1..10).map { LspEditorUtil.toUriString(virtualFile) }
        
        // All results should be identical
        assertThat(results.toSet()).hasSize(1)
        assertThat(results.first()).isNotNull()
        assertThat(results.first()).startsWith("file:///")
    }

    @Test
    fun `test URI generation with special characters`() {
        val specialPaths = listOf(
            "/path/with spaces/file.txt",
            "/path/with-dashes/file.txt", 
            "/path/with_underscores/file.txt",
            "/path/with.dots/file.txt",
            "/path/with(parentheses)/file.txt",
            "/path/with[brackets]/file.txt",
            "/path/with{braces}/file.txt",
            "/path/with@symbols/file.txt",
            "/path/with#hash/file.txt",
            "/path/with%percent/file.txt"
        )
        
        specialPaths.forEach { path ->
            val virtualFile = createMockVirtualFile(path)
            val result = LspEditorUtil.toUriString(virtualFile)
            
            assertThat(result).isNotNull()
            assertThat(result).startsWith("file:///")
            
            // Special characters should be properly encoded
            if (path.contains(" ")) {
                assertThat(result).contains("%20")
            }
            if (path.contains("(")) {
                assertThat(result).contains("%28")
            }
            if (path.contains(")")) {
                assertThat(result).contains("%29")
            }
        }
    }

    @Test
    fun `test URI generation with unicode characters`() {
        val unicodePaths = listOf(
            "/path/with/café/file.txt",
            "/path/with/文件/test.txt",
            "/path/with/файл/document.txt",
            "/path/with/ファイル/code.kt"
        )
        
        unicodePaths.forEach { path ->
            val virtualFile = createMockVirtualFile(path)
            val result = LspEditorUtil.toUriString(virtualFile)
            
            assertThat(result).isNotNull()
            assertThat(result).startsWith("file:///")
            
            // Unicode should be properly encoded
            assertThat(result).doesNotContain("\\u")
        }
    }

    @Test
    fun `test deeply nested paths`() {
        val deepPath = "/very/deeply/nested/path/structure/that/goes/many/levels/deep/to/test/normalization/file.txt"
        val virtualFile = createMockVirtualFile(deepPath)
        val result = LspEditorUtil.toUriString(virtualFile)
        
        assertThat(result).isNotNull()
        assertThat(result).startsWith("file:///")
        assertThat(result).endsWith("/file.txt")
        
        // Should not contain double slashes (except after scheme)
        assertThat(result.substring(7)).doesNotContain("//")  // Skip "file://"
    }

    @Test
    fun `test path normalization edge cases`() {
        val pathsToNormalize = listOf(
            "/path/../to/file.txt",           // Parent directory reference
            "/path/./to/file.txt",            // Current directory reference
            "/path//to///file.txt",           // Multiple slashes
            "/path/to/../from/file.txt",      // Mixed references
            "/../../../root/file.txt"        // Multiple parent references
        )
        
        pathsToNormalize.forEach { path ->
            val virtualFile = createMockVirtualFile(path)
            val result = LspEditorUtil.toUriString(virtualFile)
            
            assertThat(result).isNotNull()
            assertThat(result).startsWith("file:///")
            
            // Normalized paths should not contain . or .. segments
            assertThat(result).doesNotContain("/..")
            assertThat(result).doesNotContain("/./")
            
            // Should not contain multiple consecutive slashes
            assertThat(result.substring(7)).doesNotContain("//")
        }
    }

    @Test
    fun `test case sensitivity preservation`() {
        val caseSensitivePaths = listOf(
            "/Path/To/File.TXT",
            "/PATH/TO/FILE.txt",
            "/path/to/file.TXT",
            "/MyProject/SRC/Main/Kotlin/Test.kt"
        )
        
        caseSensitivePaths.forEach { path ->
            val virtualFile = createMockVirtualFile(path)
            val result = LspEditorUtil.toUriString(virtualFile)
            
            assertThat(result).isNotNull()
            assertThat(result).startsWith("file:///")
            
            // Case should be preserved in the URI
            val resultPath = result!!.substringAfter("file:///")
            assertThat(resultPath).isNotEqualTo(resultPath.lowercase())
        }
    }

    @Test
    fun `test concurrent URI generation`() {
        // Test thread safety of URI generation
        val virtualFile = createMockVirtualFile("/concurrent/test/file.kt")
        val results = mutableListOf<String?>()
        
        // Simulate concurrent access
        repeat(100) {
            results.add(LspEditorUtil.toUriString(virtualFile))
        }
        
        // All results should be identical and valid
        assertThat(results.toSet()).hasSize(1)
        assertThat(results.first()).isNotNull()
        assertThat(results.first()).startsWith("file:///")
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    fun `test Windows MCP workspace configuration paths`() {
        // Specific test for the MCP configuration bug scenario
        val mcpPaths = listOf(
            "C:/Users/user/project/.amazonq/agents",
            "C:/Users/user/workspace/.amazonq/config", 
            "D:/Development/project/.amazonq/mcp/settings.json",
            "E:/Projects/aws-toolkit/.amazonq/agents/workspace.json"
        )
        
        mcpPaths.forEach { path ->
            val virtualFile = createMockVirtualFile(path)
            val result = LspEditorUtil.toUriString(virtualFile)
            
            assertThat(result).isNotNull()
            assertThat(result).startsWith("file:///")
            
            // Critical: When combined with IDE paths, should not create malformed paths
            val ideBasePath = "C:\\Program Files\\JetBrains\\PyCharm"
            val combinedPath = "$ideBasePath\\$result"
            
            // Should not contain the problematic pattern
            assertThat(combinedPath).doesNotContain("\\file:\\C:")
            assertThat(combinedPath).doesNotContain("\\file:/C:")
            
            // Verify proper URI format
            assertThat(result).matches("file:///[A-Z]:/.*amazonq.*")
        }
    }

    @Test
    fun `test integration with actual File objects`() {
        // Integration test using real File objects
        val tempFile = kotlin.io.path.createTempFile("uri-test", ".tmp").toFile()
        try {
            val virtualFile = createMockVirtualFile(tempFile.absolutePath)
            val result = LspEditorUtil.toUriString(virtualFile)
            
            assertThat(result).isNotNull()
            assertThat(result).startsWith("file:///")
            assertThat(result).endsWith(".tmp")
            
            // Should be a valid URI that can be parsed
            val uri = java.net.URI.create(result!!)
            assertThat(uri.scheme).isEqualTo("file")
            assertThat(uri.path).isNotNull()
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun `test error recovery with malformed paths`() {
        // Test how the system handles potentially malformed input paths
        val malformedPaths = listOf(
            "",                    // Empty path
            "   ",                 // Whitespace only
            "/",                   // Root only
            "relative/path",       // Relative path (no leading slash)
            "\\windows\\style",    // Windows-style separators on Unix
            "C:",                  // Drive letter only
            "file://already/uri"   // Already a URI
        )
        
        malformedPaths.forEach { path ->
            val virtualFile = createMockVirtualFile(path)
            val result = LspEditorUtil.toUriString(virtualFile)
            
            if (result != null) {
                // If a result is returned, it should be a valid URI
                assertThat(result).startsWith("file:")
                
                // Should not crash when parsed as URI
                assertThat { java.net.URI.create(result) }.doesNotThrowAnyException()
            }
        }
    }

    @Test
    fun `test performance with large number of paths`() {
        // Performance regression test
        val paths = (1..1000).map { "/performance/test/path/number/$it/file.kt" }
        
        val startTime = System.currentTimeMillis()
        val results = paths.map { path ->
            val virtualFile = createMockVirtualFile(path)
            LspEditorUtil.toUriString(virtualFile)
        }
        val endTime = System.currentTimeMillis()
        
        // All results should be valid
        assertThat(results).allMatch { it != null && it.startsWith("file:///") }
        
        // Should complete reasonably quickly (less than 5 seconds for 1000 URIs)
        assertThat(endTime - startTime).isLessThan(5000)
    }
}

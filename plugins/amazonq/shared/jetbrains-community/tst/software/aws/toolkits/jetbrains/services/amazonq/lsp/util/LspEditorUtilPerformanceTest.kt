// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package software.aws.toolkits.jetbrains.services.amazonq.lsp.util

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.ApplicationExtension
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.system.measureTimeMillis

/**
 * Performance regression tests for LspEditorUtil to ensure the URI construction fix
 * doesn't introduce performance degradation.
 */
@ExtendWith(ApplicationExtension::class)
class LspEditorUtilPerformanceTest {

    private fun createMockVirtualFile(path: String, mockProtocol: String = "file", mockIsDirectory: Boolean = false): VirtualFile =
        mockk<VirtualFile> {
            every { fileSystem } returns mockk {
                every { protocol } returns mockProtocol
            }
            every { url } returns path
            every { isDirectory } returns mockIsDirectory
        }

    @Test
    fun `performance test - single URI generation should be fast`() {
        val virtualFile = createMockVirtualFile("/performance/test/single/file.kt")
        
        val timeMs = measureTimeMillis {
            val result = LspEditorUtil.toUriString(virtualFile)
            assertThat(result).isNotNull()
            assertThat(result).startsWith("file:///")
        }
        
        // Single URI generation should take less than 10ms
        assertThat(timeMs).isLessThan(10)
    }

    @Test
    fun `performance test - batch URI generation`() {
        val paths = (1..1000).map { "/performance/test/batch/file$it.kt" }
        val virtualFiles = paths.map { createMockVirtualFile(it) }
        
        val timeMs = measureTimeMillis {
            val results = virtualFiles.map { LspEditorUtil.toUriString(it) }
            
            // Verify all results are valid
            assertThat(results).allMatch { it != null && it.startsWith("file:///") }
            assertThat(results).hasSize(1000)
        }
        
        // 1000 URI generations should take less than 1 second
        assertThat(timeMs).isLessThan(1000)
        
        // Average should be less than 1ms per URI
        val avgTimePerUri = timeMs.toDouble() / 1000
        assertThat(avgTimePerUri).isLessThan(1.0)
    }

    @Test
    fun `performance test - concurrent URI generation`() {
        val paths = (1..100).map { "/performance/test/concurrent/file$it.kt" }
        val virtualFiles = paths.map { createMockVirtualFile(it) }
        
        val timeMs = measureTimeMillis {
            val results = virtualFiles.parallelStream().map { virtualFile ->
                LspEditorUtil.toUriString(virtualFile)
            }.toList()
            
            // Verify all results are valid
            assertThat(results).allMatch { it != null && it.startsWith("file:///") }
            assertThat(results).hasSize(100)
            assertThat(results.toSet()).hasSize(100) // All unique
        }
        
        // Concurrent processing should be faster than sequential
        // and complete within 500ms for 100 URIs
        assertThat(timeMs).isLessThan(500)
    }

    @Test
    fun `performance test - complex path normalization`() {
        val complexPaths = (1..100).map { index ->
            "/very/complex/../path/with/./many/../segments/that/require/./normalization/../file$index.kt"
        }
        val virtualFiles = complexPaths.map { createMockVirtualFile(it) }
        
        val timeMs = measureTimeMillis {
            val results = virtualFiles.map { LspEditorUtil.toUriString(it) }
            
            // Verify all results are valid and normalized
            assertThat(results).allMatch { uri ->
                uri != null && 
                uri.startsWith("file:///") &&
                !uri.contains("/./") &&
                !uri.contains("/../")
            }
        }
        
        // Complex path normalization for 100 paths should take less than 200ms
        assertThat(timeMs).isLessThan(200)
    }

    @Test
    fun `performance test - paths with special characters`() {
        val specialCharPaths = (1..200).map { index ->
            "/path/with spaces/and-special#chars/file$index (copy).kt"
        }
        val virtualFiles = specialCharPaths.map { createMockVirtualFile(it) }
        
        val timeMs = measureTimeMillis {
            val results = virtualFiles.map { LspEditorUtil.toUriString(it) }
            
            // Verify all results are valid with proper encoding
            assertThat(results).allMatch { uri ->
                uri != null && 
                uri.startsWith("file:///") &&
                uri.contains("%20") && // Space encoding
                uri.contains("%28") && // ( encoding
                uri.contains("%29")    // ) encoding
            }
        }
        
        // Special character encoding for 200 paths should take less than 300ms
        assertThat(timeMs).isLessThan(300)
    }

    @Test
    fun `performance test - very long paths`() {
        val longPaths = (1..50).map { index ->
            val longSegment = "very-long-directory-name-that-simulates-deep-nesting".repeat(5)
            "/$longSegment/path$index/file$index.kt"
        }
        val virtualFiles = longPaths.map { createMockVirtualFile(it) }
        
        val timeMs = measureTimeMillis {
            val results = virtualFiles.map { LspEditorUtil.toUriString(it) }
            
            // Verify all results are valid
            assertThat(results).allMatch { it != null && it.startsWith("file:///") }
            assertThat(results).hasSize(50)
        }
        
        // Long path processing for 50 paths should take less than 100ms
        assertThat(timeMs).isLessThan(100)
    }

    @Test
    fun `performance test - repeated calls with same path`() {
        val virtualFile = createMockVirtualFile("/performance/test/repeated/file.kt")
        
        val timeMs = measureTimeMillis {
            repeat(10000) {
                val result = LspEditorUtil.toUriString(virtualFile)
                assertThat(result).isNotNull()
                assertThat(result).startsWith("file:///")
            }
        }
        
        // 10,000 repeated calls should take less than 2 seconds
        assertThat(timeMs).isLessThan(2000)
        
        // Average should be less than 0.2ms per call
        val avgTimePerCall = timeMs.toDouble() / 10000
        assertThat(avgTimePerCall).isLessThan(0.2)
    }

    @Test
    fun `performance test - directory vs file processing`() {
        val files = (1..500).map { createMockVirtualFile("/perf/files/file$it.kt", mockIsDirectory = false) }
        val directories = (1..500).map { createMockVirtualFile("/perf/dirs/dir$it", mockIsDirectory = true) }
        
        val fileTimeMs = measureTimeMillis {
            val results = files.map { LspEditorUtil.toUriString(it) }
            assertThat(results).allMatch { it != null && it.startsWith("file:///") && it.endsWith(".kt") }
        }
        
        val dirTimeMs = measureTimeMillis {
            val results = directories.map { LspEditorUtil.toUriString(it) }
            assertThat(results).allMatch { it != null && it.startsWith("file:///") && !it.endsWith("/") }
        }
        
        // Both should be fast and similar performance
        assertThat(fileTimeMs).isLessThan(500)
        assertThat(dirTimeMs).isLessThan(500)
        
        // Directory processing might be slightly faster due to no extension,
        // but should not be more than 2x different
        val ratio = maxOf(fileTimeMs.toDouble() / dirTimeMs, dirTimeMs.toDouble() / fileTimeMs)
        assertThat(ratio).isLessThan(2.0)
    }

    @Test
    fun `performance test - memory usage stability`() {
        // Test that repeated URI generation doesn't cause memory leaks
        val virtualFile = createMockVirtualFile("/performance/test/memory/file.kt")
        
        val initialMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()
        
        // Generate many URIs
        repeat(10000) {
            val result = LspEditorUtil.toUriString(virtualFile)
            assertThat(result).isNotNull()
        }
        
        // Force garbage collection
        System.gc()
        Thread.sleep(100) // Give GC time to run
        
        val finalMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()
        val memoryIncrease = finalMemory - initialMemory
        
        // Memory increase should be minimal (less than 10MB)
        assertThat(memoryIncrease).isLessThan(10 * 1024 * 1024)
    }

    @Test
    fun `performance benchmark - before vs after fix comparison`() {
        // This test documents the performance characteristics of the new implementation
        val testPaths = (1..1000).map { "/benchmark/path$it/file.kt" }
        val virtualFiles = testPaths.map { createMockVirtualFile(it) }
        
        val results = mutableListOf<String?>()
        val timeMs = measureTimeMillis {
            virtualFiles.forEach { virtualFile ->
                results.add(LspEditorUtil.toUriString(virtualFile))
            }
        }
        
        // Document performance characteristics
        println("URI Generation Performance Benchmark:")
        println("- Total URIs generated: ${results.size}")
        println("- Total time: ${timeMs}ms")
        println("- Average time per URI: ${timeMs.toDouble() / results.size}ms")
        println("- URIs per second: ${(results.size * 1000.0 / timeMs).toInt()}")
        
        // Verify all results are valid
        assertThat(results).allMatch { it != null && it.startsWith("file:///") }
        
        // Performance should be reasonable
        assertThat(timeMs).isLessThan(1000) // Less than 1 second for 1000 URIs
        assertThat(results.size * 1000.0 / timeMs).isGreaterThan(1000.0) // More than 1000 URIs/second
    }
}
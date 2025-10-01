/*******************************************************************************
 * Copyright (c) 2024 Contributors
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Migration to Foreign Function & Memory API
 *******************************************************************************/
package org.eclipse.cdt.utils.spawner;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;

import org.eclipse.cdt.utils.spawner.Spawner.IChannel;
import org.eclipse.cdt.utils.spawner.Spawner.UnixChannel;

/**
 * Native interface using Foreign Function & Memory API instead of JNI.
 * This replaces the JNI-based native methods with FFM-based implementations.
 * 
 * Requires Java 22+ where FFM API is stable (no longer preview).
 * 
 * @since 6.5
 */
class SpawnerNativeInterface {

	private static final Linker LINKER = Linker.nativeLinker();
	private static final SymbolLookup STDLIB = LINKER.defaultLookup();
	private static final MethodHandle killpg;
	private static final MethodHandle kill;
	private static final MethodHandle read;
	private static final MethodHandle write;
	private static final MethodHandle close;
	private static final MethodHandle waitpid;

	static {
		try {
			// Link to system functions using Linker.nativeLinker()

			// int killpg(pid_t pgrp, int sig)
			killpg = LINKER.downcallHandle(STDLIB.find("killpg").orElseThrow(),
					FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));

			// int kill(pid_t pid, int sig)
			kill = LINKER.downcallHandle(STDLIB.find("kill").orElseThrow(),
					FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));

			// ssize_t read(int fd, void *buf, size_t count)
			read = LINKER.downcallHandle(STDLIB.find("read").orElseThrow(), FunctionDescriptor
					.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));

			// ssize_t write(int fd, const void *buf, size_t count)
			write = LINKER.downcallHandle(STDLIB.find("write").orElseThrow(), FunctionDescriptor
					.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));

			// int close(int fd)
			close = LINKER.downcallHandle(STDLIB.find("close").orElseThrow(),
					FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));

			// pid_t waitpid(pid_t pid, int *wstatus, int options)
			waitpid = LINKER.downcallHandle(STDLIB.find("waitpid").orElseThrow(), FunctionDescriptor
					.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));

		} catch (Exception e) {
			throw new ExceptionInInitializerError(e);
		}
	}

	/**
	 * Send a signal to a process using killpg or kill.
	 * 
	 * @param processID the process ID
	 * @param sig the signal number
	 * @return 0 on success, -1 on error
	 */
	static int raise(int processID, int sig) {
		try {
			int status = (int) killpg.invokeExact(processID, sig);
			if (status == -1) {
				status = (int) kill.invokeExact(processID, sig);
			}
			return status;
		} catch (Throwable e) {
			return -1;
		}
	}

	/**
	 * Wait for a process to terminate.
	 * 
	 * @param processID the process ID to wait for
	 * @return the exit status of the process
	 */
	static int waitFor(int processID) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment statusPtr = arena.allocate(ValueLayout.JAVA_INT);
			int pid = (int) waitpid.invokeExact(processID, statusPtr, 0);
			if (pid == processID) {
				return statusPtr.get(ValueLayout.JAVA_INT, 0);
			}
			return -1;
		} catch (Throwable e) {
			return -1;
		}
	}

	/**
	 * Read data from a file descriptor.
	 * 
	 * @param channel the channel containing the file descriptor
	 * @param buf the buffer to read into
	 * @param len the maximum number of bytes to read
	 * @return the number of bytes read, or -1 on error or EOF
	 * @throws IOException if an I/O error occurs
	 */
	static int read(IChannel channel, byte[] buf, int len) throws IOException {
		if (!(channel instanceof UnixChannel)) {
			throw new IOException("Invalid channel type");
		}
		int fd = ((UnixChannel) channel).fd;

		try (Arena arena = Arena.ofConfined()) {
			MemorySegment buffer = arena.allocate(len);
			long bytesRead = (long) read.invokeExact(fd, buffer, (long) len);

			if (bytesRead == 0) {
				// EOF
				return -1;
			} else if (bytesRead < 0) {
				throw new IOException("Read error");
			}

			// Copy data from native buffer to Java array
			MemorySegment.copy(buffer, ValueLayout.JAVA_BYTE, 0, buf, 0, (int) bytesRead);

			return (int) bytesRead;
		} catch (IOException e) {
			throw e;
		} catch (Throwable e) {
			throw new IOException("Read error", e);
		}
	}

	/**
	 * Write data to a file descriptor.
	 * 
	 * @param channel the channel containing the file descriptor
	 * @param buf the buffer to write from
	 * @param len the number of bytes to write
	 * @return the number of bytes written, or -1 on error
	 * @throws IOException if an I/O error occurs
	 */
	static int write(IChannel channel, byte[] buf, int len) throws IOException {
		if (!(channel instanceof UnixChannel)) {
			throw new IOException("Invalid channel type");
		}
		int fd = ((UnixChannel) channel).fd;

		try (Arena arena = Arena.ofConfined()) {
			MemorySegment buffer = arena.allocate(len);
			// Copy data from Java array to native buffer
			MemorySegment.copy(buf, 0, buffer, ValueLayout.JAVA_BYTE, 0, len);
			long bytesWritten = (long) write.invokeExact(fd, buffer, (long) len);

			if (bytesWritten < 0) {
				throw new IOException("Write error");
			}

			return (int) bytesWritten;
		} catch (IOException e) {
			throw e;
		} catch (Throwable e) {
			throw new IOException("Write error", e);
		}
	}

	/**
	 * Close a file descriptor.
	 * 
	 * @param channel the channel containing the file descriptor
	 * @return 0 on success, -1 on error
	 */
	static int close(IChannel channel) {
		if (!(channel instanceof UnixChannel)) {
			return -1;
		}
		int fd = ((UnixChannel) channel).fd;

		try {
			return (int) close.invokeExact(fd);
		} catch (Throwable e) {
			return -1;
		}
	}

	/**
	 * Check if data is available to read (stub - not implemented in FFM version).
	 * 
	 * @param channel the channel to check
	 * @return 0 (not implemented)
	 */
	static int available(IChannel channel) {
		// This would require ioctl which is more complex
		// For now, return 0 to maintain compatibility
		return 0;
	}

	/**
	 * Configure native tracing (stub - not implemented in FFM version).
	 */
	static void configureNativeTrace(boolean spawner, boolean spawnerDetails, boolean starter, boolean readReport) {
		// Not implemented in FFM version
		// The native trace functionality would need to be reimplemented
	}
}

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
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;

import org.eclipse.cdt.utils.spawner.Spawner.IChannel;
import org.eclipse.cdt.utils.spawner.Spawner.UnixChannel;

import jdk.incubator.foreign.CLinker;
import jdk.incubator.foreign.FunctionDescriptor;
import jdk.incubator.foreign.MemoryAddress;
import jdk.incubator.foreign.MemorySegment;
import jdk.incubator.foreign.ResourceScope;
import jdk.incubator.foreign.SegmentAllocator;

/**
 * Native interface using Foreign Function & Memory API instead of JNI.
 * This replaces the JNI-based native methods with FFM-based implementations.
 * 
 * @since 6.5
 */
class SpawnerNativeInterface {

	private static final CLinker LINKER = CLinker.getInstance();
	private static final MethodHandle killpg;
	private static final MethodHandle kill;
	private static final MethodHandle read;
	private static final MethodHandle write;
	private static final MethodHandle close;
	private static final MethodHandle waitpid;

	static {
		try {
			// Link to system functions using CLinker.systemLookup()

			// int killpg(pid_t pgrp, int sig)
			MemoryAddress killpgAddr = CLinker.systemLookup().lookup("killpg").orElseThrow();
			killpg = LINKER.downcallHandle(killpgAddr, MethodType.methodType(int.class, int.class, int.class),
					FunctionDescriptor.of(CLinker.C_INT, CLinker.C_INT, CLinker.C_INT));

			// int kill(pid_t pid, int sig)
			MemoryAddress killAddr = CLinker.systemLookup().lookup("kill").orElseThrow();
			kill = LINKER.downcallHandle(killAddr, MethodType.methodType(int.class, int.class, int.class),
					FunctionDescriptor.of(CLinker.C_INT, CLinker.C_INT, CLinker.C_INT));

			// ssize_t read(int fd, void *buf, size_t count)
			MemoryAddress readAddr = CLinker.systemLookup().lookup("read").orElseThrow();
			read = LINKER.downcallHandle(readAddr,
					MethodType.methodType(long.class, int.class, MemoryAddress.class, long.class),
					FunctionDescriptor.of(CLinker.C_LONG, CLinker.C_INT, CLinker.C_POINTER, CLinker.C_LONG));

			// ssize_t write(int fd, const void *buf, size_t count)
			MemoryAddress writeAddr = CLinker.systemLookup().lookup("write").orElseThrow();
			write = LINKER.downcallHandle(writeAddr,
					MethodType.methodType(long.class, int.class, MemoryAddress.class, long.class),
					FunctionDescriptor.of(CLinker.C_LONG, CLinker.C_INT, CLinker.C_POINTER, CLinker.C_LONG));

			// int close(int fd)
			MemoryAddress closeAddr = CLinker.systemLookup().lookup("close").orElseThrow();
			close = LINKER.downcallHandle(closeAddr, MethodType.methodType(int.class, int.class),
					FunctionDescriptor.of(CLinker.C_INT, CLinker.C_INT));

			// pid_t waitpid(pid_t pid, int *wstatus, int options)
			MemoryAddress waitpidAddr = CLinker.systemLookup().lookup("waitpid").orElseThrow();
			waitpid = LINKER.downcallHandle(waitpidAddr,
					MethodType.methodType(int.class, int.class, MemoryAddress.class, int.class),
					FunctionDescriptor.of(CLinker.C_INT, CLinker.C_INT, CLinker.C_POINTER, CLinker.C_INT));

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
		try (ResourceScope scope = ResourceScope.newConfinedScope()) {
			SegmentAllocator allocator = SegmentAllocator.ofScope(scope);
			MemorySegment statusPtr = allocator.allocate(CLinker.C_INT);
			int pid = (int) waitpid.invokeExact(processID, statusPtr.address(), 0);
			if (pid == processID) {
				return statusPtr.get(CLinker.C_INT, 0);
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

		try (ResourceScope scope = ResourceScope.newConfinedScope()) {
			SegmentAllocator allocator = SegmentAllocator.ofScope(scope);
			MemorySegment buffer = allocator.allocateArray(CLinker.C_CHAR, len);
			long bytesRead = (long) read.invokeExact(fd, buffer.address(), (long) len);

			if (bytesRead == 0) {
				// EOF
				return -1;
			} else if (bytesRead < 0) {
				throw new IOException("Read error");
			}

			// Copy data from native buffer to Java array
			for (int i = 0; i < bytesRead; i++) {
				buf[i] = buffer.get(CLinker.C_CHAR, i);
			}

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

		try (ResourceScope scope = ResourceScope.newConfinedScope()) {
			SegmentAllocator allocator = SegmentAllocator.ofScope(scope);
			MemorySegment buffer = allocator.allocateArray(CLinker.C_CHAR, buf, 0, len);
			long bytesWritten = (long) write.invokeExact(fd, buffer.address(), (long) len);

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

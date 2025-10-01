/*******************************************************************************
 * Copyright (c) 2025 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.cdt.utils.spawner;

import java.io.IOException;

import org.eclipse.cdt.utils.spawner.Spawner.IChannel;

/**
 * Common interface for platform-specific spawner implementations using JNA.
 * 
 * @noreference This interface is not intended to be referenced by clients.
 * @noimplement This interface is not intended to be implemented by clients.
 * @noextend This interface is not intended to be extended by clients.
 */
interface SpawnerNative {

	/**
	 * Execute a process with the given command, environment, and working directory.
	 * 
	 * @param cmdarray command and arguments
	 * @param envp environment variables
	 * @param dir working directory
	 * @param channels output parameter for I/O channels
	 * @return process ID or -1 on error
	 * @throws IOException if execution fails
	 */
	int exec0(String[] cmdarray, String[] envp, String dir, IChannel[] channels) throws IOException;

	/**
	 * Execute a process without redirecting streams.
	 * 
	 * @param cmdarray command and arguments
	 * @param envp environment variables
	 * @param dir working directory
	 * @return process ID or -1 on error
	 * @throws IOException if execution fails
	 */
	int exec1(String[] cmdarray, String[] envp, String dir) throws IOException;

	/**
	 * Execute a process with a PTY.
	 * 
	 * @param cmdarray command and arguments
	 * @param envp environment variables
	 * @param dir working directory
	 * @param channels output parameter for I/O channels
	 * @param slaveName PTY slave name
	 * @param masterFD PTY master file descriptor
	 * @param console whether to use console mode
	 * @return process ID or -1 on error
	 * @throws IOException if execution fails
	 */
	int exec2(String[] cmdarray, String[] envp, String dir, IChannel[] channels, String slaveName, int masterFD,
			boolean console) throws IOException;

	/**
	 * Send a signal to a process.
	 * 
	 * @param processID process ID
	 * @param sig signal number
	 * @return 0 on success, -1 on error
	 */
	int raise(int processID, int sig);

	/**
	 * Wait for a process to terminate.
	 * 
	 * @param processID process ID
	 * @return exit status of the process
	 */
	int waitFor(int processID);

	/**
	 * Configure native tracing/debugging.
	 * 
	 * @param spawner enable spawner traces
	 * @param spawnerDetails enable detailed spawner traces
	 * @param starter enable starter traces
	 * @param readReport enable read report traces
	 */
	void configureNativeTrace(boolean spawner, boolean spawnerDetails, boolean starter, boolean readReport);
}

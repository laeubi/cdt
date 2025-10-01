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
import org.eclipse.cdt.utils.spawner.Spawner.UnixChannel;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.ptr.IntByReference;

/**
 * Unix/Linux implementation of SpawnerNative using JNA to call the native spawner library.
 * 
 * @noreference This class is not intended to be referenced by clients.
 */
class UnixSpawnerNative implements SpawnerNative {

	/**
	 * JNA interface to the spawner native library
	 */
	interface SpawnerLib extends Library {
		SpawnerLib INSTANCE = Native.load("spawner", SpawnerLib.class);

		int spawner_exec0(String[] cmd, String[] envp, String dir, IntByReference fd0, IntByReference fd1,
				IntByReference fd2);

		int spawner_exec1(String[] cmd, String[] envp, String dir);

		int spawner_exec2(String[] cmd, String[] envp, String dir, IntByReference fd0, IntByReference fd1,
				IntByReference fd2, String slaveName, int masterFD, boolean console);

		int spawner_raise(int pid, int sig);

		int spawner_waitFor(int pid);

		void spawner_configureTrace(boolean spawner, boolean spawnerDetails, boolean starter, boolean readReport);
	}

	@Override
	public int exec0(String[] cmdarray, String[] envp, String dir, IChannel[] channels) throws IOException {
		if (channels == null || channels.length != 3) {
			throw new IOException("Invalid channels array");
		}

		IntByReference fd0 = new IntByReference();
		IntByReference fd1 = new IntByReference();
		IntByReference fd2 = new IntByReference();

		int pid = SpawnerLib.INSTANCE.spawner_exec0(cmdarray, envp, dir, fd0, fd1, fd2);
		if (pid > 0) {
			channels[0] = new UnixChannel(fd0.getValue());
			channels[1] = new UnixChannel(fd1.getValue());
			channels[2] = new UnixChannel(fd2.getValue());
		}
		return pid;
	}

	@Override
	public int exec1(String[] cmdarray, String[] envp, String dir) throws IOException {
		return SpawnerLib.INSTANCE.spawner_exec1(cmdarray, envp, dir);
	}

	@Override
	public int exec2(String[] cmdarray, String[] envp, String dir, IChannel[] channels, String slaveName, int masterFD,
			boolean console) throws IOException {
		if (channels == null || channels.length != 3) {
			throw new IOException("Invalid channels array");
		}

		IntByReference fd0 = new IntByReference();
		IntByReference fd1 = new IntByReference();
		IntByReference fd2 = new IntByReference();

		int pid = SpawnerLib.INSTANCE.spawner_exec2(cmdarray, envp, dir, fd0, fd1, fd2, slaveName, masterFD, console);
		if (pid > 0) {
			channels[0] = new UnixChannel(fd0.getValue());
			channels[1] = new UnixChannel(fd1.getValue());
			channels[2] = new UnixChannel(fd2.getValue());
		}
		return pid;
	}

	@Override
	public int raise(int processID, int sig) {
		return SpawnerLib.INSTANCE.spawner_raise(processID, sig);
	}

	@Override
	public int waitFor(int processID) {
		return SpawnerLib.INSTANCE.spawner_waitFor(processID);
	}

	@Override
	public void configureNativeTrace(boolean spawner, boolean spawnerDetails, boolean starter, boolean readReport) {
		SpawnerLib.INSTANCE.spawner_configureTrace(spawner, spawnerDetails, starter, readReport);
	}
}


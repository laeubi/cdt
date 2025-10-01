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
import org.eclipse.cdt.utils.spawner.Spawner.WinChannel;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.WString;
import com.sun.jna.ptr.LongByReference;

/**
 * Windows implementation of SpawnerNative using JNA to call the native spawner library.
 * 
 * @noreference This class is not intended to be referenced by clients.
 */
class WindowsSpawnerNative implements SpawnerNative {

	/**
	 * JNA interface to the spawner native library
	 */
	interface SpawnerLib extends Library {
		SpawnerLib INSTANCE = Native.load("spawner", SpawnerLib.class);

		int spawner_exec0(WString[] cmd, WString[] envp, WString dir, LongByReference h0, LongByReference h1,
				LongByReference h2);

		int spawner_exec1(WString[] cmd, WString[] envp, WString dir);

		int spawner_exec2(WString[] cmd, WString[] envp, WString dir, LongByReference h0, LongByReference h1,
				LongByReference h2, WString slaveName, int masterFD, boolean console);

		int spawner_raise(int pid, int sig);

		int spawner_waitFor(int pid);

		void spawner_configureTrace(boolean spawner, boolean spawnerDetails, boolean starter, boolean readReport);
	}

	@Override
	public int exec0(String[] cmdarray, String[] envp, String dir, IChannel[] channels) throws IOException {
		if (channels == null || channels.length != 3) {
			throw new IOException("Invalid channels array");
		}

		WString[] cmdW = toWStringArray(cmdarray);
		WString[] envW = toWStringArray(envp);
		LongByReference h0 = new LongByReference();
		LongByReference h1 = new LongByReference();
		LongByReference h2 = new LongByReference();

		int pid = SpawnerLib.INSTANCE.spawner_exec0(cmdW, envW, new WString(dir), h0, h1, h2);
		if (pid > 0) {
			channels[0] = new WinChannel(h0.getValue());
			channels[1] = new WinChannel(h1.getValue());
			channels[2] = new WinChannel(h2.getValue());
		}
		return pid;
	}

	@Override
	public int exec1(String[] cmdarray, String[] envp, String dir) throws IOException {
		WString[] cmdW = toWStringArray(cmdarray);
		WString[] envW = toWStringArray(envp);
		return SpawnerLib.INSTANCE.spawner_exec1(cmdW, envW, new WString(dir));
	}

	@Override
	public int exec2(String[] cmdarray, String[] envp, String dir, IChannel[] channels, String slaveName, int masterFD,
			boolean console) throws IOException {
		if (channels == null || channels.length != 3) {
			throw new IOException("Invalid channels array");
		}

		WString[] cmdW = toWStringArray(cmdarray);
		WString[] envW = toWStringArray(envp);
		LongByReference h0 = new LongByReference();
		LongByReference h1 = new LongByReference();
		LongByReference h2 = new LongByReference();

		int pid = SpawnerLib.INSTANCE.spawner_exec2(cmdW, envW, new WString(dir), h0, h1, h2, new WString(slaveName),
				masterFD, console);
		if (pid > 0) {
			channels[0] = new WinChannel(h0.getValue());
			channels[1] = new WinChannel(h1.getValue());
			channels[2] = new WinChannel(h2.getValue());
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

	/**
	 * Convert String array to WString array for Windows Unicode support
	 */
	private WString[] toWStringArray(String[] strings) {
		if (strings == null || strings.length == 0) {
			return new WString[0];
		}

		WString[] result = new WString[strings.length];
		for (int i = 0; i < strings.length; i++) {
			result[i] = new WString(strings[i]);
		}
		return result;
	}
}


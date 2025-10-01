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

/**
 * Windows implementation of StreamNative using JNA to call the native spawner library.
 * 
 * @noreference This class is not intended to be referenced by clients.
 */
class WindowsStreamNative implements StreamNative {

	/**
	 * JNA interface to the spawner native library for I/O operations
	 */
	interface SpawnerIOLib extends Library {
		SpawnerIOLib INSTANCE = Native.load("spawner", SpawnerIOLib.class);

		int spawner_read(long handle, byte[] buf, int len);

		int spawner_write(long handle, byte[] buf, int len);

		int spawner_close(long handle);

		int spawner_available(long handle);
	}

	@Override
	public int read(IChannel channel, byte[] buf, int len) throws IOException {
		if (!(channel instanceof WinChannel)) {
			throw new IOException("Invalid channel type");
		}
		long handle = ((WinChannel) channel).handle;
		int result = SpawnerIOLib.INSTANCE.spawner_read(handle, buf, len);
		if (result < 0) {
			throw new IOException("read error");
		}
		return result;
	}

	@Override
	public int write(IChannel channel, byte[] buf, int len) throws IOException {
		if (!(channel instanceof WinChannel)) {
			throw new IOException("Invalid channel type");
		}
		long handle = ((WinChannel) channel).handle;
		int result = SpawnerIOLib.INSTANCE.spawner_write(handle, buf, len);
		if (result < 0) {
			throw new IOException("write error");
		}
		return result;
	}

	@Override
	public int close(IChannel channel) throws IOException {
		if (!(channel instanceof WinChannel)) {
			throw new IOException("Invalid channel type");
		}
		long handle = ((WinChannel) channel).handle;
		return SpawnerIOLib.INSTANCE.spawner_close(handle);
	}

	@Override
	public int available(IChannel channel) throws IOException {
		if (!(channel instanceof WinChannel)) {
			throw new IOException("Invalid channel type");
		}
		long handle = ((WinChannel) channel).handle;
		return SpawnerIOLib.INSTANCE.spawner_available(handle);
	}
}

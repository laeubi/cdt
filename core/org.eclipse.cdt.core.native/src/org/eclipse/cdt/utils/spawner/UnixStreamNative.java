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

/**
 * Unix/Linux implementation of StreamNative using JNA to call the native spawner library.
 * 
 * @noreference This class is not intended to be referenced by clients.
 */
class UnixStreamNative implements StreamNative {

	/**
	 * JNA interface to the spawner native library for I/O operations
	 */
	interface SpawnerIOLib extends Library {
		SpawnerIOLib INSTANCE = Native.load("spawner", SpawnerIOLib.class);

		int spawner_read(int fd, byte[] buf, int len);

		int spawner_write(int fd, byte[] buf, int len);

		int spawner_close(int fd);

		int spawner_available(int fd);
	}

	@Override
	public int read(IChannel channel, byte[] buf, int len) throws IOException {
		if (!(channel instanceof UnixChannel)) {
			throw new IOException("Invalid channel type");
		}
		int fd = ((UnixChannel) channel).fd;
		int result = SpawnerIOLib.INSTANCE.spawner_read(fd, buf, len);
		if (result < 0) {
			throw new IOException("read error");
		}
		return result;
	}

	@Override
	public int write(IChannel channel, byte[] buf, int len) throws IOException {
		if (!(channel instanceof UnixChannel)) {
			throw new IOException("Invalid channel type");
		}
		int fd = ((UnixChannel) channel).fd;
		int result = SpawnerIOLib.INSTANCE.spawner_write(fd, buf, len);
		if (result < 0) {
			throw new IOException("write error");
		}
		return result;
	}

	@Override
	public int close(IChannel channel) throws IOException {
		if (!(channel instanceof UnixChannel)) {
			throw new IOException("Invalid channel type");
		}
		int fd = ((UnixChannel) channel).fd;
		return SpawnerIOLib.INSTANCE.spawner_close(fd);
	}

	@Override
	public int available(IChannel channel) throws IOException {
		if (!(channel instanceof UnixChannel)) {
			throw new IOException("Invalid channel type");
		}
		int fd = ((UnixChannel) channel).fd;
		return SpawnerIOLib.INSTANCE.spawner_available(fd);
	}
}

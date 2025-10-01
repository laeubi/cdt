/*******************************************************************************
 * Copyright (c) 2000, 2014 QNX Software Systems and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     QNX Software Systems - Initial API and implementation
 *     Martin Oberhuber (Wind River) - [303083] Split out the Spawner
 *******************************************************************************/
package org.eclipse.cdt.utils.spawner;

import java.io.IOException;
import java.io.InputStream;

import org.eclipse.cdt.internal.core.natives.Messages;
import org.eclipse.cdt.utils.spawner.Spawner.IChannel;
import org.eclipse.core.runtime.Platform;

class SpawnerInputStream extends InputStream {
	private IChannel channel;
	private static final StreamNative streamImpl = createStreamImpl();

	private static StreamNative createStreamImpl() {
		if (Platform.getOS().equals(Platform.OS_WIN32)) {
			return new WindowsStreamNative();
		} else {
			return new UnixStreamNative();
		}
	}

	/**
	 * From a Unix valid file descriptor set a Reader.
	 * @param fd file descriptor.
	 */
	public SpawnerInputStream(IChannel channel) {
		this.channel = channel;
	}

	/**
	 * Implementation of read for the InputStream.
	 *
	 * @exception IOException on error.
	 */
	@Override
	public int read() throws IOException {
		byte b[] = new byte[1];
		if (1 != read(b, 0, 1))
			return -1;
		return b[0];
	}

	/**
	 * @see InputStream#read(byte[], int, int)
	 */
	@Override
	public int read(byte[] buf, int off, int len) throws IOException {
		if (channel == null) {
			return -1;
		}
		if (buf == null) {
			throw new NullPointerException();
		} else if ((off < 0) || (off > buf.length) || (len < 0) || ((off + len) > buf.length) || ((off + len) < 0)) {
			throw new IndexOutOfBoundsException();
		} else if (len == 0) {
			return 0;
		}
		byte[] tmpBuf = off > 0 ? new byte[len] : buf;

		len = streamImpl.read(channel, tmpBuf, len);
		if (len <= 0)
			return -1;

		if (tmpBuf != buf) {
			System.arraycopy(tmpBuf, 0, buf, off, len);
		}
		return len;
	}

	/**
	 * Close the Reader
	 * @exception IOException on error.
	 */
	@Override
	public void close() throws IOException {
		if (channel == null)
			return;
		int status = streamImpl.close(channel);
		if (status == -1)
			throw new IOException(Messages.Util_exception_closeError);
		channel = null;
	}

	@Override
	public int available() throws IOException {
		if (channel == null) {
			return 0;
		}
		try {
			return streamImpl.available(channel);
		} catch (UnsatisfiedLinkError e) {
			// for those platforms that do not implement available0
			return super.available();
		}
	}

	@Override
	protected void finalize() throws IOException {
		close();
	}
}


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
 * Common interface for platform-specific stream I/O implementations using JNA.
 * 
 * @noreference This interface is not intended to be referenced by clients.
 * @noimplement This interface is not intended to be implemented by clients.
 * @noextend This interface is not intended to be extended by clients.
 */
interface StreamNative {

	/**
	 * Read data from a channel.
	 * 
	 * @param channel the channel to read from
	 * @param buf buffer to read into
	 * @param len maximum number of bytes to read
	 * @return number of bytes read, or -1 on EOF/error
	 * @throws IOException if an I/O error occurs
	 */
	int read(IChannel channel, byte[] buf, int len) throws IOException;

	/**
	 * Write data to a channel.
	 * 
	 * @param channel the channel to write to
	 * @param buf buffer to write from
	 * @param len number of bytes to write
	 * @return number of bytes written
	 * @throws IOException if an I/O error occurs
	 */
	int write(IChannel channel, byte[] buf, int len) throws IOException;

	/**
	 * Close a channel.
	 * 
	 * @param channel the channel to close
	 * @return 0 on success, -1 on error
	 * @throws IOException if an I/O error occurs
	 */
	int close(IChannel channel) throws IOException;

	/**
	 * Get the number of bytes available for reading.
	 * 
	 * @param channel the channel to check
	 * @return number of bytes available
	 * @throws IOException if an I/O error occurs
	 */
	int available(IChannel channel) throws IOException;
}

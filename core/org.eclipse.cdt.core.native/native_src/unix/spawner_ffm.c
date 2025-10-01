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
 *     FFM API migration - new C interface for Foreign Function & Memory API
 *******************************************************************************/

#include <unistd.h>
#include <stdlib.h>
#include <stdio.h>
#include <signal.h>
#include <string.h>
#include <stdbool.h>

#include "exec0.h"

static bool trace_enabled = false;

/**
 * Execute a command without channels (detached mode).
 * 
 * @param cmdarray array of command and arguments (NULL-terminated)
 * @param envp array of environment variables (NULL-terminated)
 * @param dirpath working directory path
 * @return process ID on success, -1 on error
 */
int spawner_exec1_ffm(char **cmdarray, char **envp, const char *dirpath) {
    if (!cmdarray || !cmdarray[0]) {
        return -1;
    }
    
    if (trace_enabled) {
        fprintf(stderr, "spawner_exec1_ffm: command=%s, dir=%s\n", cmdarray[0], dirpath);
    }
    
    return exec0(cmdarray[0], cmdarray, envp, dirpath, NULL);
}

/**
 * Execute a command with channels (pipes for stdin/stdout/stderr).
 * 
 * @param cmdarray array of command and arguments (NULL-terminated)
 * @param envp array of environment variables (NULL-terminated)
 * @param dirpath working directory path
 * @param channels array to receive file descriptors [stdin, stdout, stderr]
 * @return process ID on success, -1 on error
 */
int spawner_exec0_ffm(char **cmdarray, char **envp, const char *dirpath, int channels[3]) {
    if (!cmdarray || !cmdarray[0] || !channels) {
        return -1;
    }
    
    if (trace_enabled) {
        fprintf(stderr, "spawner_exec0_ffm: command=%s, dir=%s\n", cmdarray[0], dirpath);
    }
    
    return exec0(cmdarray[0], cmdarray, envp, dirpath, channels);
}

/**
 * Execute a command with PTY support.
 * 
 * @param cmdarray array of command and arguments (NULL-terminated)
 * @param envp array of environment variables (NULL-terminated)
 * @param dirpath working directory path
 * @param channels array to receive file descriptors [stdin, stdout, stderr]
 * @param slaveName PTY slave name
 * @param masterFD PTY master file descriptor
 * @param console whether to use console mode
 * @return process ID on success, -1 on error
 */
int spawner_exec2_ffm(char **cmdarray, char **envp, const char *dirpath, 
                      int channels[3], const char *slaveName, int masterFD, bool console) {
    if (!cmdarray || !cmdarray[0] || !channels) {
        return -1;
    }
    
    if (trace_enabled) {
        fprintf(stderr, "spawner_exec2_ffm: command=%s, dir=%s, pty=%s\n", 
                cmdarray[0], dirpath, slaveName);
    }
    
    return exec_pty(cmdarray[0], cmdarray, envp, dirpath, channels, slaveName, masterFD, console);
}

/**
 * Configure native trace output.
 */
void spawner_configure_trace_ffm(bool spawner, bool spawnerDetails, bool starter, bool readReport) {
    if (spawner) {
        trace_enabled = true;
        fprintf(stderr, "spawner_configure_trace_ffm: tracing enabled\n");
    }
}

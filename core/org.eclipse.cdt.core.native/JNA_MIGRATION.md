# JNI to JNA Migration for Spawner

This document describes the migration from Java Native Interface (JNI) to Java Native Access (JNA) for the Spawner implementation.

## Overview

The Spawner class and related I/O classes previously used JNI to call native code. This has been migrated to use JNA, which offers several advantages:
- No need to compile JNI glue code for each platform
- Simpler native function signatures
- Automatic marshaling of basic types
- Better error handling

## Architecture

### Java Layer

The Java code has been refactored to use platform-specific JNA implementations:

```
Spawner.java
    ├─> SpawnerNative (interface)
    │   ├─> UnixSpawnerNative (Unix/Linux/Mac implementation)
    │   └─> WindowsSpawnerNative (Windows implementation)
    │
SpawnerInputStream/SpawnerOutputStream.java
    ├─> StreamNative (interface)
    │   ├─> UnixStreamNative (Unix/Linux/Mac implementation)
    │   └─> WindowsStreamNative (Windows implementation)
```

The platform selection is done at runtime based on `Platform.getOS()`.

### Native Layer

The C code now exposes JNA-compatible functions with standard C calling conventions instead of JNI conventions:

#### Unix/Linux Functions (in spawner.c and io.c)
```c
// Process management
int spawner_exec0(char **cmd, char **envp, const char *dir, int *fd0, int *fd1, int *fd2);
int spawner_exec1(char **cmd, char **envp, const char *dir);
int spawner_exec2(char **cmd, char **envp, const char *dir, int *fd0, int *fd1, int *fd2,
                  const char *pts_name, int masterFD, bool console);
int spawner_raise(int pid, int sig);
int spawner_waitFor(int pid);
void spawner_configureTrace(bool spawner, bool spawnerDetails, bool starter, bool readReport);

// I/O operations
int spawner_read(int fd, char *buf, int len);
int spawner_write(int fd, const char *buf, int len);
int spawner_close(int fd);
int spawner_available(int fd);
```

#### Windows Functions (in Win32ProcessEx.c and iostream.c)
```c
// Process management
int spawner_exec0(wchar_t **cmd, wchar_t **envp, const wchar_t *dir, long long *h0, long long *h1, long long *h2);
int spawner_exec1(wchar_t **cmd, wchar_t **envp, const wchar_t *dir);
int spawner_exec2(wchar_t **cmd, wchar_t **envp, const wchar_t *dir, long long *h0, long long *h1, long long *h2,
                  const wchar_t *slaveName, int masterFD, bool console);
int spawner_raise(int pid, int sig);
int spawner_waitFor(int pid);
void spawner_configureTrace(bool spawner, bool spawnerDetails, bool starter, bool readReport);

// I/O operations
int spawner_read(long long handle, char *buf, int len);
int spawner_write(long long handle, const char *buf, int len);
int spawner_close(long long handle);
int spawner_available(long long handle);
```

## Implementation Status

### ✅ Complete
- **Java JNA interfaces**: All platform-specific JNA implementations are complete
- **Unix C wrappers**: All wrapper functions are implemented and tested
- **Windows I/O wrappers**: I/O functions (read, write, close, available) are complete
- **Configuration**: configureTrace wrapper is complete for both platforms

### ⚠️ Partial (Windows Process Management)
The Windows process management functions (`spawner_exec0`, `spawner_exec1`, `spawner_exec2`, `spawner_raise`, `spawner_waitFor`) currently have placeholder implementations in `Win32ProcessEx.c`.

These functions need to be completed by:
1. Extracting the process creation logic from the existing JNI `Java_org_eclipse_cdt_utils_spawner_Spawner_exec0` function
2. Creating standalone functions that can be called from JNA
3. Properly handling the internal `pProcInfo_t` structures and events
4. Ensuring proper handle/channel return values

Reference the existing JNI implementations in Win32ProcessEx.c:
- Lines 327-527: `Java_org_eclipse_cdt_utils_spawner_Spawner_exec0` 
- Lines 528-602: `Java_org_eclipse_cdt_utils_spawner_Spawner_exec1`
- Lines 101-326: `Java_org_eclipse_cdt_utils_spawner_Spawner_exec2`
- Lines 604-678: `Java_org_eclipse_cdt_utils_spawner_Spawner_raise`
- Lines 688-716: `Java_org_eclipse_cdt_utils_spawner_Spawner_waitFor`

## Migration Benefits

1. **No JNI Glue Code**: JNA automatically handles the marshaling between Java and native code
2. **Simpler Maintenance**: No need to regenerate JNI headers when Java method signatures change
3. **Platform Detection**: Automatic selection of the correct platform implementation at runtime
4. **Error Handling**: Better exception handling through JNA's error conversion

## Building

The native libraries still need to be built using the existing build process:
```bash
cd native_src
make rebuild
```

Or using Maven with the appropriate profile:
```bash
mvn clean install -Dnative=all
```

## Testing

After completing the Windows C wrapper implementations, testing should include:
1. Process spawning with and without PTY
2. Process signaling (TERM, KILL, INT, etc.)
3. Stream I/O (read, write, close operations)
4. Process lifecycle (wait, exit status)
5. All platforms (Linux, Windows, macOS)

## Dependencies

The JNA library is already included as an optional dependency in the MANIFEST.MF:
```
Require-Bundle: ...
 com.sun.jna;bundle-version="[5.17.0,6.0.0)";resolution:=optional,
 com.sun.jna.platform;bundle-version="[5.17.0,6.0.0)";resolution:=optional
```

## Notes

- The old JNI methods have been removed from the Java classes
- `System.loadLibrary("spawner")` calls have been removed - JNA handles loading
- The native library name is still "spawner" and is loaded by JNA
- Channel types (`UnixChannel` with `fd` and `WinChannel` with `handle`) remain unchanged

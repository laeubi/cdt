# JNI to JNA Migration - Implementation Summary

## Overview
This document summarizes the migration of the Spawner implementation from Java Native Interface (JNI) to Java Native Access (JNA).

## Goals Achieved
✅ Replace JNI with JNA for calling native code  
✅ Support both Windows and Unix/Linux platforms  
✅ Maintain existing API compatibility  
✅ Minimize changes to existing code

## Changes Made

### 1. Java Code Changes

#### New Files Created:
1. **`SpawnerNative.java`** - Interface defining platform-agnostic spawner operations
   - exec0, exec1, exec2, raise, waitFor, configureNativeTrace

2. **`UnixSpawnerNative.java`** - Unix/Linux implementation using JNA
   - Calls native spawner library functions
   - Uses `IntByReference` for file descriptors
   - Creates `UnixChannel` objects

3. **`WindowsSpawnerNative.java`** - Windows implementation using JNA
   - Calls native spawner library functions
   - Uses `LongByReference` for handles
   - Uses `WString` for Unicode support
   - Creates `WinChannel` objects

4. **`StreamNative.java`** - Interface defining platform-agnostic I/O operations
   - read, write, close, available

5. **`UnixStreamNative.java`** - Unix/Linux I/O implementation
   - Direct JNA calls to spawner library
   - Works with `UnixChannel` (file descriptors)

6. **`WindowsStreamNative.java`** - Windows I/O implementation
   - Direct JNA calls to spawner library
   - Works with `WinChannel` (handles)

#### Modified Files:
1. **`Spawner.java`**
   - Removed all `native` method declarations
   - Removed `System.loadLibrary("spawner")` call
   - Added `nativeImpl` field (SpawnerNative instance)
   - Changed methods to call `nativeImpl` instead of native methods
   - Platform selection done in `createNativeImpl()`

2. **`SpawnerInputStream.java`**
   - Removed all `native` method declarations
   - Removed `System.loadLibrary("spawner")` call
   - Added `streamImpl` field (StreamNative instance)
   - Changed methods to call `streamImpl` instead of native methods

3. **`SpawnerOutputStream.java`**
   - Removed all `native` method declarations
   - Removed `System.loadLibrary("spawner")` call
   - Added `streamImpl` field (StreamNative instance)
   - Changed methods to call `streamImpl` instead of native methods

### 2. Unix/Linux Native Code Changes

#### Modified Files:
1. **`native_src/unix/spawner.c`**
   - Added JNA wrapper functions:
     - `int spawner_exec0(char **cmd, char **envp, const char *dir, int *fd0, int *fd1, int *fd2)`
     - `int spawner_exec1(char **cmd, char **envp, const char *dir)`
     - `int spawner_exec2(..., const char *pts_name, int masterFD, bool console)`
     - `int spawner_raise(int pid, int sig)`
     - `int spawner_waitFor(int pid)`
     - `void spawner_configureTrace(...)`

2. **`native_src/unix/io.c`**
   - Added JNA wrapper functions:
     - `int spawner_read(int fd, char *buf, int len)`
     - `int spawner_write(int fd, const char *buf, int len)`
     - `int spawner_close(int fd)`
     - `int spawner_available(int fd)`

**Status**: ✅ COMPLETE - All Unix wrapper functions are fully implemented

### 3. Windows Native Code Changes

#### Modified Files:
1. **`native_src/win/Win32ProcessEx.c`**
   - Added JNA wrapper function declarations:
     - `int spawner_exec0(wchar_t **cmd, wchar_t **envp, const wchar_t *dir, long long *h0, long long *h1, long long *h2)` - ⚠️ TODO
     - `int spawner_exec1(wchar_t **cmd, wchar_t **envp, const wchar_t *dir)` - ⚠️ TODO
     - `int spawner_exec2(...)` - ⚠️ TODO
     - `int spawner_raise(int uid, int sig)` - ✅ COMPLETE
     - `int spawner_waitFor(int uid)` - ✅ COMPLETE
     - `void spawner_configureTrace(...)` - ✅ COMPLETE

2. **`native_src/win/iostream.c`**
   - Added JNA wrapper functions:
     - `int spawner_read(long long handle, char *buf, int len)` - ✅ COMPLETE
     - `int spawner_write(long long handle, const char *buf, int len)` - ✅ COMPLETE
     - `int spawner_close(long long handle)` - ✅ COMPLETE
     - `int spawner_available(long long handle)` - ✅ COMPLETE

**Status**: 
- ✅ COMPLETE: I/O operations, raise, waitFor, configureTrace
- ⚠️ TODO: Process creation functions (exec0, exec1, exec2)

### 4. Documentation
- **`JNA_MIGRATION.md`** - Comprehensive migration guide
- **`IMPLEMENTATION_SUMMARY.md`** - This file

## Key Architectural Changes

### Before (JNI):
```
Java Code (Spawner.java)
    └─> native methods (JNI conventions)
        └─> spawner.dll/spawner.so (JNI exports)
            └─> Internal C functions
```

### After (JNA):
```
Java Code (Spawner.java)
    └─> SpawnerNative interface
        ├─> UnixSpawnerNative (for Unix/Linux)
        │   └─> JNA calls to spawner.so
        │       └─> spawner_* functions (C ABI)
        │           └─> Internal C functions
        │
        └─> WindowsSpawnerNative (for Windows)
            └─> JNA calls to spawner.dll
                └─> spawner_* functions (C ABI)
                    └─> Internal C functions
```

## Benefits of Migration

1. **No JNI Headers**: Don't need to run `javah` or maintain JNI header files
2. **Simpler Marshaling**: JNA automatically handles basic type conversions
3. **Runtime Platform Selection**: Java code selects implementation at runtime
4. **Better Error Handling**: JNA provides better exception handling
5. **Easier Maintenance**: Standard C functions instead of JNI conventions

## Remaining Work

### Critical (for Windows):
1. **Implement `spawner_exec0`** in Win32ProcessEx.c
   - Extract logic from `Java_org_eclipse_cdt_utils_spawner_Spawner_exec0` (lines 327-527)
   - Remove JNI-specific code (JNIEnv, jobject, etc.)
   - Create helper function for command line processing
   - Return handles via pointer parameters

2. **Implement `spawner_exec1`** in Win32ProcessEx.c
   - Extract logic from `Java_org_eclipse_cdt_utils_spawner_Spawner_exec1` (lines 528-602)
   - Similar to exec0 but without channel creation

3. **Implement `spawner_exec2`** in Win32ProcessEx.c
   - Extract logic from `Java_org_eclipse_cdt_utils_spawner_Spawner_exec2` (lines 101-326)
   - Handle PTY-specific parameters

### Optional:
1. **Update build configuration** to explicitly export JNA functions
2. **Add tests** for the new JNA implementations
3. **Performance testing** to ensure no regression

## Testing Strategy

Once Windows exec functions are complete:

1. **Unit Tests**: Test each wrapper function independently
2. **Integration Tests**: Test full process lifecycle
3. **Platform Tests**: Run on Windows, Linux, and macOS
4. **Regression Tests**: Ensure existing CDT functionality works

## Migration Checklist

- [x] Create JNA interface definitions
- [x] Implement Unix JNA bindings (Java)
- [x] Implement Windows JNA bindings (Java)
- [x] Update Spawner.java to use JNA
- [x] Update Stream classes to use JNA
- [x] Implement Unix C wrappers
- [x] Implement Windows I/O C wrappers
- [x] Implement Windows process management (partial)
- [ ] Complete Windows exec functions
- [ ] Update build system
- [ ] Add/update tests
- [ ] Performance validation

## Notes

- The `spawner` library name is unchanged - JNA loads it automatically
- Channel types (`UnixChannel`, `WinChannel`) are unchanged
- The existing JNI functions remain in the C code but are unused
- Once migration is complete, JNI functions can be removed

## References

- JNA Documentation: https://github.com/java-native-access/jna
- Existing JNA usage: `org.eclipse.cdt.utils.pty.ConPTY` (reference implementation)
- Original issue: Migration from JNI to JNA for Spawner

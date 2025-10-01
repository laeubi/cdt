# Foreign Function & Memory API Migration

## Overview

This document describes the migration of Spawner.java from JNI (Java Native Interface) to FFM (Foreign Function & Memory API) for calling native system functions.

## Migration Status

### Migrated to FFM ✅

The following functions have been migrated from JNI to FFM API:

1. **`raise(int processID, int sig)`** - Send POSIX signals to processes
   - Calls system `killpg()` and `kill()` functions
   - Used for interrupt, terminate, kill operations

2. **`waitFor(int processID)`** - Wait for process termination
   - Calls system `waitpid()` function
   - Returns process exit status

3. **I/O Operations** in `SpawnerInputStream` and `SpawnerOutputStream`:
   - **`read()`** - Read from file descriptors
   - **`write()`** - Write to file descriptors
   - **`close()`** - Close file descriptors
   - All use direct system calls via FFM

### Remaining JNI Functions

The following complex functions remain as JNI due to their intricate process spawning logic:

- `exec0()` - Execute process with pipes (stdin/stdout/stderr)
- `exec1()` - Execute detached process
- `exec2()` - Execute process with PTY support

These functions involve fork, pipe, execve, and PTY manipulation which would require significant rewriting to implement purely in Java via FFM.

## Technical Details

### Requirements

- **Java 22+** (FFM API is stable, no preview flags needed)
- **Runtime Flags**:
  - `--enable-native-access=ALL-UNNAMED` - Allow native memory access

**Note**: For Java 21, the FFM API is still in preview and requires the `--enable-preview` flag.

### API Changes

| Before (JNI) | After (FFM) |
|--------------|-------------|
| `jdk.incubator.foreign` (Java 17) | `java.lang.foreign` (Java 22+) |
| `CLinker.getInstance()` | `Linker.nativeLinker()` |
| `CLinker.systemLookup()` | `Linker.nativeLinker().defaultLookup()` |
| `ResourceScope` | `Arena` |
| `SegmentAllocator` | Part of `Arena` |
| JNI header files (`org_eclipse_cdt_*.h`) | No longer needed for migrated functions |

### Implementation Details

**SpawnerNativeInterface** is the new FFM-based native interface that:

1. Links directly to system C library functions at class initialization
2. Uses `MethodHandle` for type-safe native function calls
3. Manages native memory with `Arena` for automatic cleanup
4. Handles memory copying between Java byte arrays and native buffers

Example of system call binding:

```java
// Lookup system function
MemoryAddress killAddr = Linker.nativeLinker()
    .defaultLookup()
    .find("kill")
    .orElseThrow();

// Create downcall handle with function signature
kill = Linker.nativeLinker().downcallHandle(
    killAddr,
    FunctionDescriptor.of(
        ValueLayout.JAVA_INT,    // return type
        ValueLayout.JAVA_INT,    // pid parameter
        ValueLayout.JAVA_INT     // signal parameter
    )
);

// Invoke native function
int result = (int) kill.invokeExact(pid, sig);
```

### Benefits of FFM Migration

1. **Type Safety**: Compile-time checking of native function signatures
2. **Memory Safety**: Automatic resource management with Arena
3. **Performance**: Reduced overhead compared to JNI
4. **Maintainability**: No need for JNI glue code and header files
5. **Future-proof**: FFM API became stable in Java 22

### Limitations

1. **Native Access Permission**: Requires `--enable-native-access` at runtime
2. **Platform-Specific**: Currently only implemented for Unix/Linux
3. **Partial Migration**: Complex exec functions still use JNI

**Java 21 Users**: If using Java 21, the FFM API is in preview and requires the `--enable-preview` flag in addition to the above requirements.

## Build Configuration

### Maven Configuration

For Java 22+, no special compiler arguments are needed as FFM is stable.

For Java 21, you would need to add `--enable-preview`:

```xml
<build>
    <plugins>
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-compiler-plugin</artifactId>
            <configuration>
                <compilerArgs>
                    <arg>--enable-preview</arg>
                </compilerArgs>
            </configuration>
        </plugin>
    </plugins>
</build>
```

### Manifest Configuration

Updated `MANIFEST.MF`:

```
Bundle-RequiredExecutionEnvironment: JavaSE-22
```

## Testing

To test the FFM implementation:

```bash
# Build with Java 22+
export JAVA_HOME=/path/to/java-22
mvn clean compile -pl core/org.eclipse.cdt.core.native

# Run with required flags (Java 22+)
java --enable-native-access=ALL-UNNAMED ...

# For Java 21 (preview):
java --enable-preview --enable-native-access=ALL-UNNAMED ...
```

## Future Work

### Complete FFM Migration

To fully eliminate JNI, the exec functions would need to be reimplemented to:

1. Replace C native code with Java code calling system functions via FFM:
   - `fork()` / `vfork()` / `posix_spawn()`
   - `pipe()`
   - `dup2()`
   - `execve()`
   - PTY functions (`openpty()`, `grantpt()`, etc.)

2. Handle process group management
3. Manage environment variables and working directory
4. Set up signal handling

This is a substantial undertaking but would make the code more maintainable and portable.

### Platform Support

Current FFM implementation is Unix/Linux only. Windows support would require:

- Binding to Windows API functions
- Different function signatures and calling conventions
- Handling of Windows HANDLEs instead of file descriptors

## References

- [JEP 454: Foreign Function & Memory API (Second Preview)](https://openjdk.org/jeps/454) - Java 21
- [JEP 442: Foreign Function & Memory API (Third Preview)](https://openjdk.org/jeps/442) - Java 20  
- [JEP 434: Foreign Function & Memory API (Second Preview)](https://openjdk.org/jeps/434) - Java 19
- [JEP 424: Foreign Function & Memory API (Preview)](https://openjdk.org/jeps/424) - Java 19
- [JEP 454: Foreign Function & Memory API](https://openjdk.org/jeps/454) - **Stable in Java 22**
- [Foreign Function & Memory API Tutorial](https://docs.oracle.com/en/java/javase/22/core/foreign-function-and-memory-api.html)

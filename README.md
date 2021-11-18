# ParameterRemapper
Remap (Rename) parameter name or local variable name using mappings file.

## mappings file
format:
```text
<class> <method name> <method signature> <remap info>
```

### example

source code:
```java
package test;

public class MemoryUtils {
    public void writeObject(boolean dummy, long foo, long bar) {
        // write at memory address [foo] with value [bar]
        // parameter [dummy] is ignored, just for example
    }
}
```

mapping file:
```text
test/MemoryUtils writeObject (ZJJ)V foo->address bar->value
```

will generate this file:
```java
package test;

public class MemoryUtils {
    public void writeObject(boolean dummy, long address, long value) {
        // write at memory address [address] with value [value]
    }
}
```

- `test/MemoryUtils` is class
- `writeObject` is method name
- `(JJ)V` is method signature
- `foo->address bar->value` is remap info
  - `foo->address` - replace a local variable named `foo` with `address`
  - `bar->value` - replace a local variable named `bar` with `value`

remap info can be these values:
- find by old name: `oldName->newName`
- find by param index that is starting from 0: `->newName`
- skip param index: `->`

so this mapping file will do exact same as above:
```text
test/MemoryUtils writeObject (ZJJ)V -> ->address bar->value
```
- `->address ->value` is remap info
  - `->` - skips the first local variable
  - `->address` - set second local variable name to `address`
  - `bar->value` - replace a local variable named `bar` with `value`

## options
- `--help` - shows help message
- `--debug` - enable debug log
- `--verbose` - enable verbose log
- `--mapping-file=path/to/mappings.txt` - specify mappings file, defaults to `mappings.txt`
- `--input-file=path/to/input.jar` - specify input file, defaults to `input.jar`
- `--output-file=path/to/output.jar` - specify output file, defaults to `remapped.jar`
- `--threads=8` - numbers of threads to process classes

## building
just run `./gradlew build`

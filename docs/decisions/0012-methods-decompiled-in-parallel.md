# 0012: A class's methods are decompiled in parallel

**Status:** accepted

## Context

FernFlower decompiles a class's methods one after another on one thread. The largest classes of game clients have
hundreds of methods and take 20 to 30 seconds, nearly all of it in the methods, while the decompiler's JVM
(decision 0011) leaves the other processors idle. Each method's code is decompiled from its own bytecode into its
own statements and variables; the class's other methods are only used afterwards, when FernFlower finishes the
class and writes it.

## Decision

Four more FernFlower classes are patched the way decision 0010 describes, with their diffs in `patches/fernflower`:

- `ClassWrapper.init` decompiles the code of the class's methods on up to one thread per processor, each in a
  context of its own, then finishes each method in order on the calling thread as before: its log messages, its
  variable names and its place in the class.
- `DecompilerContext` makes that context: it shares the class's options, classes and logger, and has the method's
  own variables, counters and copy of the import collector.
- `ImportCollector` makes that copy and records the class names a method's code adds. They are added to the class's
  collector method by method, in order, which leaves it as decompiling the methods one after another would; a
  name's first use decides whether it is imported.
- `LazyLoader` finds where every method's code is in the class file in one pass. It read the class up to the method,
  constant pool included, for each method, which took a tenth of the time on classes with hundreds of methods.

`JetBrainsDecompilerService` gives FernFlower a time limit per method that counts each thread's processor time, 30
seconds of it. FernFlower's own keeps one start time for the whole class and counts time on the clock, and methods
decompiled together slow each other down: the largest method of a game client took 12 seconds on the clock instead
of 4 alone, and the largest methods together went past the old 15-second limit and were left out. The service also
runs FernFlower in memory, reading the class from its bytes and taking the Java from the result saver, instead of
writing a JAR to a temporary folder and unpacking FernFlower's.

## Consequences

- The Java is the same as decompiling the methods one at a time: the classes of a game client, decompiled both ways,
  give the same text.
- The largest class of a game client takes about 8 to 12 seconds on 10 processors instead of 20, and is held up by its
  longest method. Methods decompiled together slow each other down, about three times on an M1 Pro, since FernFlower
  is limited by memory more than by processors. For that reason the largest methods aren't started first: together
  they slowed each other down more than starting the longest one late cost.
- The worker uses more memory at once, since several methods are decompiled at the same time; its 1 GB heap is
  still enough for the largest classes found so far.
- Code reached while decompiling a method must not change state the class's methods share. FernFlower's only such
  change, hiding a field of a class literal, goes to a synchronized set. A FernFlower upgrade must be checked for new
  ones: `bin/check-patches upgrade` carries the diffs over, but not the reasoning.

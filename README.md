# loom-fiber
Continuation & Fiber examples using the OpenJDK project Loom prototype

## How to build

First you need to get pro, the simplest solution is to use the pro_wrapper,
so grab a version of (http://jdk.java.net/)[jdk 11 or 12] and type
```
  export PRO_SPECIAL_BUILD='early-access-loom'
  /path/to/jdk/bin/java pro_wrapper
```

It should install a version of pro compatible with loom in a local directory named 'pro' and run the build

If you want to re-run the build, you can either re-run pro_wrapper or call directly pro like this
```
  ./pro/bin/pro
```

## What is a continuation and what is a fiber

A Continuation is a stack of function calls that can be stopped at some point (with yield) and restarted afterward (with run).

A Fiber is a continuation that runs on a thread pool (java.util.concurrent.Executor) so unlike a continuation, a fiber doesn't run on the same thread
as the code that execute it. Unlike an usual executor, when a fiber do a blocking call (on IO, lock, condition, sleep, etc) it doesn't block the underlying thread,
the fiber is stopped and another one can be scheduled on the same thread. When the result of the blocking call arrived, the fiber is restarted once the same thread
that starts the fiber is free to be used.

The go-routine of golang are fibers.

## Examples using the project Loom

### Continuations

- Generators,
  an Iterator and a Stream using a continuation.
- EventContinuation,
  allows to send values back and forth between a code and a continuation.


### Fibers

- Task,
  a simple async/await mechanism like in JavaScript, C# or Kotlin using a fiber. Note that unlike these languages, fibers are managed by the VM not by the compiler.
- YetAnotherExecutors,
  yet another executor using fibers.

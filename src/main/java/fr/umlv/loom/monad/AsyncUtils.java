package fr.umlv.loom.monad;

import fr.umlv.loom.monad.AsyncMonad.DeadlineException;
import fr.umlv.loom.monad.AsyncMonad.ExceptionHandler;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Spliterator;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

final class AsyncUtils {

}

package fr.umlv.loom.structured;

/**
 * A callable that propagates the checked exceptions
 *
 * @param <T> type of the result
 * @param <E> type of the checked exception, uses {@code RuntimeException} otherwise.
 */
@FunctionalInterface
public interface Invokable<T, E extends Exception> {
  /**
   * Compute the computation.
   *
   * @return a result
   * @throws E an exception
   * @throws InterruptedException if the computation is interrupted or cancelled
   */
  T invoke() throws E, InterruptedException;
}

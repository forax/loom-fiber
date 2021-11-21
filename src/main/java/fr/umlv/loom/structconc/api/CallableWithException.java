package fr.umlv.loom.structconc.api;

public interface CallableWithException<V, X extends Exception> {
  V call() throws X;
}

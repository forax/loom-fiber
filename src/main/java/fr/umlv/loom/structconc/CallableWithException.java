package fr.umlv.loom.structconc;

public interface CallableWithException<V, X extends Exception> {
  V call() throws X;
}

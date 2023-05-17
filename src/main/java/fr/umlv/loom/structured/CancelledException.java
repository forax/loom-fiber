package fr.umlv.loom.structured;

public final class CancelledException extends RuntimeException {
  public CancelledException() {
    super(null, null, false, false);
  }
}

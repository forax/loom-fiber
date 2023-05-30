package fr.umlv.loom.example;

// $JAVA_HOME/bin/java --enable-preview -cp target/classes  fr.umlv.loom.example._7_scoped_value
public class _7_scoped_value {
  private static final ScopedValue<String> USER = ScopedValue.newInstance();

  private static void sayHello() {
    System.out.println("Hello " + USER.get());
  }

  public static void main(String[] args) throws InterruptedException {
    var vthread = Thread.ofVirtual()
        .start(() -> {
          ScopedValue.runWhere(USER, "Bob", () -> {
            sayHello();
          });
        });

    vthread.join();
  }
}

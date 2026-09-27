package myau;

/**
 * Injection-mode alias: the inject bootstrap (myau.inject.*) instantiates
 * myau.Myau; we forward to the real client OpenMyau so the injection
 * payload is exactly this jar's client.
 */
public class Myau extends OpenMyau {
    public Myau() {
        super();
    }
}
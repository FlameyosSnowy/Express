package me.flame.express.errors;

public class AttemptFailedException extends Exception {
    public AttemptFailedException(Throwable exception) {
        super(exception);
    }

    public AttemptFailedException(String message) {
        super(message);
    }
}

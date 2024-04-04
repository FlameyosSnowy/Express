package me.flame.express.errors;

public class ResultException extends Exception {
    public ResultException(Throwable throwable) {
        super(throwable);
    }

    public ResultException(String message) {
        super(message);
    }
}

package software.amazon.smithy.java.sparrowhawk;

public final class ParseException extends RuntimeException {
    public ParseException(String message) {
        this(message, false);
    }

    public ParseException(String message, boolean stackTrace) {
        super(message);
        if (stackTrace) {
            super.fillInStackTrace();
        }
    }

    @Override
    public Throwable fillInStackTrace() {
        return this;
    }
}

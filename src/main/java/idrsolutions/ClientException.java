package idrsolutions;

public class ClientException extends Exception {

    public ClientException(final String exceptionMessage, final Throwable error) {
        super(exceptionMessage, error);
    }

    public ClientException(final String exceptionMessage) {
        super(exceptionMessage);
    }
}

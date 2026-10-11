// src/main/java/com/printops/demo/exception/InvalidInviteException.java
package com.printops.demo.exception;

// Código de invitación que no se puede usar al registrarse. El motivo viaja al
// cliente como `code` para que muestre la pantalla adecuada.
public class InvalidInviteException extends RuntimeException {

    public enum Reason { NOT_FOUND, USED, EXPIRED, WRONG_EMAIL }

    private final Reason reason;

    public InvalidInviteException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }
}

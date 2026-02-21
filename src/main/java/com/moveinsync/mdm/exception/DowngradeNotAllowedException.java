package com.moveinsync.mdm.exception;

public class DowngradeNotAllowedException extends RuntimeException {
    public DowngradeNotAllowedException(String message) {
        super(message);
    }
}

//--------------------------------------------------
// Class UserSubjectiveException
//--------------------------------------------------
// Written by Kenvix <i@kenvix.com>
//--------------------------------------------------

package com.kenvix.natpoked.contacts.exception;

public class UserInvalidUsageException extends UserViolationException {

    public UserInvalidUsageException(int code) {
        super(code);
    }

    public UserInvalidUsageException(String message, int code) {
        super(message, code);
    }

    public UserInvalidUsageException(String message, Throwable cause) {
        super(message, cause);
    }

    public UserInvalidUsageException(Throwable cause, int code) {
        super(cause, code);
    }

    public UserInvalidUsageException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}

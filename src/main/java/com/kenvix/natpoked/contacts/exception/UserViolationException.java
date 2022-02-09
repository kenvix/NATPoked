//--------------------------------------------------
// Class UserSubjectiveException
//--------------------------------------------------
// Written by Kenvix <i@kenvix.com>
//--------------------------------------------------

package com.kenvix.natpoked.contacts.exception;

import com.kenvix.utils.exception.CommonBusinessException;

public class UserViolationException extends CommonBusinessException {

    public UserViolationException(int code) {
        super(code);
    }

    public UserViolationException(String message, int code) {
        super(message, code);
    }

    public UserViolationException(String message, Throwable cause) {
        super(message, cause);
    }

    public UserViolationException(Throwable cause, int code) {
        super(cause, code);
    }

    public UserViolationException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}

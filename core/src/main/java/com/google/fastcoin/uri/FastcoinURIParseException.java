package com.google.fastcoin.uri;

/**
 * <p>Exception to provide the following to {@link FastcoinURI}:</p>
 * <ul>
 * <li>Provision of parsing error messages</li>
 * </ul>
 * <p>This base exception acts as a general failure mode not attributable to a specific cause (other than
 * that reported in the exception message). Since this is in English, it may not be worth reporting directly
 * to the user other than as part of a "general failure to parse" response.</p>
 *
 * @since 0.4.0
 */
public class FastcoinURIParseException extends RuntimeException {
    public FastcoinURIParseException(String s) {
        super(s);
    }

    public FastcoinURIParseException(String s, Throwable throwable) {
        super(s, throwable);
    }
}

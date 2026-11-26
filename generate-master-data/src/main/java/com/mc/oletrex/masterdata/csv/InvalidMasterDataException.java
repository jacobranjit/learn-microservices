package com.mc.oletrex.masterdata.csv;

/** A CSV file is missing or a row cannot be parsed. */
public class InvalidMasterDataException extends RuntimeException {

    public InvalidMasterDataException(String message) {
        super(message);
    }

    public InvalidMasterDataException(String message, Throwable cause) {
        super(message, cause);
    }
}

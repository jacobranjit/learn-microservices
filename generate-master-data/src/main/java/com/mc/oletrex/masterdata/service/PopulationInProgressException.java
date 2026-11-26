package com.mc.oletrex.masterdata.service;

public class PopulationInProgressException extends RuntimeException {

    public PopulationInProgressException() {
        super("Master data is already being populated; wait for that run to finish");
    }
}

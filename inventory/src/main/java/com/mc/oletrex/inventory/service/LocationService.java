package com.mc.oletrex.inventory.service;

import com.mc.oletrex.inventory.dto.CursorPage;
import com.mc.oletrex.inventory.dto.LocationRequest;
import com.mc.oletrex.inventory.dto.LocationResponse;
import com.mc.oletrex.inventory.dto.LocationUpdateRequest;

public interface LocationService {

    /** @throws com.mc.oletrex.inventory.exception.LocationAlreadyExistsException if the code is taken */
    LocationResponse createLocation(LocationRequest request);

    /** @throws com.mc.oletrex.inventory.exception.LocationNotFoundException if no location has this code */
    LocationResponse getLocation(String code);

    /** @throws com.mc.oletrex.inventory.exception.InvalidCursorException if the cursor cannot be decoded */
    CursorPage<LocationResponse> getLocations(String cursor, int limit);

    /** @throws com.mc.oletrex.inventory.exception.LocationNotFoundException if no location has this code */
    LocationResponse updateLocation(String code, LocationUpdateRequest request);
}

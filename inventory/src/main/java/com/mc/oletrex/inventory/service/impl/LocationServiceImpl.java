package com.mc.oletrex.inventory.service.impl;

import com.mc.oletrex.inventory.dto.CursorPage;
import com.mc.oletrex.inventory.dto.LocationRequest;
import com.mc.oletrex.inventory.dto.LocationResponse;
import com.mc.oletrex.inventory.dto.LocationUpdateRequest;
import com.mc.oletrex.inventory.exception.LocationAlreadyExistsException;
import com.mc.oletrex.inventory.exception.LocationNotFoundException;
import com.mc.oletrex.inventory.model.Location;
import com.mc.oletrex.inventory.repository.LocationRepository;
import com.mc.oletrex.inventory.service.LocationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class LocationServiceImpl implements LocationService {

    private final LocationRepository locationRepository;

    @Override
    public LocationResponse createLocation(LocationRequest request) {
        if (locationRepository.existsByCode(request.code())) {
            throw new LocationAlreadyExistsException(request.code());
        }
        Location location = locationRepository.saveAndFlush(Location.builder()
                .code(request.code())
                .name(request.name())
                .type(request.type())
                .addressLine(request.addressLine())
                .city(request.city())
                .state(request.state())
                .postalCode(request.postalCode())
                .country(request.country())
                .priority(request.priority())
                .active(request.active() == null || request.active())
                .build());
        return toResponse(location);
    }

    @Override
    @Transactional(readOnly = true)
    public LocationResponse getLocation(String code) {
        return toResponse(findLocation(code));
    }

    @Override
    @Transactional(readOnly = true)
    public CursorPage<LocationResponse> getLocations(String cursor, int limit) {
        List<Location> rows = locationRepository.findByIdGreaterThanOrderByIdAsc(IdCursor.decode(cursor), Limit.of(limit + 1));
        boolean hasMore = rows.size() > limit;
        List<Location> page = hasMore ? rows.subList(0, limit) : rows;
        String nextCursor = hasMore ? IdCursor.encode(page.getLast().getId()) : null;
        return new CursorPage<>(page.stream().map(this::toResponse).toList(), nextCursor, hasMore);
    }

    @Override
    public LocationResponse updateLocation(String code, LocationUpdateRequest request) {
        Location location = findLocation(code);
        location.setName(request.name());
        location.setType(request.type());
        location.setAddressLine(request.addressLine());
        location.setCity(request.city());
        location.setState(request.state());
        location.setPostalCode(request.postalCode());
        location.setCountry(request.country());
        location.setPriority(request.priority());
        location.setActive(request.active());
        locationRepository.flush(); // so updatedAt in the response reflects this change
        return toResponse(location);
    }

    private Location findLocation(String code) {
        return locationRepository.findByCode(code).orElseThrow(() -> new LocationNotFoundException(code));
    }

    private LocationResponse toResponse(Location l) {
        return new LocationResponse(l.getCode(), l.getName(), l.getType(), l.getAddressLine(), l.getCity(),
                l.getState(), l.getPostalCode(), l.getCountry(), l.getPriority(), l.isActive(),
                l.getCreatedAt(), l.getUpdatedAt());
    }
}

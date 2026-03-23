package io.cognis.vertical.sa.agriculture.store;

import io.cognis.vertical.sa.agriculture.model.FarmerProfile;
import java.io.IOException;
import java.util.List;

/**
 * Persistent store for South African farmer profiles, keyed by phone number.
 */
public interface FarmerStore {

    /** Returns an existing profile or creates a new default one. Never returns null. */
    FarmerProfile findOrCreate(String phone) throws IOException;

    /** Persists the given profile, overwriting any existing entry for the same phone. */
    void save(FarmerProfile profile) throws IOException;

    /** Returns all profiles in the given province (case-insensitive). */
    List<FarmerProfile> findByProvince(String province) throws IOException;

    /** Returns all profiles. */
    List<FarmerProfile> findAll() throws IOException;
}

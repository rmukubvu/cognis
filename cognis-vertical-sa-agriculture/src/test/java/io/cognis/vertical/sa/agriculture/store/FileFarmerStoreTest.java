package io.cognis.vertical.sa.agriculture.store;

import static org.assertj.core.api.Assertions.assertThat;

import io.cognis.vertical.sa.agriculture.model.FarmerProfile;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileFarmerStoreTest {

    @TempDir Path tempDir;

    private FileFarmerStore store() {
        return new FileFarmerStore(tempDir.resolve("farmers.json"));
    }

    @Test
    void findOrCreateReturnsFreshProfileForNewPhone() throws Exception {
        FarmerProfile profile = store().findOrCreate("+27821234567");
        assertThat(profile.phone()).isEqualTo("+27821234567");
        assertThat(profile.language()).isEqualTo("english");
        assertThat(profile.province()).isBlank();
        assertThat(profile.crops()).isEmpty();
        assertThat(profile.isEnriched()).isFalse();
    }

    @Test
    void findOrCreateReturnsSameProfileOnSecondCall() throws Exception {
        FileFarmerStore s = store();
        FarmerProfile first = s.findOrCreate("+27821234567");
        FarmerProfile second = s.findOrCreate("+27821234567");
        assertThat(second.registeredAt()).isEqualTo(first.registeredAt());
    }

    @Test
    void savePersistsAndFindOrCreateReturnsUpdated() throws Exception {
        FileFarmerStore s = store();
        FarmerProfile profile = s.findOrCreate("+27831111111");
        FarmerProfile updated = profile.withProvince("limpopo")
                                       .withLanguage("zulu")
                                       .withCrops(List.of("maize", "sunflower"))
                                       .withHectares(5.5);
        s.save(updated);

        FarmerProfile loaded = s.findOrCreate("+27831111111");
        assertThat(loaded.province()).isEqualTo("limpopo");
        assertThat(loaded.language()).isEqualTo("zulu");
        assertThat(loaded.crops()).containsExactly("maize", "sunflower");
        assertThat(loaded.hectares()).isEqualTo(5.5);
        assertThat(loaded.isEnriched()).isTrue();
    }

    @Test
    void findByProvinceReturnsMatchingFarmers() throws Exception {
        FileFarmerStore s = store();
        FarmerProfile limpopo1 = s.findOrCreate("+27811111111").withProvince("limpopo");
        FarmerProfile limpopo2 = s.findOrCreate("+27822222222").withProvince("limpopo");
        FarmerProfile kzn      = s.findOrCreate("+27833333333").withProvince("kwazulu-natal");
        s.save(limpopo1);
        s.save(limpopo2);
        s.save(kzn);

        List<FarmerProfile> limpopoFarmers = s.findByProvince("limpopo");
        assertThat(limpopoFarmers).hasSize(2);
        assertThat(limpopoFarmers.stream().map(FarmerProfile::phone).toList())
            .containsExactlyInAnyOrder("+27811111111", "+27822222222");
    }

    @Test
    void findByProvinceIsCaseInsensitive() throws Exception {
        FileFarmerStore s = store();
        s.save(s.findOrCreate("+27844444444").withProvince("limpopo"));
        assertThat(s.findByProvince("Limpopo")).hasSize(1);
        assertThat(s.findByProvince("LIMPOPO")).hasSize(1);
    }

    @Test
    void findAllReturnsAllProfiles() throws Exception {
        FileFarmerStore s = store();
        s.findOrCreate("+27811111111");
        s.findOrCreate("+27822222222");
        s.findOrCreate("+27833333333");
        assertThat(s.findAll()).hasSize(3);
    }

    @Test
    void findByProvinceWithBlankReturnsEmpty() throws Exception {
        assertThat(store().findByProvince("")).isEmpty();
        assertThat(store().findByProvince("   ")).isEmpty();
    }

    @Test
    void daffIdIsPersisted() throws Exception {
        FileFarmerStore s = store();
        FarmerProfile profile = s.findOrCreate("+27855555555").withDaffId("DAFF-2024-001234");
        s.save(profile);
        assertThat(s.findOrCreate("+27855555555").daffId()).isEqualTo("DAFF-2024-001234");
    }
}

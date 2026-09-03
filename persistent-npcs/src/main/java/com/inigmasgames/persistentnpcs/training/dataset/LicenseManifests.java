package com.inigmasgames.persistentnpcs.training.dataset;

import com.inigmasgames.persistentnpcs.training.dataset.DatasetContracts.LicenseManifest;
import com.inigmasgames.persistentnpcs.training.registry.CanonicalJson;
import java.util.List;
import java.util.Set;

public final class LicenseManifests {
    private LicenseManifests() { }
    public static LicenseManifest projectFixtureOnly() {
        Seed seed = new Seed("orbis-project-fixtures-v1", List.of("PROJECT_OWNED_FIXTURE"),
                List.of("real-player-data", "unapproved-teacher-output"),
                "Bounded synthetic/project-owned validation corpus only.", true);
        return new LicenseManifest(1, seed.id(), Set.copyOf(seed.allowed()),
                Set.copyOf(seed.prohibited()),
                seed.basis(), seed.approved(), CanonicalJson.sha256(seed));
    }
    private record Seed(String id, List<String> allowed, List<String> prohibited,
            String basis, boolean approved) { }
}

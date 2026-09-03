package com.inigmasgames.persistentnpcs.training.registry;

/** Named append-only registries for the complete artifact graph. */
public final class TrainingArtifactRegistries {
    private final AppendOnlyJsonlRegistry models;
    private final AppendOnlyJsonlRegistry promptTemplates;
    private final AppendOnlyJsonlRegistry datasets;
    private final AppendOnlyJsonlRegistry trainingRuns;
    private final AppendOnlyJsonlRegistry evaluationRuns;
    private final AppendOnlyJsonlRegistry modelBundles;
    private final AppendOnlyJsonlRegistry promotions;
    private final AppendOnlyJsonlRegistry teacherSources;

    public TrainingArtifactRegistries(ArtifactRoot root) {
        models = registry(root, "models.jsonl");
        promptTemplates = registry(root, "prompt-templates.jsonl");
        datasets = registry(root, "datasets.jsonl");
        trainingRuns = registry(root, "training-runs.jsonl");
        evaluationRuns = registry(root, "evaluation-runs.jsonl");
        modelBundles = registry(root, "model-bundles.jsonl");
        promotions = registry(root, "promotions.jsonl");
        teacherSources = registry(root, "teacher-sources.jsonl");
    }

    public AppendOnlyJsonlRegistry models() { return models; }
    public AppendOnlyJsonlRegistry promptTemplates() { return promptTemplates; }
    public AppendOnlyJsonlRegistry datasets() { return datasets; }
    public AppendOnlyJsonlRegistry trainingRuns() { return trainingRuns; }
    public AppendOnlyJsonlRegistry evaluationRuns() { return evaluationRuns; }
    public AppendOnlyJsonlRegistry modelBundles() { return modelBundles; }
    public AppendOnlyJsonlRegistry promotions() { return promotions; }
    public AppendOnlyJsonlRegistry teacherSources() { return teacherSources; }

    public void initialize() {
        models.initialize(); promptTemplates.initialize(); datasets.initialize(); trainingRuns.initialize();
        evaluationRuns.initialize(); modelBundles.initialize(); promotions.initialize();
        teacherSources.initialize();
    }

    private static AppendOnlyJsonlRegistry registry(ArtifactRoot root, String file) {
        return new AppendOnlyJsonlRegistry(root.resolve("registry", file));
    }
}

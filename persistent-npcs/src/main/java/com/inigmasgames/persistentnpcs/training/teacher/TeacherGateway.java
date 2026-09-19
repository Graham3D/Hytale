package com.inigmasgames.persistentnpcs.training.teacher;

import com.inigmasgames.persistentnpcs.training.registry.ArtifactIds;
import com.inigmasgames.persistentnpcs.training.registry.CanonicalJson;
import java.time.Instant;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/** Enforces terms, concurrency, retry, timeout, and non-gold trust boundaries. */
public final class TeacherGateway implements AutoCloseable {
    private final TeacherPolicyRegistry policies;
    private final ExecutorService executor;
    private final Semaphore concurrency;

    public TeacherGateway(TeacherPolicyRegistry policies, int maximumConcurrency) {
        this.policies = java.util.Objects.requireNonNull(policies, "policies");
        int bounded = Math.max(1, Math.min(8, maximumConcurrency));
        this.executor = Executors.newFixedThreadPool(bounded);
        this.concurrency = new Semaphore(bounded);
    }

    public TeacherContracts.TeacherRunResult execute(TeacherProvider provider,
            TeacherContracts.TeacherRequest request) {
        var identity = provider.identity();
        TeacherSourcePolicy policy = policies.requireApproved(identity.policyId(),
                identity.sourceId());
        if (!policy.snapshotHash().equals(identity.termsSnapshotHash())) {
            throw new IllegalStateException("teacher terms snapshot drift; generation blocked");
        }
        TeacherContracts.Capability capability = switch (request.taskConfig().taskType()) {
            case TARGET_GENERATION -> TeacherContracts.Capability.GENERATE_TARGET;
            case CRITIQUE -> TeacherContracts.Capability.CRITIQUE_STUDENT_OUTPUT;
            case PREFERENCE_RANKING -> TeacherContracts.Capability.RANK_PREFERENCE;
        };
        if (!provider.capabilities().contains(capability)) throw new IllegalStateException(
                "teacher lacks required capability " + capability);
        long started = System.nanoTime();
        Throwable last = null;
        int used = 0;
        for (int attempt = 1; attempt <= request.taskConfig().maximumAttempts(); attempt++) {
            used = attempt;
            try {
                concurrency.acquire();
                Future<TeacherContracts.TeacherResponse> future = executor.submit(
                        operation(provider, request));
                TeacherContracts.TeacherResponse response;
                try {
                    response = future.get(request.taskConfig().timeoutMillis(),
                            TimeUnit.MILLISECONDS);
                } catch (Exception timeoutOrFailure) {
                    future.cancel(true);
                    throw timeoutOrFailure;
                } finally {
                    concurrency.release();
                }
                if (!request.requestId().equals(response.requestId())) {
                    throw new IllegalStateException("teacher response/request identity mismatch");
                }
                long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
                String requestHash = CanonicalJson.sha256(request);
                String responseHash = CanonicalJson.sha256(response);
                var identitySeed = new RunIdentity(identity.contentId(), requestHash,
                        responseHash, request.taskConfig(), policy.snapshotHash());
                var manifest = new TeacherContracts.TeacherRunManifest(1,
                        ArtifactIds.teacherRun(identitySeed), identity, policy, requestHash,
                        responseHash, request.taskConfig(),
                        TeacherContracts.OutputTrust.PROPOSED_LABEL, used, elapsed, Instant.now());
                return new TeacherContracts.TeacherRunResult(manifest, response);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("teacher request interrupted", interrupted);
            } catch (Throwable failure) {
                last = failure;
            }
        }
        throw new IllegalStateException("teacher request failed after " + used
                + " bounded attempt(s)", last);
    }

    private static Callable<TeacherContracts.TeacherResponse> operation(
            TeacherProvider provider, TeacherContracts.TeacherRequest request) {
        return switch (request.taskConfig().taskType()) {
            case TARGET_GENERATION -> () -> provider.generateTarget(request);
            case CRITIQUE -> () -> provider.critiqueStudentOutput(request);
            case PREFERENCE_RANKING -> () -> provider.rankPreference(request);
        };
    }

    @Override public void close() { executor.shutdownNow(); }
    private record RunIdentity(String teacherId, String requestHash, String responseHash,
            TeacherContracts.TeacherTaskConfig taskConfig, String policyHash) { }
}

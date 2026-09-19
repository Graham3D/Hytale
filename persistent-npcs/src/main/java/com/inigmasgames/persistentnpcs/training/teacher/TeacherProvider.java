package com.inigmasgames.persistentnpcs.training.teacher;

import java.util.Set;

/** Provider-neutral teacher boundary; no implementation is wired into production runtime. */
public interface TeacherProvider {
    TeacherContracts.TeacherIdentity identity();
    Set<TeacherContracts.Capability> capabilities();
    TeacherContracts.TeacherResponse generateTarget(TeacherContracts.TeacherRequest request)
            throws Exception;
    TeacherContracts.TeacherResponse critiqueStudentOutput(
            TeacherContracts.TeacherRequest request) throws Exception;
    TeacherContracts.TeacherResponse rankPreference(
            TeacherContracts.TeacherRequest request) throws Exception;
    TeacherContracts.Health healthCheck();
}

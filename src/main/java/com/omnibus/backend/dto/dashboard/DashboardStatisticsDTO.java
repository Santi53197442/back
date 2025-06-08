package com.omnibus.backend.dto.dashboard;

import java.util.List;

public class DashboardStatisticsDTO {
    private List<UserRoleCountDTO> userRoleCounts;
    private List<UserAgeGroupDTO> userAgeDistribution;
    private List<UserCreationOverTimeDTO> userCreationOverTime;

    // --- Constructor Vacío ---
    public DashboardStatisticsDTO() {
    }

    // --- Getters y Setters ---
    public List<UserRoleCountDTO> getUserRoleCounts() {
        return userRoleCounts;
    }

    public void setUserRoleCounts(List<UserRoleCountDTO> userRoleCounts) {
        this.userRoleCounts = userRoleCounts;
    }

    public List<UserAgeGroupDTO> getUserAgeDistribution() {
        return userAgeDistribution;
    }

    public void setUserAgeDistribution(List<UserAgeGroupDTO> userAgeDistribution) {
        this.userAgeDistribution = userAgeDistribution;
    }

    public List<UserCreationOverTimeDTO> getUserCreationOverTime() {
        return userCreationOverTime;
    }

    public void setUserCreationOverTime(List<UserCreationOverTimeDTO> userCreationOverTime) {
        this.userCreationOverTime = userCreationOverTime;
    }
}
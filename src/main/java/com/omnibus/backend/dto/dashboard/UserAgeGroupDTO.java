package com.omnibus.backend.dto.dashboard;

public class UserAgeGroupDTO {
    private String ageGroup;
    private long count;

    // --- Constructor Vacío ---
    public UserAgeGroupDTO() {
    }

    // --- Getters y Setters ---
    public String getAgeGroup() {
        return ageGroup;
    }

    public void setAgeGroup(String ageGroup) {
        this.ageGroup = ageGroup;
    }

    public long getCount() {
        return count;
    }

    public void setCount(long count) {
        this.count = count;
    }
}

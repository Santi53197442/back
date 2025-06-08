package com.omnibus.backend.dto.dashboard;

public class UserRoleCountDTO {
    private String roleName;
    private long count;

    // --- Constructor Vacío (buena práctica) ---
    public UserRoleCountDTO() {
    }

    // --- Getters y Setters ---
    public String getRoleName() {
        return roleName;
    }

    public void setRoleName(String roleName) {
        this.roleName = roleName;
    }

    public long getCount() {
        return count;
    }

    public void setCount(long count) {
        this.count = count;
    }
}
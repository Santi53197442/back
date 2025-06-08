package com.omnibus.backend.dto.dashboard;

import java.time.LocalDate;

public class UserCreationOverTimeDTO {
    private LocalDate creationDate;
    private long count;

    // --- Constructor Vacío ---
    public UserCreationOverTimeDTO() {
    }

    // --- Getters y Setters ---
    public LocalDate getCreationDate() {
        return creationDate;
    }

    public void setCreationDate(LocalDate creationDate) {
        this.creationDate = creationDate;
    }

    public long getCount() {
        return count;
    }

    public void setCount(long count) {
        this.count = count;
    }
}
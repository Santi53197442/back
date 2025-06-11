// Ubicación: src/main/java/com/omnibus/backend/dto/PaginatedUserResponseDTO.java
package com.omnibus.backend.dto;

import java.util.List;

public class PaginatedUserResponseDTO {

    private List<UserViewDTO> content; // Lista de usuarios de la página actual
    private int currentPage;          // Número de la página actual (base 0)
    private long totalItems;          // Total de elementos en todas las páginas
    private int totalPages;           // Número total de páginas

    // Constructor vacío
    public PaginatedUserResponseDTO() {
    }

    // Constructor con todos los campos
    public PaginatedUserResponseDTO(List<UserViewDTO> content, int currentPage, long totalItems, int totalPages) {
        this.content = content;
        this.currentPage = currentPage;
        this.totalItems = totalItems;
        this.totalPages = totalPages;
    }

    // --- Getters y Setters ---

    public List<UserViewDTO> getContent() {
        return content;
    }

    public void setContent(List<UserViewDTO> content) {
        this.content = content;
    }

    public int getCurrentPage() {
        return currentPage;
    }

    public void setCurrentPage(int currentPage) {
        this.currentPage = currentPage;
    }

    public long getTotalItems() {
        return totalItems;
    }

    public void setTotalItems(long totalItems) {
        this.totalItems = totalItems;
    }

    public int getTotalPages() {
        return totalPages;
    }

    public void setTotalPages(int totalPages) {
        this.totalPages = totalPages;
    }
}
package com.omnibus.backend.service;

import com.omnibus.backend.dto.dashboard.*; // Importa tus nuevos DTOs
import org.springframework.stereotype.Service;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import java.util.List;
import java.util.stream.Collectors;
import java.math.BigInteger; // Para los resultados de COUNT(*) en SQL nativo

@Service
public class DashboardService {

    @PersistenceContext
    private EntityManager entityManager;

    public DashboardStatisticsDTO getDashboardStatistics() {
        DashboardStatisticsDTO stats = new DashboardStatisticsDTO();
        stats.setUserRoleCounts(getUserRoleCounts());
        stats.setUserAgeDistribution(getUserAgeDistribution());
        stats.setUserCreationOverTime(getUserCreationsOverTime());
        return stats;
    }

    private List<UserRoleCountDTO> getUserRoleCounts() {
        // Esta consulta se basa en tu estructura de herencia con tablas separadas
        String sql = "SELECT " +
                "  CASE " +
                "    WHEN c.usuario_id IS NOT NULL THEN 'CLIENTE' " +
                "    WHEN v.usuario_id IS NOT NULL THEN 'VENDEDOR' " +
                "    WHEN a.usuario_id IS NOT NULL THEN 'ADMINISTRADOR' " +
                "    ELSE 'DESCONOCIDO' " +
                "  END as roleName, " +
                "  COUNT(*) as count " +
                "FROM usuarios u " +
                "LEFT JOIN clientes_data c ON u.id = c.usuario_id " +
                "LEFT JOIN vendedores_data v ON u.id = v.usuario_id " +
                "LEFT JOIN administradores_data a ON u.id = a.usuario_id " +
                "GROUP BY roleName";

        Query query = entityManager.createNativeQuery(sql);
        List<Object[]> results = query.getResultList();

        return results.stream().map(row -> {
            UserRoleCountDTO dto = new UserRoleCountDTO();
            dto.setRoleName((String) row[0]);
            dto.setCount(((BigInteger) row[1]).longValue());
            return dto;
        }).collect(Collectors.toList());
    }

    private List<UserAgeGroupDTO> getUserAgeDistribution() {
        // Esta consulta calcula la edad a partir de fecha_nac (ejemplo para PostgreSQL)
        String sql = "SELECT " +
                "  CASE " +
                "    WHEN date_part('year', age(fecha_nac)) BETWEEN 18 AND 25 THEN '18-25' " +
                "    WHEN date_part('year', age(fecha_nac)) BETWEEN 26 AND 35 THEN '26-35' " +
                "    WHEN date_part('year', age(fecha_nac)) BETWEEN 36 AND 50 THEN '36-50' " +
                "    WHEN date_part('year', age(fecha_nac)) > 50 THEN '51+' " +
                "    ELSE 'N/A' " +
                "  END as ageGroup, " +
                "  COUNT(*) as count " +
                "FROM usuarios " +
                "WHERE fecha_nac IS NOT NULL " +
                "GROUP BY ageGroup " +
                "ORDER BY ageGroup";

        Query query = entityManager.createNativeQuery(sql);
        List<Object[]> results = query.getResultList();

        return results.stream().map(row -> {
            UserAgeGroupDTO dto = new UserAgeGroupDTO();
            dto.setAgeGroup((String) row[0]);
            dto.setCount(((BigInteger) row[1]).longValue());
            return dto;
        }).collect(Collectors.toList());
    }

    private List<UserCreationOverTimeDTO> getUserCreationsOverTime() {
        // Agrupa los usuarios creados por día
        String sql = "SELECT CAST(fecha_creacion AS DATE) as creationDate, COUNT(*) as count " +
                "FROM usuarios " +
                "GROUP BY CAST(fecha_creacion AS DATE) " +
                "ORDER BY creationDate ASC";

        Query query = entityManager.createNativeQuery(sql, UserCreationOverTimeDTO.class); // Spring puede mapear directamente si los nombres coinciden
        return query.getResultList();
    }
}
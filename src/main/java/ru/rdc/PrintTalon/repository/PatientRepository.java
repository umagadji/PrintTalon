package ru.rdc.PrintTalon.repository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import ru.rdc.PrintTalon.model.Patient;

import java.util.List;
import java.util.Optional;

@Repository
public class PatientRepository {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    public Optional<Patient> authenticate(String rawSnils) {
        // Удаляем всё, кроме цифр
        String normalizedSnils = rawSnils.replaceAll("[^\\d]", "");

        String sql = """
        SELECT
            p.keyid,
            p.num,
            INITCAP(p.lastname) || ' ' ||
            UPPER(SUBSTR(p.firstname, 1, 1)) || '.' ||
            UPPER(SUBSTR(p.secondname, 1, 1)) || '.' AS fam,
            INITCAP(p.firstname) as name,
            p.birthdate,
            p.snils
        FROM
            patient p
        WHERE
            p.snils IS NOT NULL
            AND REPLACE(REPLACE(p.snils, '-', ''), ' ', '') = ?
    """;

        List<Patient> result = jdbcTemplate.query(sql, new Object[]{normalizedSnils}, (rs, rowNum) -> {
            Patient patient = new Patient();
            patient.setPatientId(rs.getLong("keyid"));
            patient.setNum(rs.getString("num"));
            patient.setFam(rs.getString("fam"));
            patient.setName(rs.getString("name"));
            patient.setBirthdate(String.valueOf(rs.getDate("birthdate")));
            patient.setSnils(rs.getString("snils"));
            return patient;
        });

        return result.stream().findFirst();
    }

    // Новый метод поиска по ID
    public Optional<Patient> findById(Long patientId) {
        String sql = """
        SELECT
            p.keyid,
            p.num,
            INITCAP(p.lastname) || ' ' ||
            UPPER(SUBSTR(p.firstname, 1, 1)) || '.' ||
            UPPER(SUBSTR(p.secondname, 1, 1)) || '.' AS fam,
            INITCAP(p.firstname) as name,
            p.birthdate,
            p.snils
        FROM
            patient p
        WHERE
            p.keyid = ?
        """;

        try {
            Patient patient = jdbcTemplate.queryForObject(sql, new Object[]{patientId}, (rs, rowNum) -> {
                Patient p = new Patient();
                p.setPatientId(rs.getLong("keyid"));
                p.setNum(rs.getString("num"));
                p.setFam(rs.getString("fam"));
                p.setName(rs.getString("name"));
                p.setBirthdate(String.valueOf(rs.getDate("birthdate")));
                p.setSnils(rs.getString("snils"));
                return p;
            });
            return Optional.ofNullable(patient);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }
}
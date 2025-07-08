package ru.rdc.PrintTalon.repository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import ru.rdc.PrintTalon.model.Patient;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

@Repository
public class PatientRepository {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    public Optional<Patient> authenticateBySnils(String rawSnils) {
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

        List<Patient> result = jdbcTemplate.query(sql, new Object[]{normalizedSnils}, this::mapPatient);
        return result.stream().findFirst();
    }

    public Optional<Patient> authenticateByPolicyNumber(String policyNumber) {
        String normalizedPolicy = policyNumber.replaceAll("[^\\d]", "");

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
        patient p,
        police pol
    WHERE
        pol.code IS NOT NULL
        AND pol.patientid = p.keyid
        AND REPLACE(pol.code, ' ', '') = ?
    """;

        List<Patient> result = jdbcTemplate.query(sql, new Object[]{normalizedPolicy}, this::mapPatient);
        return result.stream().findFirst();
    }

    public Optional<Patient> authenticateByCardNumber(String cardNumber) {
        String normalizedCard = cardNumber.replaceAll("[^\\d]", "");

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
            p.num IS NOT NULL
            AND REPLACE(p.num, ' ', '') = ?
        """;

        List<Patient> result = jdbcTemplate.query(sql, new Object[]{normalizedCard}, this::mapPatient);
        return result.stream().findFirst();
    }

    private Patient mapPatient(ResultSet rs, int rowNum) throws SQLException {
        Patient patient = new Patient();
        patient.setPatientId(rs.getLong("keyid"));
        patient.setNum(rs.getString("num"));
        patient.setFam(rs.getString("fam"));
        patient.setName(rs.getString("name"));
        patient.setBirthdate(String.valueOf(rs.getDate("birthdate")));
        patient.setSnils(rs.getString("snils"));
        return patient;
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
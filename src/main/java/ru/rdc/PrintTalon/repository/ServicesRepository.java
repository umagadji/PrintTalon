package ru.rdc.PrintTalon.repository;

import lombok.RequiredArgsConstructor;
import oracle.jdbc.OracleTypes;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import ru.rdc.PrintTalon.dto.ServiceDTO;

import java.sql.*;
import java.sql.Date;
import java.time.LocalDate;
import java.util.*;

@Repository
@RequiredArgsConstructor
public class ServicesRepository {

    private final JdbcTemplate jdbcTemplate;

    public List<ServiceDTO> getServicesForPatient(Long patientId, LocalDate from, LocalDate to) {
        return jdbcTemplate.execute((Connection con) -> {
            CallableStatement stmt = con.prepareCall(
                    "{ call p_reg_direction.get_rnumb_refuse_list_by_pat(?, ?, ?, ?, ?) }"
            );
            stmt.setDate(1, java.sql.Date.valueOf(from));
            stmt.setDate(2, java.sql.Date.valueOf(to));
            stmt.setLong(3, patientId);
            stmt.setNull(4, Types.BIGINT);
            stmt.registerOutParameter(5, OracleTypes.CURSOR);

            System.out.println(patientId);

            stmt.execute();

            ResultSet rs = (ResultSet) stmt.getObject(5);
            Map<String, ServiceDTO> mergedMap = new LinkedHashMap<>();

            while (rs.next()) {
                String agrType = rs.getString("AGR_TYP");
                Long keyid = rs.getLong("KEYID");
                Long psKeyid = rs.getLong("PS_KEYID");

                if (keyid == 0 || !( "5.1.0".equals(agrType) || "5.5.0".equals(agrType) )) {
                    continue;
                }

                String visitId = getVisitIdByPsKeyid(psKeyid);
                if (visitId == null) {
                    continue;
                }

                Integer visitType;
                try {
                    visitType = jdbcTemplate.queryForObject(
                            "SELECT vistype FROM visit WHERE keyid = ?",
                            new Object[]{visitId},
                            Integer.class
                    );
                } catch (EmptyResultDataAccessException e) {
                    continue;
                }

                if (visitType == null || visitType != 0) {
                    continue;
                }

                String mergeKey = visitId;
                if (!mergedMap.containsKey(mergeKey)) {
                    ServiceDTO dto = new ServiceDTO();
                    dto.setKeyid(keyid);
                    dto.setRoom(rs.getString("ROOM"));
                    dto.setSCode(rs.getString("S_CODE"));
                    dto.setSText(rs.getString("S_TEXT"));
                    dto.setDat(rs.getTimestamp("DAT").toLocalDateTime());
                    dto.setDirForm(rs.getString("DIR_FORM"));
                    dto.setDirOrg(rs.getString("DIRORG"));
                    dto.setDoctor(rs.getString("DOCTOR"));
                    dto.setVisitId(visitId);
                    dto.setPsKeyid(psKeyid);
                    dto.setAgrType(agrType);
                    mergedMap.put(mergeKey, dto);
                } else {
                    ServiceDTO dto = mergedMap.get(mergeKey);
                    dto.setSText(dto.getSText() + ", " + rs.getString("S_TEXT"));
                }
            }

            return new ArrayList<>(mergedMap.values());
        });
    }

    /*public List<ServiceDTO> getServicesForPatient(Long patientId, LocalDate from, LocalDate to) {
        return jdbcTemplate.execute((Connection con) -> {
            CallableStatement stmt = con.prepareCall(
                    "{ call p_reg_direction.get_rnumb_refuse_list_by_pat(?, ?, ?, ?, ?) }"
            );
            stmt.setDate(1, java.sql.Date.valueOf(from));
            stmt.setDate(2, java.sql.Date.valueOf(to));
            stmt.setLong(3, patientId);
            stmt.setNull(4, Types.BIGINT);
            stmt.registerOutParameter(5, OracleTypes.CURSOR);

            stmt.execute();

            ResultSet rs = (ResultSet) stmt.getObject(5);
            Map<String, ServiceDTO> mergedMap = new LinkedHashMap<>();

            while (rs.next()) {
                String agrType = rs.getString("AGR_TYP");
                Long keyid = rs.getLong("KEYID");

                if (keyid != 0 && ("5.1.0".equals(agrType) || "5.5.0".equals(agrType))) {
                    String visitId = getVisitIdByPsKeyid(rs.getLong("PS_KEYID"));

                    String mergeKey = visitId != null ? visitId : UUID.randomUUID().toString();
                    if (!mergedMap.containsKey(mergeKey)) {
                        ServiceDTO dto = new ServiceDTO();
                        dto.setKeyid(keyid);
                        dto.setRoom(rs.getString("ROOM"));
                        dto.setSCode(rs.getString("S_CODE"));
                        dto.setSText(rs.getString("S_TEXT"));
                        dto.setDat(rs.getTimestamp("DAT").toLocalDateTime());
                        dto.setDirForm(rs.getString("DIR_FORM"));
                        dto.setDirOrg(rs.getString("DIRORG"));
                        dto.setDoctor(rs.getString("DOCTOR"));
                        dto.setVisitId(visitId);
                        dto.setPsKeyid(rs.getLong("PS_KEYID"));
                        dto.setAgrType(agrType);
                        mergedMap.put(mergeKey, dto);
                    } else {
                        ServiceDTO dto = mergedMap.get(mergeKey);
                        dto.setSText(dto.getSText() + ", " + rs.getString("S_TEXT"));
                    }
                }
            }

            return new ArrayList<>(mergedMap.values());
        });
    }*/

    private String getVisitIdByPsKeyid(Long psKeyid) {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT visitid FROM patserv WHERE keyid = ?",
                    new Object[]{psKeyid},
                    String.class
            );
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    public Map<String, Object> getTalonData(Long rnumbId) {
        String sql = """
        SELECT
            INITCAP(fn_pat_name_by_id(p.keyid)) AS INFFIOONE,
            INITCAP(fn_pat_name_by_id(p.keyid)) AS INFFIOTWO,
            TO_CHAR(p.birthdate, 'dd.mm.yyyy') AS INFBDONE,
            INITCAP(fn_get_doc_name_by_vis_id(v.keyid)) AS INFDOCTORONE,
            INITCAP(fn_get_doc_name_by_vis_id(v.keyid)) AS INFDOCTORTWO,
            UPPER(pkg_kladr.get_address(p.keyid,1)) AS INFADDRONE,
            UPPER(pkg_kladr.get_address(p.keyid,1)) AS INFADDRTWO,
            (SELECT printtext FROM lpu WHERE ROWNUM = 1) AS LPU,
            'ТАЛОН ПРИЕМА ЗАСТРАХОВАННОГО ПАЦИЕНТА №: ' || r.keyid AS PNUMONE,
            pkg_barcode.Code_39(p.num) AS BARCODE,
            'ВРАЧ: ' || UPPER(fn_get_doc_name_by_vis_id(v.keyid)) AS DOCTOR,
            '' AS SRVCODE,
            'ПАЦИЕНТ: ' || UPPER(fn_pat_name_by_id(p.keyid)) AS FIO,
            'ДАТА РОЖДЕНИЯ: ' || TO_CHAR(p.birthdate, 'dd.mm.yyyy') AS BD,
            'ПОЛИС: ' || fn_get_police_talon(pol.keyid) AS DOCUMENT,
            'РЕГИСТРАТОР: ' || fn_get_man_name_by_id(r.createby) AS REGISTRATORONE,
            'ДАТА РЕГ.: ' || TO_CHAR(r.createdate, 'dd.mm.yyyy') AS DATAREG,
            'НАПЕЧАТАЛ: ' || (SELECT FN_GET_MAN_NAME_BY_ID(GSP_GET_USER_ID()) FROM DUAL) AS NAPECHATAL,
            'ДАТА ПЕЧАТИ: ' || TO_CHAR(SYSDATE, 'dd.mm.yyyy hh24:mi') AS PRINTDATONE,
            'НАПРАВЛЕН: ' || 
                (SELECT fr.text FROM reg_direction dr, dir_org fr WHERE fr.keyid = dr.dir_org_id AND dr.keyid = ps.reg_direction_id) ||
                (SELECT ' ' || vrd.dir_diag_code FROM v_reg_direction vrd, reg_direction rd WHERE vrd.keyid = rd.keyid AND v.reg_direction_id = rd.keyid AND ROWNUM = 1) 
                AS NAPRAVLEN,
            (CASE 
                WHEN r.dirid IN (4666,4667,4668,4669,4670,1450494,1450495,1450496)
                THEN 'ДАТА ОБСЛ.: ' || TO_CHAR(r.dat, 'dd.mm.yyyy')
                ELSE 'ДАТА ОБСЛ.: ' || TO_CHAR(r.dat, 'dd.mm.yyyy') || ' ВРЕМЯ: ' || fn_get_time_rnumb_by_id((SELECT r.visitid FROM rnumb r WHERE r.keyid = %d))
            END) AS DATEOBSLONE,
            '№ МЕДКАРТЫ: ' || p.num AS MEDNUMONE,
            TO_CHAR(r.dat, 'hh24:mi') AS RTIME,
            'ОТРЫВНОЙ ТАЛОН К ТАЛОНУ №: ' || r.keyid AS PNUMTWO,
            'ПАЦИЕНТ: ' || UPPER(fn_pat_name_by_id(p.keyid)) AS FIOTWO,
            (CASE 
                WHEN r.dirid IN (4666,4667,4668,4669,4670,1450494,1450495,1450496)
                THEN 'ДАТА ОБСЛ.: ' || TO_CHAR(r.dat, 'dd.mm.yyyy')
                ELSE 'ДАТА ОБСЛ.: ' || TO_CHAR(r.dat, 'dd.mm.yyyy') || ' ВРЕМЯ: ' || fn_get_time_rnumb_by_id((SELECT r.visitid FROM rnumb r WHERE r.keyid = %d))
            END) AS DATEOBSLTWO,
            TO_CHAR(r.dat, 'hh24:mi') AS RTIMETWO,
            'НАПЕЧАТАНО: ' || TO_CHAR(SYSDATE, 'dd.mm.yyyy hh24:mi') AS PRINTDATTWO,
            '№ РЕГ.: ' || (SELECT m.code FROM man m WHERE r.createby = m.keyid) AS REGCODE,
            '№ МЕДКАРТЫ: ' || p.num AS MEDNUMTWO,
            (CASE 
                WHEN r.dirid IN (4666,4667,4668,4669,4670,1450494,1450495,1450496)
                THEN fn_lab_list_by_visit_id_rn_new((SELECT r.visitid FROM rnumb r WHERE r.keyid = %d), %d)
                ELSE NVL(srv.print_text, srv.text)
            END) AS USLLIST,
            (CASE 
                WHEN r.dirid IN (4666,4667,4668,4669,4670,1450494,1450495,1450496)
                THEN '' 
                ELSE NVL(srv.print_text, srv.text)
            END) AS USLLISTTWO,
            (CASE 
                WHEN r.dirid IN (4666,4667,4668,4669,4670,1450494,1450495,1450496)
                THEN 'ИССЛЕДОВАНИЯ: '
                ELSE ''
            END) AS USLTITLE,
            (CASE 
                WHEN r.dirid IN (4666,4667,4668,4669,4670,1450494,1450495,1450496)
                THEN 'Результаты исследования выдаются в поликлинике по месту жительства.'
                ELSE ''
            END) AS OTRTALONINFO,
            (CASE 
                WHEN r.dirid IN (4666,4667,4668,4669,4670,1450494,1450495,1450496)
                THEN 'КАБИНЕТ: ' || r.room
                ELSE 'КАБИНЕТ: ' || fn_get_shift_rnumb_by_id((SELECT r.visitid FROM rnumb r WHERE r.keyid = %d))
            END) AS ROOMONE,
            (CASE 
                WHEN r.dirid IN (4666,4667,4668,4669,4670,1450494,1450495,1450496)
                THEN 'КАБИНЕТ: ' || r.room
                ELSE 'КАБИНЕТ: ' || fn_get_shift_rnumb_by_id((SELECT r.visitid FROM rnumb r WHERE r.keyid = %d))
            END) AS ROOMTWO,
            p.cellular AS PHONE,
            (
                SELECT '№НАПР.: ' || LISTAGG(DISTINCT SUBSTR(rd.code, INSTR(rd.code, '/', -1) + 1), ', ') WITHIN GROUP (ORDER BY rd.code)
                FROM reg_direction rd 
                JOIN patserv ps_sub ON ps_sub.reg_direction_id = rd.keyid
                JOIN visit v_sub ON v_sub.keyid = ps_sub.visitid 
                WHERE v_sub.keyid IN (SELECT r.visitid FROM rnumb r WHERE r.keyid = %d)
            ) AS rdnumbs,
            (SELECT pp.logon_name FROM inetuser.patient_logon pp WHERE pp.patient_id = p.keyid AND ROWNUM = 1) AS LOGIN,
            (SELECT pp.pass FROM inetuser.patient_logon pp WHERE pp.patient_id = p.keyid AND ROWNUM = 1) AS PASSWORD
        FROM 
            patient p,
            visit v,
            srvdep srv,
            patserv ps,
            agr a,
            police pol,
            rnumb r
        WHERE 
            p.keyid = v.patientid
            AND ps.visitid = v.keyid
            AND srv.keyid = ps.srvdepid
            AND v.keyid = (SELECT r.visitid FROM rnumb r WHERE r.keyid = %d)
            AND r.visitid = v.keyid
            AND a.keyid = v.agrid
            AND pol.agrid (+)= a.keyid
            AND pol.keyid = v.policeid
            AND ps.root_id IS NULL
            AND ROWNUM = 1
        """.formatted(rnumbId, rnumbId, rnumbId, rnumbId, rnumbId, rnumbId, rnumbId, rnumbId, rnumbId, rnumbId);

        return jdbcTemplate.queryForMap(sql);
    }

}
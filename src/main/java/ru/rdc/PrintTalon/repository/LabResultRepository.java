package ru.rdc.PrintTalon.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import ru.rdc.PrintTalon.dto.LabResultViewDto;
import ru.rdc.PrintTalon.dto.LabTestResultDto;

import java.time.LocalDate;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class LabResultRepository {

    private final JdbcTemplate jdbcTemplate;

    //Получаем данные для результатов анализов из БД
    public List<LabTestResultDto> findResultsByPatientIdAndDate(Long patientID, LocalDate analysisDate) {
        String sql = """
                SELECT
                    p_pat.num(r.patient_id) as pnum,
                    p_pat.fio(r.patient_id) || ' (' || p_pat.sex(r.patient_id) || ')' as fio,
                    p_pat.age(r.patient_id) as bdate,
                    p_pat.address(r.patient_id) as address,
                    ls.ids as ids,
                    s.text as material,
                    coalesce(trim((select (case when r.agr_id in (1,4,5,7,31,32,36,37,60,61,253,264,265,294,295,296) then solution_med.p_lab.get_customer(r.id, 0)
                         else (select a.text from solution_med.agr a where r.agr_id = a.keyid) end)
                         from solution_lab.research r where r.id = ls.ariadna_id)), 'Без МО') as lpu,
                    coalesce(trim((select ld.text from nlab.lab_dep ld where ld.id = ls.target_lab_dep_id)), 'Нет') as otd,
                    (SELECT LISTAGG(distinct tr.executor_text, ', ') WITHIN GROUP (ORDER BY tr.executor_text)
                     FROM nlab.test_result tr
                     WHERE tr.lab_sample_id = ls.id
                       AND tr.executor_text IS NOT NULL
                       AND NOT EXISTS (SELECT 1 FROM nlab.lab_device ld WHERE ld.text = tr.executor_text)) as executors,
                    serv.text as usl,
                    lt.text as labtest,
                    tr.value as value,
                    tr.units,
                    tr.norm_text as norm,
                    to_char(ls.collect_date, 'dd.mm.yyyy hh24:mi') as collecdate,
                    to_char(COALESCE(r.finishdate, tr.validation_date), 'dd.mm.yyyy hh24:mi') as finisdate,
                    to_char(sysdate, 'dd.mm.yyyy hh24:mi') as printdate,
                    tr.pathology_status as patstatus,
                    nvl((select tg.text from nlab.test_group tg where tg.id = lt.test_group_id), ' ') as test_group,
                    lt.view_sortcode as view_sortcode,
                    nvl((select tg.view_sortcode from nlab.test_group tg where tg.id = lt.test_group_id), '0') as tg_sort,
                    serv.code as code_usl,
                    tr.note as comments,
                    (SELECT to_char(listagg(t.executor_text, ', ') within GROUP(ORDER BY executor_text))
                     FROM (SELECT DISTINCT tr.executor_text
                           FROM nlab.test_result tr
                           WHERE tr.lab_sample_id = ls.id
                             AND tr.executor_text is not null
                             AND NOT EXISTS (SELECT 1 FROM nlab.usr u WHERE u.text = tr.executor_text)) t) as device,
                    (select (CASE WHEN r.agr_id IN (4, 5, 7) then --ДРКБ
                                 (SELECT max(solution_med.pkg_protocol_universal.get_answer(rg.keyid,'NPR_MO_DOCTOR','REG_DIRECTION_ADDITIONAL_FORM','text','regregdir'))
                                 FROM solution_med.patserv ps, solution_lab.order_info oi, solution_med.visit v, solution_med.reg_direction rg
                                 WHERE oi.research_id=r.id AND ps.keyid=oi.patserv_id AND rg.keyid=v.reg_direction_id AND ps.visitid=v.keyid)
                             else '' end) from solution_lab.research r where r.id = ls.ariadna_id)
                     as vrachizmo,
                    (select (CASE WHEN r.agr_id IN (4, 5, 7) then --ДРКБ
                                       (SELECT max(solution_med.pkg_protocol_universal.get_answer(rg.keyid,'9','REG_DIRECTION_ADDITIONAL_FORM','text','regregdir'))
                                          FROM solution_med.patserv ps, solution_lab.order_info oi, solution_med.visit v, solution_med.reg_direction rg
                                         WHERE oi.research_id=r.id AND ps.keyid=oi.patserv_id AND rg.keyid=v.reg_direction_id AND ps.visitid=v.keyid)
                                     else '' end) from solution_lab.research r where r.id = ls.ariadna_id)
                     as otdeleniemo
                    ,(SELECT 'Лицензия: ' || frv.text AS field_name
                    FROM SOLUTION_REG.FORM_RESULT_VALUE_ECONOM frv
                    WHERE frv.form_result_id = '277' AND frv.col_num IS NULL AND frv.row_num IS NULL AND frv.form_item_id = 19889) AS LICENSE
                    ,(SELECT 'Адрес: ' || frv.text AS field_name
                    FROM SOLUTION_REG.FORM_RESULT_VALUE_ECONOM frv
                    WHERE frv.form_result_id = '277' AND frv.col_num IS NULL AND frv.row_num IS NULL AND frv.form_item_id = 7187) AS RDCADDRESS
                    ,(SELECT 'Горячая линия: ' || frv.text AS field_name
                    FROM SOLUTION_REG.FORM_RESULT_VALUE_ECONOM frv
                    WHERE frv.form_result_id = '277' AND frv.col_num IS NULL AND frv.row_num IS NULL AND frv.form_item_id = 19890) AS HOTLINE
                    ,(SELECT 'Call-center: ' || frv.text AS field_name
                    FROM SOLUTION_REG.FORM_RESULT_VALUE_ECONOM frv
                    WHERE frv.form_result_id = '277' AND frv.col_num IS NULL AND frv.row_num IS NULL AND frv.form_item_id = 19891) AS CALLCENTER
                    ,(SELECT 'Сайт: ' || frv.text AS field_name
                    FROM SOLUTION_REG.FORM_RESULT_VALUE_ECONOM frv
                    WHERE frv.form_result_id = '277' AND frv.col_num IS NULL AND frv.row_num IS NULL AND frv.form_item_id = 19892) AS SITE
                    ,(SELECT 'E-mail: ' || frv.text AS field_name
                    FROM SOLUTION_REG.FORM_RESULT_VALUE_ECONOM frv
                    WHERE frv.form_result_id = '277' AND frv.col_num IS NULL AND frv.row_num IS NULL AND frv.form_item_id = 19893) AS EMAIL
                    ,(SELECT 'Справочная: ' || frv.text AS field_name
                    FROM SOLUTION_REG.FORM_RESULT_VALUE_ECONOM frv
                    WHERE frv.form_result_id = '277' AND frv.col_num IS NULL AND frv.row_num IS NULL AND frv.form_item_id = 19894) AS SPRAVOCHNAYA
                    ,(SELECT frv.text AS field_name
                    FROM SOLUTION_REG.FORM_RESULT_VALUE_ECONOM frv
                    WHERE frv.form_result_id = '277' AND frv.col_num IS NULL AND frv.row_num IS NULL AND frv.form_item_id = 7178) AS MO
                FROM nlab.test_result tr
                         JOIN nlab.lab_sample ls ON tr.lab_sample_id = ls.id
                         JOIN solution_lab.research r ON ls.ariadna_id = r.id
                         JOIN patient p ON r.patient_id = p.keyid
                         JOIN nlab.specimen s ON ls.specimen_id = s.id
                         JOIN nlab.lab_test lt ON tr.lab_test_id = lt.id
                         JOIN nlab.serv serv ON serv.id IN (SELECT st.serv_id FROM nlab.serv_test st WHERE st.test_id = lt.id)
                         JOIN nlab.lab_research lr ON lr.serv_id = serv.id AND lr.lab_sample_id = ls.id
                WHERE p.keyid = ?
                  AND trunc(r.collectdate) >= ?
                  AND tr.value IS NOT NULL
                  AND ls.status IN ('VALIDATED','REPORTED', 'ARCHIVED', 'RESULT')
                  AND tr.validation_status = 1
                  AND lt.id NOT IN (3787)
                ORDER BY to_char(r.collectdate, 'dd.mm.yyyy'), lt.view_sortcode
                """;

        return jdbcTemplate.query(sql, new Object[]{patientID, analysisDate}, (rs, rowNum) -> {
            return new LabTestResultDto(
                    rs.getString("pnum"),
                    rs.getString("fio"),
                    rs.getString("bdate"),
                    rs.getString("address"),
                    rs.getString("ids"),
                    rs.getString("material"),
                    rs.getString("lpu"),
                    rs.getString("otd"),
                    rs.getString("executors"),
                    rs.getString("usl"),
                    rs.getString("labtest"),
                    rs.getString("value"),
                    rs.getString("units"),
                    rs.getString("norm"),
                    rs.getString("collecdate"),
                    rs.getString("finisdate"),
                    rs.getString("printdate"),
                    rs.getInt("patstatus"),
                    rs.getString("test_group"),
                    rs.getInt("view_sortcode"),
                    rs.getString("tg_sort"),
                    rs.getString("code_usl"),
                    rs.getString("comments"),
                    rs.getString("device"),
                    rs.getString("vrachizmo"),
                    rs.getString("otdeleniemo"),
                    rs.getString("LICENSE"),
                    rs.getString("RDCADDRESS"),
                    rs.getString("HOTLINE"),
                    rs.getString("CALLCENTER"),
                    rs.getString("SITE"),
                    rs.getString("EMAIL"),
                    rs.getString("SPRAVOCHNAYA"),
                    rs.getString("MO")
            );
        });
    }

    //Получаем данные для анализов, которые в работе из БД
    public List<LabResultViewDto> findWorkingResultsByPatientIdAndDate(Long patientID, LocalDate analysisDate) {
        String sql = """
                select distinct
                ls.ids as ids,
                s.text as material,
                coalesce(trim((select ld.text from nlab.lab_dep ld where ld.id = ls.target_lab_dep_id)),'Нет') as otd,
                serv.text as usl,
                to_char(ls.collect_date, 'dd.mm.yyyy hh24:mi') as collecdate,
                'В работе' as finisdate
                from
                nlab.test_result tr
                join nlab.lab_sample ls on tr.lab_sample_id = ls.id
                join solution_lab.research r on ls.ariadna_id = r.id
                join nlab.specimen s on ls.specimen_id = s.id
                join nlab.lab_test lt on tr.lab_test_id = lt.id
                join nlab.serv serv on serv.id in (select st.serv_id from nlab.serv_test st where st.test_id = lt.id)
                join nlab.lab_research lr on lr.serv_id = serv.id and lr.lab_sample_id = ls.id
                WHERE r.patient_id = ?
                  AND trunc(r.collectdate) >= ?
                  and ls.status in ('WORKING', 'READY', 'RESULT')
                  and tr.validation_status = 0
                """;

        return jdbcTemplate.query(sql, new Object[]{patientID, analysisDate}, (rs, rowNum) -> {
            return new LabResultViewDto(
                    rs.getString("ids"),
                    rs.getString("material"),
                    rs.getString("otd"),
                    rs.getString("usl"),
                    rs.getString("collecdate"),
                    rs.getString("finisdate")
            );
        });
    }
}
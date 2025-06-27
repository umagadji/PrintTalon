package ru.rdc.PrintTalon.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.rdc.PrintTalon.dto.LabResultViewDto;
import ru.rdc.PrintTalon.dto.LabTestResultDto;
import ru.rdc.PrintTalon.repository.LabResultRepository;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LabResultService {
    private final LabResultRepository repository;

    public Map<String, List<LabTestResultDto>> groupResults(String snils, LocalDate date) {
        List<LabTestResultDto> results = repository.findResultsBySnilsAndDate(snils, date);

        // Группировка по ключу (ids, material, otd, collecdate)
        Map<String, List<LabTestResultDto>> grouped = results.stream()
                .collect(Collectors.groupingBy(r ->
                        r.getIds() + "|" + r.getMaterial() + "|" + r.getOtd() + "|" + r.getCollecdate()
                ));

        return grouped;
    }

    public List<Path> generatePdfReports(String snils, LocalDate date) {
        Map<String, List<LabTestResultDto>> groupedData = groupResults(snils, date);
        List<Path> pdfPaths = new ArrayList<>();
        int idx = 1;

        for (Map.Entry<String, List<LabTestResultDto>> entry : groupedData.entrySet()) {
            List<LabTestResultDto> group = entry.getValue();

            List<LabTestResultDto> afpHgchGroup = group.stream()
                    .filter(r -> List.of("516945", "521606", "516947", "521608").contains(r.getCodeUsl()))
                    .toList();

            List<LabTestResultDto> vpchGroup = group.stream()
                    .filter(r -> List.of("517062", "521242", "521243", "517026").contains(r.getCodeUsl()))
                    .toList();

            List<LabTestResultDto> regularGroup = group.stream()
                    .filter(r -> !List.of("516945", "521606", "516947", "521608", "517062", "521242", "521243", "517026")
                            .contains(r.getCodeUsl()))
                    .toList();

            if (!afpHgchGroup.isEmpty()) {
                Path path = createLabResultsAfphgchPdf(afpHgchGroup, snils, date, idx);
                pdfPaths.add(path);
            }
            if (!vpchGroup.isEmpty()) {
                Path path = createLabResultsVpchPdf(vpchGroup, snils, date, idx);
                pdfPaths.add(path);
            }
            if (!regularGroup.isEmpty()) {
                Path path = createLabResultsStandardPdf(regularGroup, snils, date, idx);
                pdfPaths.add(path);
            }

            idx++;
        }

        return pdfPaths;
    }

    public Map<String, List<LabResultViewDto>> getCategorizedResultsForView(String snils, LocalDate date) {
        List<LabTestResultDto> results = repository.findResultsBySnilsAndDate(snils, date);

        // Коды для категорий
        Set<String> afpHgchCodes = Set.of("516945", "521606", "516947", "521608");
        Set<String> vpchCodes = Set.of("517062", "521242", "521243", "517026");

        Map<String, List<LabResultViewDto>> categorized = new LinkedHashMap<>();
        categorized.put("afp_hgch", new ArrayList<>());
        categorized.put("vpch", new ArrayList<>());
        categorized.put("regular", new ArrayList<>());

        // Группировка по IDS (может попасть в несколько категорий)
        Map<String, List<LabTestResultDto>> groupedByIds = results.stream()
                .collect(Collectors.groupingBy(LabTestResultDto::getIds));

        for (var entry : groupedByIds.entrySet()) {
            String ids = entry.getKey();
            List<LabTestResultDto> group = entry.getValue();

            // Категория 1
            List<LabTestResultDto> afpGroup = group.stream()
                    .filter(r -> afpHgchCodes.contains(r.getCodeUsl()))
                    .toList();
            if (!afpGroup.isEmpty()) {
                LabTestResultDto first = afpGroup.get(0);
                String usl = afpGroup.stream().map(LabTestResultDto::getUsl).distinct().collect(Collectors.joining(", "));
                categorized.get("afp_hgch").add(new LabResultViewDto(ids, first.getMaterial(), first.getOtd(), usl,
                        first.getCollecdate(), first.getFinisdate()));
            }

            // Категория 2
            List<LabTestResultDto> vpchGroup = group.stream()
                    .filter(r -> vpchCodes.contains(r.getCodeUsl()))
                    .toList();
            if (!vpchGroup.isEmpty()) {
                LabTestResultDto first = vpchGroup.get(0);
                String usl = vpchGroup.stream().map(LabTestResultDto::getUsl).distinct().collect(Collectors.joining(", "));
                categorized.get("vpch").add(new LabResultViewDto(ids, first.getMaterial(), first.getOtd(), usl,
                        first.getCollecdate(), first.getFinisdate()));
            }

            // Остальное
            List<LabTestResultDto> otherGroup = group.stream()
                    .filter(r -> !afpHgchCodes.contains(r.getCodeUsl()) && !vpchCodes.contains(r.getCodeUsl()))
                    .toList();
            if (!otherGroup.isEmpty()) {
                LabTestResultDto first = otherGroup.get(0);
                String usl = otherGroup.stream().map(LabTestResultDto::getUsl).distinct().collect(Collectors.joining(", "));
                categorized.get("regular").add(new LabResultViewDto(ids, first.getMaterial(), first.getOtd(), usl,
                        first.getCollecdate(), first.getFinisdate()));
            }
        }

        return categorized;
    }

    /**
     * Получает все результаты анализов по СНИЛСу и дате
     * @param snils СНИЛС пациента
     * @param analysisDate Дата анализа
     * @return Список результатов анализов
     */
    public List<LabTestResultDto> findResultsBySnilsAndDate(String snils, LocalDate analysisDate) {
        return repository.findResultsBySnilsAndDate(snils, analysisDate);
    }

    // Заглушки — реализуй генерацию PDF с помощью iText, FlyingSaucer, Jasper и т.п.
    private Path createLabResultsAfphgchPdf(List<LabTestResultDto> data, String snils, LocalDate date, int idx) {
        // Генерация PDF и возврат пути к файлу
        return Path.of("pdf/lab_results_" + snils + "_" + date + "_afphgch_" + idx + ".pdf");
    }

    private Path createLabResultsVpchPdf(List<LabTestResultDto> data, String snils, LocalDate date, int idx) {
        return Path.of("pdf/lab_results_" + snils + "_" + date + "_vpch_" + idx + ".pdf");
    }

    private Path createLabResultsStandardPdf(List<LabTestResultDto> data, String snils, LocalDate date, int idx) {
        return Path.of("pdf/lab_results_" + snils + "_" + date + "_" + idx + ".pdf");
    }
}

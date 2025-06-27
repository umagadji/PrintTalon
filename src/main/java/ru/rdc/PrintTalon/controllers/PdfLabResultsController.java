package ru.rdc.PrintTalon.controllers;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageConfig;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.itextpdf.text.*;
import com.itextpdf.text.pdf.*;
import com.itextpdf.text.pdf.qrcode.ErrorCorrectionLevel;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import ru.rdc.PrintTalon.dto.LabTestResultDto;
import ru.rdc.PrintTalon.services.LabResultService;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Controller
@RequiredArgsConstructor
public class PdfLabResultsController {

    private final LabResultService labResultService;

    /**
     * Генерирует PDF-файл с результатами лабораторных исследований по заданным параметрам.
     *
     * @param ids      Идентификатор образца (исследования)
     * @param snils    СНИЛС пациента
     * @param date     Дата проведения исследования
     * @param group    Группа исследований (vpch, afp_hgch, other)
     * @param response HTTP-ответ, в который будет записан PDF-файл
     * @throws IOException В случае ошибки чтения ресурсов или записи в поток ответа
     */
    @GetMapping("/labresults/pdf/{ids}")
    public void generatePdf(
            @PathVariable String ids,
            @RequestParam String snils,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) String group,
            HttpServletResponse response) throws IOException {

        // Преобразуем "regular" в "other" для совместимости с логикой фильтрации
        if ("regular".equalsIgnoreCase(group)) {
            group = "other";
        }

        // Получаем все результаты для заданного СНИЛС и даты, фильтруем по ids (идентификатору анализа)
        List<LabTestResultDto> results = labResultService.findResultsBySnilsAndDate(snils, date)
                .stream()
                .filter(r -> r.getIds().equals(ids))
                .collect(Collectors.toList());

        // Если не найдено результатов, возвращаем 404
        if (results.isEmpty()) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "Нет данных для печати");
            return;
        }

        // Списки кодов для ВПЧ и АФП+ХГЧ групп
        List<String> afpHgchCodes = List.of("516945", "521606", "516947", "521608");
        List<String> vpchCodes = List.of("517062", "521242", "521243", "517026");

        // Отфильтровываем результаты по запрошенной группе
        List<LabTestResultDto> filteredResults;
        if ("vpch".equalsIgnoreCase(group)) {
            filteredResults = results.stream()
                    .filter(r -> vpchCodes.contains(r.getCodeUsl()))
                    .toList();
        } else if ("afp_hgch".equalsIgnoreCase(group)) {
            filteredResults = results.stream()
                    .filter(r -> afpHgchCodes.contains(r.getCodeUsl()))
                    .toList();
        } else if ("other".equalsIgnoreCase(group)) {
            filteredResults = results.stream()
                    .filter(r -> !vpchCodes.contains(r.getCodeUsl()) && !afpHgchCodes.contains(r.getCodeUsl()))
                    .toList();
        } else {
            // Если передана неизвестная группа — ошибка
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Неизвестная группа результатов");
            return;
        }

        // Если после фильтрации ничего не осталось — возвращаем 404
        if (filteredResults.isEmpty()) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "Нет данных для печати для выбранной группы");
            return;
        }

        // Генерация PDF-документа
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            // Создаем документ A4 с небольшими отступами
            Document document = new Document(PageSize.A4, mmToPoints(8f), mmToPoints(8f), mmToPoints(4f), mmToPoints(4f));
            PdfWriter.getInstance(document, baos);
            document.open();

            // Загружаем шрифт Ubuntu-Regular из ресурсов
            Resource ubuntuRegularResource = new ClassPathResource("fonts/Ubuntu-Regular.ttf");
            InputStream ubuntuRegularStream = ubuntuRegularResource.getInputStream();
            byte[] ubuntuRegularBytes = ubuntuRegularStream.readAllBytes();
            BaseFont ubuntuRegularFont = BaseFont.createFont(
                    "Ubuntu-Regular.ttf", BaseFont.IDENTITY_H, BaseFont.EMBEDDED, BaseFont.CACHED, ubuntuRegularBytes, null);

            // Загружаем шрифт Ubuntu-Medium из ресурсов
            Resource ubuntuMediumResource = new ClassPathResource("fonts/Ubuntu-Medium.ttf");
            InputStream ubuntuMediumStream = ubuntuMediumResource.getInputStream();
            byte[] ubuntuMediumBytes = ubuntuMediumStream.readAllBytes();
            BaseFont ubuntuMediumFont = BaseFont.createFont(
                    "Ubuntu-Medium.ttf", BaseFont.IDENTITY_H, BaseFont.EMBEDDED, BaseFont.CACHED, ubuntuMediumBytes, null);

            // Добавляем в PDF шапку документа и информацию об образце
            addHeader(document, ubuntuRegularFont, ubuntuMediumFont, filteredResults);
            addOrderInfo(document, ubuntuRegularFont, ubuntuMediumFont, filteredResults);

            document.add(Chunk.NEWLINE); // просто одна пустая строка

            // Добавляем таблицу результатов в зависимости от группы
            if ("vpch".equalsIgnoreCase(group)) {
                addVpchResultsTable(document, ubuntuRegularFont, ubuntuMediumFont, filteredResults);
            } else if ("afp_hgch".equalsIgnoreCase(group)) {
                addResultsAfpKhGch(document, ubuntuRegularFont, ubuntuMediumFont, filteredResults);
            } else if ("other".equalsIgnoreCase(group)) {
                addResultsTable(document, ubuntuRegularFont, ubuntuMediumFont, filteredResults);
            }

            document.add(Chunk.NEWLINE); // просто одна пустая строка

            // Добавляем подвал с общей информацией
            addFooter(document, ubuntuRegularFont, results);

            // Закрываем документ
            document.close();

            // Устанавливаем заголовки и отправляем PDF клиенту
            response.setContentType("application/pdf");
            response.setHeader("Content-Disposition", "inline; filename=lab_results_" + ids + ".pdf");
            response.setContentLength(baos.size());
            response.getOutputStream().write(baos.toByteArray());
            response.getOutputStream().flush();

        } catch (DocumentException e) {
            log.error("Ошибка при генерации PDF", e);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Ошибка генерации PDF");
        }
    }

    // Функция конвертации мм в пункты
    private float mmToPoints(float mm) {
        return mm * 2.83465f;
    }

    //Метод для добавления шапки
    private void addHeader(Document document, BaseFont baseFont, BaseFont boldFont, List<LabTestResultDto> results) throws DocumentException, IOException {
        if (results == null || results.isEmpty()) return;

        LabTestResultDto labTestResultDto = results.get(0);

        Font font8_5 = new Font(baseFont, 8.5f);

        PdfPTable table = new PdfPTable(4);
        // Задаём ширины колонок в миллиметрах
        float[] widthsMm = {42f, 80f, 55f, 18f};

        // Переводим в пункты
        float[] widthsPoints = new float[widthsMm.length];
        for (int i = 0; i < widthsMm.length; i++) {
            widthsPoints[i] = mmToPoints(widthsMm[i]);
        }

        // Общая ширина таблицы — сумма ширин колонок в пунктах
        float totalWidth = 0f;
        for (float w : widthsPoints) totalWidth += w;

        // Устанавливаем ширину таблицы и фиксируем её
        table.setTotalWidth(totalWidth);
        table.setLockedWidth(true);

        // Устанавливаем ширины колонок
        table.setWidths(widthsPoints);

        // Логотип (1 колонка, rowspan 4)
        Image logo = Image.getInstance(Objects.requireNonNull(getClass().getResource("/static/images/logo.png")));
        logo.scaleAbsolute(mmToPoints(39f), mmToPoints(15f));
        PdfPCell logoCell = new PdfPCell(logo, true);
        logoCell.setBorder(Rectangle.NO_BORDER);
        logoCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        logoCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        logoCell.setRowspan(4);
        table.addCell(logoCell);

        // 1-я строка, 2-я колонка
        PdfPCell cell1_2 = new PdfPCell(new Phrase(labTestResultDto.getRdcMo(), font8_5));
        cell1_2.setBorder(Rectangle.NO_BORDER);
        cell1_2.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell1_2.setPaddingLeft(mmToPoints(2f));
        table.addCell(cell1_2);

        // 1-я строка, 3-я колонка
        PdfPCell cell1_3 = new PdfPCell(new Phrase(labTestResultDto.getRdcCallcenter(), font8_5));
        cell1_3.setBorder(Rectangle.NO_BORDER);
        cell1_3.setVerticalAlignment(Element.ALIGN_RIGHT);
        //cell1_3.setHorizontalAlignment(Element.ALIGN_RIGHT);
        table.addCell(cell1_3);

        // 1-я строка, 4-я колонка (QR код rowspan=4)
        Image qrCode = Image.getInstance(Objects.requireNonNull(getClass().getResource("/static/images/qr.png")));
        PdfPCell qrCell = new PdfPCell(qrCode, true);
        qrCode.scaleToFit(mmToPoints(18f), mmToPoints(60f)); // высота 4 строк по 15 мм = 60 мм
        qrCell.setPadding(0);
        qrCell.setBorder(Rectangle.NO_BORDER);
        qrCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        qrCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        qrCell.setRowspan(4);
        table.addCell(qrCell);

        // 2-я строка, 2-я колонка
        PdfPCell cell2_2 = new PdfPCell(new Phrase(labTestResultDto.getRdcAddress(), font8_5));
        cell2_2.setBorder(Rectangle.NO_BORDER);
        cell2_2.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell2_2.setPaddingLeft(mmToPoints(2f));
        table.addCell(cell2_2);

        // 2-я строка, 3-я колонка
        PdfPCell cell2_3 = new PdfPCell(new Phrase(labTestResultDto.getRdcSite(), font8_5));
        cell2_3.setBorder(Rectangle.NO_BORDER);
        cell2_3.setVerticalAlignment(Element.ALIGN_RIGHT);
        //cell2_3.setHorizontalAlignment(Element.ALIGN_RIGHT);
        table.addCell(cell2_3);

        // 3-я строка, 2-я колонка
        PdfPCell cell3_2 = new PdfPCell(new Phrase(labTestResultDto.getRdcLicense(), font8_5));
        cell3_2.setBorder(Rectangle.NO_BORDER);
        cell3_2.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell3_2.setPaddingLeft(mmToPoints(2f));
        table.addCell(cell3_2);

        // 3-я строка, 3-я колонка
        PdfPCell cell3_3 = new PdfPCell(new Phrase(labTestResultDto.getRdcEmail(), font8_5));
        cell3_3.setBorder(Rectangle.NO_BORDER);
        cell3_3.setVerticalAlignment(Element.ALIGN_RIGHT);
        //cell3_3.setHorizontalAlignment(Element.ALIGN_RIGHT);
        table.addCell(cell3_3);

        // 4-я строка, 2-я колонка
        PdfPCell cell4_2 = new PdfPCell(new Phrase(labTestResultDto.getRdcHotline(), font8_5));
        cell4_2.setBorder(Rectangle.NO_BORDER);
        cell4_2.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell4_2.setPaddingLeft(mmToPoints(2f));
        table.addCell(cell4_2);

        // 4-я строка, 3-я колонка
        PdfPCell cell4_3 = new PdfPCell(new Phrase(labTestResultDto.getRdcSprav(), font8_5));
        cell4_3.setBorder(Rectangle.NO_BORDER);
        cell4_3.setVerticalAlignment(Element.ALIGN_RIGHT);
        //cell4_3.setHorizontalAlignment(Element.ALIGN_RIGHT);
        table.addCell(cell4_3);

        // Добавляем таблицу в документ
        document.add(table);

        // Нижняя линия под таблицей
        PdfPCell lineCell = new PdfPCell();
        lineCell.setColspan(4);
        lineCell.setBorder(Rectangle.BOTTOM);
        lineCell.setBorderWidthBottom(0.5f);
        lineCell.setFixedHeight(5f);
        PdfPTable lineTable = new PdfPTable(1);
        lineTable.setWidthPercentage(100);
        lineTable.addCell(lineCell);
        document.add(lineTable);
    }

    //Метод для добавления информации о заказе после шапки
    private void addOrderInfo(Document document, BaseFont baseFont, BaseFont boldFont, List<LabTestResultDto> results) throws DocumentException {
        Font font9 = new Font(baseFont, 9f);
        Font fontBold = new Font(boldFont, 9f);

        PdfPTable table = new PdfPTable(2);
        // Задаём ширины колонок в миллиметрах
        float[] widthsMm = {125f, 70f};

        // Переводим в пункты
        float[] widthsPoints = new float[widthsMm.length];
        for (int i = 0; i < widthsMm.length; i++) {
            widthsPoints[i] = mmToPoints(widthsMm[i]);
        }

        // Общая ширина таблицы — сумма ширин колонок в пунктах
        float totalWidth = 0f;
        for (float w : widthsPoints) totalWidth += w;

        // Устанавливаем ширину таблицы и фиксируем её
        table.setTotalWidth(totalWidth);
        table.setLockedWidth(true);
        table.setWidths(widthsPoints);

        if (results != null && !results.isEmpty()) {

            LabTestResultDto firstResult = results.get(0);

            // "№ карты" — на всю ширину таблицы (2 колонки)
            PdfPCell pnumCell = createCell("№ карты: " + firstResult.getPnum(), fontBold);
            pnumCell.setColspan(2);
            table.addCell(pnumCell);

            // "Ф.И.О." — на всю ширину таблицы (2 колонки), в следующей строке
            PdfPCell fioCell = createCell("Ф.И.О.: " + firstResult.getFio(), font9);
            fioCell.setColspan(2);
            table.addCell(fioCell);

            table.addCell(createCell("Возраст: " + firstResult.getBdate(), font9));

            PdfPCell dateregCell = createCell("Дата регистрации образца: " + firstResult.getCollecdate(), font9);
            dateregCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            table.addCell(dateregCell);

            table.addCell(createCell("Образец №: " + firstResult.getIds(), font9));

            PdfPCell datefinishCell = createCell("Дата выполнения: " + firstResult.getFinisdate(), font9);
            datefinishCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            table.addCell(datefinishCell);

            // Левая ячейка — "Материал:"
            table.addCell(createCell("Материал: " + firstResult.getMaterial(), font9));

            // Формируем строку с датой
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
            String formattedNow = LocalDateTime.now().format(formatter);
            String rightText = "Дата печати результата: " + formattedNow;

            PdfPCell dateprintCell = createCell(rightText, font9);
            dateprintCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            table.addCell(dateprintCell);

            // Направившее ЛПУ (на всю ширину)
            PdfPCell lpuCell = createCell("Направившее ЛПУ: " + firstResult.getLpu(), font9);
            lpuCell.setColspan(2);
            table.addCell(lpuCell);

            // Адрес (на всю ширину)
            PdfPCell addressCell = createCell("Адрес: " + firstResult.getAddress(), font9);
            addressCell.setColspan(2);
            table.addCell(addressCell);

            if ((firstResult.getOtdeleniemo() != null && !firstResult.getOtdeleniemo().trim().isEmpty()) ||
                    (firstResult.getVrachizmo() != null && !firstResult.getVrachizmo().trim().isEmpty())) {

                // Формируем строку с разделением через " / "
                StringBuilder sb = new StringBuilder();

                if (firstResult.getOtdeleniemo() != null && !firstResult.getOtdeleniemo().trim().isEmpty()) {
                    sb.append("Отделение МО: ").append(firstResult.getOtdeleniemo().trim());
                }

                if (firstResult.getVrachizmo() != null && !firstResult.getVrachizmo().trim().isEmpty()) {
                    if (sb.length() > 0) {
                        sb.append(" / ");
                    }
                    sb.append("Направивший врач из МО: ").append(firstResult.getVrachizmo().trim());
                }

                PdfPCell combinedCell = createCell(sb.toString(), font9);
                combinedCell.setColspan(2);  // занять всю ширину из 2 колонок
                table.addCell(combinedCell);
            }

            // Отдел лаборатории (на всю ширину)
            String labDepartment = firstResult.getOtd(); // отдел лаборатории, всегда есть
            String executor = firstResult.getExecutors(); // исполнитель, может быть null или пустым
            String text = "Отдел лаборатории: " + labDepartment;
            if (executor != null && !executor.isEmpty()) {
                text += " / Исполнитель: " + executor;
            }
            PdfPCell labDeptCell = createCell(text, font9);
            labDeptCell.setColspan(2);
            table.addCell(labDeptCell);

            // Оборудование (на всю ширину)
            if (firstResult.getDevice() != null && !firstResult.getDevice().trim().isEmpty()) {

                Optional<LabTestResultDto> labTestResultDtoOptional = results.stream().filter(labTestResultDto -> labTestResultDto.getLabtest().toLowerCase().contains("реактив")).findFirst();
                String strReaktiv = labTestResultDtoOptional.map(labTestResultDto -> " / Реактив: " + labTestResultDto.getValue()).orElse("");

                PdfPCell devicetCell = createCell("Исследование выполнено на: " + firstResult.getDevice() + strReaktiv, fontBold);
                devicetCell.setColspan(2);
                table.addCell(devicetCell);
            }
        }

        // Добавляем таблицу в документ
        document.add(table);

        // Добавляем управляемый вертикальный отступ после таблицы
        PdfPTable spacer = new PdfPTable(1);
        spacer.setWidthPercentage(100);
        PdfPCell spacerCell = new PdfPCell(new Phrase(" "));
        spacerCell.setFixedHeight(6f); // отступ в пунктах (можно уменьшить или увеличить)
        spacerCell.setBorder(Rectangle.NO_BORDER);
        spacer.addCell(spacerCell);
        document.add(spacer);
    }

    //Метод для вывода стандартного бланка результатов
    private void addResultsTable(Document document, BaseFont baseFont, BaseFont boldFont, List<LabTestResultDto> results) throws DocumentException {
        if (results == null || results.isEmpty()) return;

        Font fontNormal = new Font(baseFont, 9f);
        Font fontBold = new Font(boldFont, 9f);
        BaseColor lightGrey = new BaseColor(242, 242, 242);
        BaseColor lightRed = new BaseColor(255, 204, 204);

        // Группируем результаты по testGroup
        Map<String, List<LabTestResultDto>> grouped = new LinkedHashMap<>();
        for (LabTestResultDto r : results) {
            grouped.computeIfAbsent(r.getTestGroup(), k -> new ArrayList<>()).add(r);
        }

        // Сортируем группы по tgSort
        List<Map.Entry<String, List<LabTestResultDto>>> sortedGroups = grouped.entrySet().stream()
                .sorted(Comparator.comparingInt(entry ->
                        !entry.getValue().isEmpty() ? Integer.parseInt(entry.getValue().get(0).getTgSort()) : 0))
                .collect(Collectors.toList());

        // Создаём таблицу с 4 столбцами
        PdfPTable table = new PdfPTable(4);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{95f, 40f, 20f, 40f}); // ширины колонок в пунктах, примерно как в Python mm

        // Добавляем заголовки столбцов с серым фоном
        addHeaderCell(table, "Показатель", fontBold, lightGrey);
        addHeaderCell(table, "Результат", fontBold, lightGrey);
        addHeaderCell(table, "Ед. изм.", fontBold, lightGrey);
        addHeaderCell(table, "Реф. интервал", fontBold, lightGrey);

        // Индекс текущей строки для стилей (нужно для подсветки и объединения ячеек)
        int currentRow = 1;

        // Для объединения строк с комментариями (индексы строк)
        List<int[]> commentSpans = new ArrayList<>(); // {startRow, endRow}
        List<Integer> firstColumnSpans = new ArrayList<>(); // индексы строк с объединением первой колонки

        // Остальной код обработки (теперь с sortedGroups вместо grouped)
        for (Map.Entry<String, List<LabTestResultDto>> groupEntry : sortedGroups) {
            String groupName = groupEntry.getKey();
            List<LabTestResultDto> groupResults = groupEntry.getValue();

            if (groupName.startsWith("Реактив")) {
                continue; // пропускаем группы, начинающиеся с "Реактив"
            }

            // Добавляем строку с названием группы, занимающей все 4 колонки
            PdfPCell groupCell = new PdfPCell(new Phrase(groupName, fontBold));
            groupCell.setColspan(4);
            groupCell.setHorizontalAlignment(Element.ALIGN_CENTER);
            groupCell.setBackgroundColor(lightGrey);
            groupCell.setPadding(5);
            groupCell.setBorderWidth(0.1f);
            table.addCell(groupCell);
            currentRow++;

            // Сортируем тесты в группе по viewSortCode (предположим геттер getViewSortCode)
            groupResults.sort(Comparator.comparingInt(LabTestResultDto::getViewSortcode));

            for (int i = 0; i < groupResults.size(); i++) {
                LabTestResultDto r = groupResults.get(i);

                if (r.getLabtest() != null && r.getLabtest().startsWith("Реактив")) {
                    continue;
                }

                // Создаем ячейки
                PdfPCell testCell = new PdfPCell(new Phrase(r.getLabtest() != null ? r.getLabtest() : "", fontNormal));
                testCell.setBorderWidth(0.1f);
                PdfPCell valueCell = new PdfPCell(new Phrase(r.getValue() != null ? r.getValue() : "", fontNormal));
                valueCell.setBorderWidth(0.1f);
                PdfPCell unitCell = new PdfPCell(new Phrase(r.getUnits() != null ? r.getUnits() : "", fontNormal));
                unitCell.setBorderWidth(0.1f);
                PdfPCell normCell = new PdfPCell(new Phrase(r.getNorm() != null ? r.getNorm() : "", fontNormal));
                normCell.setBorderWidth(0.1f);

                // Если patstatus != 0 — подсвечиваем всю строку светло-красным
                if (r.getPatstatus() != 0) {
                    //testCell.setBackgroundColor(lightRed);
                    valueCell.setBackgroundColor(lightRed);
                    //unitCell.setBackgroundColor(lightRed);
                    //normCell.setBackgroundColor(lightRed);
                }

                // Добавляем ячейки в таблицу
                table.addCell(testCell);
                table.addCell(valueCell);
                table.addCell(unitCell);
                table.addCell(normCell);

                currentRow++;

                // Если есть комментарий, добавляем отдельную строку
                if (r.getComments() != null && !r.getComments().isEmpty()) {
                    // Пустая ячейка для "Показатель"
                    PdfPCell emptyCell = new PdfPCell(new Phrase("", fontNormal));
                    emptyCell.setBorderWidth(0.1f);
                    table.addCell(emptyCell);

                    // Комментарий — объединяем 3 колонки справа
                    PdfPCell commentCell = new PdfPCell(new Phrase(r.getComments(), fontNormal));
                    commentCell.setColspan(3);
                    commentCell.setPaddingTop(2);
                    commentCell.setPaddingBottom(2);
                    commentCell.setBorderWidth(0.1f);
                    table.addCell(commentCell);

                    commentSpans.add(new int[]{currentRow, currentRow});
                    firstColumnSpans.add(currentRow - 1); // строка с результатом, где нужно объединить первую ячейку с ячейкой под комментарием

                    currentRow++;
                }
            }
        }

        table.setSpacingAfter(0f); // обнуляем отступ после таблицы
        table.setSpacingBefore(0f); // обнуляем отступ перед таблицей (если нужно)

        document.add(table);
    }

    //Метод для вывода результатов ВПЧ
    private void addVpchResultsTable(Document document, BaseFont baseFont, BaseFont boldFont, List<LabTestResultDto> results) throws DocumentException {
        if (results == null || results.isEmpty()) return;

        Font fontNormal = new Font(baseFont, 9f);
        Font fontBold = new Font(boldFont, 9f);
        BaseColor lightGrey = new BaseColor(222, 222, 222);
        BaseColor lightRed = new BaseColor(255, 204, 204);

        PdfPTable table = new PdfPTable(3);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{45f, 75f, 75f});
        table.setSpacingBefore(10f);

        // Первая строка заголовка
        PdfPCell header1_1 = new PdfPCell(new Phrase("Показатель", fontBold));
        header1_1.setRowspan(2);
        header1_1.setBackgroundColor(lightGrey);
        header1_1.setHorizontalAlignment(Element.ALIGN_CENTER);
        header1_1.setVerticalAlignment(Element.ALIGN_MIDDLE);
        table.addCell(header1_1);

        PdfPCell header1_2 = new PdfPCell(new Phrase("Результаты", fontBold));
        header1_2.setColspan(2);
        header1_2.setBackgroundColor(lightGrey);
        header1_2.setHorizontalAlignment(Element.ALIGN_CENTER);
        header1_2.setVerticalAlignment(Element.ALIGN_MIDDLE);
        table.addCell(header1_2);

        // Вторая строка заголовка
        PdfPCell header2_1 = new PdfPCell(new Phrase("Относительный, Lg (X/КВМ)", fontBold));
        header2_1.setBackgroundColor(lightGrey);
        header2_1.setHorizontalAlignment(Element.ALIGN_CENTER);
        table.addCell(header2_1);

        PdfPCell header2_2 = new PdfPCell(new Phrase("Абсолютный, Lg (копий/образец)", fontBold));
        header2_2.setBackgroundColor(lightGrey);
        header2_2.setHorizontalAlignment(Element.ALIGN_CENTER);
        table.addCell(header2_2);

        // Третья строка с описанием метода
        PdfPCell methodCell = new PdfPCell(new Phrase("Выявление, типирование и количественное определение вируса папилломы человека методом ПЦР", fontBold));
        methodCell.setColspan(3);
        methodCell.setBackgroundColor(lightGrey);
        methodCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        methodCell.setPadding(4);
        table.addCell(methodCell);

        // Группировка и сортировка
        Map<String, List<LabTestResultDto>> grouped = results.stream()
                .filter(r -> r.getTestGroup() != null && !r.getTestGroup().startsWith("Реактив"))
                .collect(Collectors.groupingBy(
                        LabTestResultDto::getTestGroup,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        int rowIndex = 3; // учли 3 строки заголовка
        List<int[]> redCells = new ArrayList<>(); // (row, col)

        for (Map.Entry<String, List<LabTestResultDto>> group : grouped.entrySet()) {
            List<LabTestResultDto> tests = group.getValue();

            // Сортировка по viewSortCode
            tests.sort(Comparator.comparingInt(LabTestResultDto::getViewSortcode));

            Map<String, String[]> combined = new LinkedHashMap<>();
            String kvmValue = null;

            for (LabTestResultDto r : tests) {
                String labtest = r.getLabtest();
                if (labtest == null || labtest.startsWith("Реактив")) continue;

                String testName = labtest.replace(" (Относительный)", "")
                        .replace(" (Абсолютный)", "");

                if ("КВМ".equals(testName)) {
                    kvmValue = r.getValue();
                    continue;
                }

                combined.putIfAbsent(testName, new String[2]);
                if (labtest.contains("Относительный")) {
                    combined.get(testName)[0] = r.getValue();
                } else if (labtest.contains("Абсолютный")) {
                    combined.get(testName)[1] = r.getValue();
                }
            }

            for (Map.Entry<String, String[]> entry : combined.entrySet()) {
                String name = entry.getKey();
                String valRel = entry.getValue()[0];
                String valAbs = entry.getValue()[1];

                PdfPCell nameCell = new PdfPCell(new Phrase(name, fontNormal));
                PdfPCell relCell = new PdfPCell(new Phrase(valRel != null ? valRel : "", fontNormal));
                PdfPCell absCell = new PdfPCell(new Phrase(valAbs != null ? valAbs : "", fontNormal));

                nameCell.setPadding(3);
                relCell.setPadding(3);
                absCell.setPadding(3);

                table.addCell(nameCell);
                table.addCell(relCell);
                table.addCell(absCell);

                if (valRel != null && !valRel.equalsIgnoreCase("Не обнаружено")) {
                    redCells.add(new int[]{rowIndex, 1});
                }
                if (valAbs != null && !valAbs.equalsIgnoreCase("Не обнаружено")) {
                    redCells.add(new int[]{rowIndex, 2});
                }

                rowIndex++;
            }

            // Добавляем КВМ
            if (kvmValue != null) {
                PdfPCell nameCell = new PdfPCell(new Phrase("КВМ", fontNormal));
                PdfPCell emptyCell = new PdfPCell(new Phrase(""));
                PdfPCell kvmCell = new PdfPCell(new Phrase(kvmValue, fontNormal));

                nameCell.setPadding(3);
                kvmCell.setPadding(3);
                emptyCell.setPadding(3);

                table.addCell(nameCell);
                table.addCell(emptyCell);
                table.addCell(kvmCell);
                rowIndex++;
            }
        }

        // Применяем стили к ячейкам с обнаруженными значениями
        for (int[] rc : redCells) {
            PdfPCell cell = table.getRow(rc[0]).getCells()[rc[1]];
            cell.setBackgroundColor(lightRed);
        }

        document.add(table);

        // Добавляем поясняющий текст
        Paragraph p1 = new Paragraph("* Копий ДНК ВПЧ на 1 клетку (Lg)", fontNormal);
        Paragraph p2 = new Paragraph("Заключение: Клинически значимая концентрация вируса не менее 10^3 копий ДНК ВПЧ на образец", fontNormal);
        Paragraph p3 = new Paragraph("ВИРУС ПАПИЛЛОМЫ ВЫСОКОГО ОНКОГЕННОГО РИСКА (ВПЧ), НР", fontNormal);

        p1.setSpacingBefore(10);
        p2.setSpacingBefore(2);
        p3.setSpacingBefore(10);

        table.setSpacingAfter(0f); // обнуляем отступ после таблицы
        table.setSpacingBefore(0f); // обнуляем отступ перед таблицей (если нужно)

        document.add(p1);
        document.add(p2);
        document.add(p3);
    }

    //Вывод результатов для ХГЧ и АФП
    private void addResultsAfpKhGch(Document document, BaseFont baseFont, BaseFont boldFont, List<LabTestResultDto> results) throws DocumentException {
        if (results == null || results.isEmpty()) return;

        Font fontNormal = new Font(baseFont, 9f);
        Font fontBold = new Font(boldFont, 9f);
        BaseColor lightGrey = new BaseColor(242, 242, 242);
        BaseColor lightRed = new BaseColor(255, 204, 204);

        Map<String, List<LabTestResultDto>> grouped = new LinkedHashMap<>();
        for (LabTestResultDto r : results) {
            grouped.computeIfAbsent(r.getTestGroup(), k -> new ArrayList<>()).add(r);
        }

        PdfPTable table = new PdfPTable(4);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{95f, 40f, 20f, 40f});

        addHeaderCell(table, "Показатель", fontBold, lightGrey);
        addHeaderCell(table, "Результат", fontBold, lightGrey);
        addHeaderCell(table, "Ед. изм.", fontBold, lightGrey);
        addHeaderCell(table, "Реф. интервал", fontBold, lightGrey);

        int currentRow = 1;

        for (Map.Entry<String, List<LabTestResultDto>> groupEntry : grouped.entrySet()) {
            String groupName = groupEntry.getKey();
            List<LabTestResultDto> groupResults = groupEntry.getValue();

            if (groupName.startsWith("Реактив")) continue;

            PdfPCell groupCell = new PdfPCell(new Phrase(groupName, fontBold));
            groupCell.setColspan(4);
            groupCell.setHorizontalAlignment(Element.ALIGN_CENTER);
            groupCell.setBackgroundColor(lightGrey);
            groupCell.setPadding(5);
            groupCell.setBorderWidth(0.1f);
            table.addCell(groupCell);
            currentRow++;

            groupResults.sort(Comparator.comparingInt(LabTestResultDto::getViewSortcode));

            for (LabTestResultDto r : groupResults) {
                if (r.getLabtest() != null && r.getLabtest().startsWith("Реактив")) continue;

                PdfPCell testCell = new PdfPCell(new Phrase(r.getLabtest() != null ? r.getLabtest() : "", fontNormal));
                testCell.setBorderWidth(0.1f);
                PdfPCell valueCell = new PdfPCell(new Phrase(r.getValue() != null ? r.getValue() : "", fontNormal));
                valueCell.setBorderWidth(0.1f);
                PdfPCell unitCell = new PdfPCell(new Phrase(r.getUnits() != null ? r.getUnits() : "", fontNormal));
                unitCell.setBorderWidth(0.1f);
                PdfPCell normCell = new PdfPCell(new Phrase(r.getNorm() != null ? r.getNorm() : "", fontNormal));
                normCell.setBorderWidth(0.1f);

                if (r.getPatstatus() != 0) {
                    //testCell.setBackgroundColor(lightRed);
                    valueCell.setBackgroundColor(lightRed);
                    //unitCell.setBackgroundColor(lightRed);
                    //normCell.setBackgroundColor(lightRed);
                }

                table.addCell(testCell);
                table.addCell(valueCell);
                table.addCell(unitCell);
                table.addCell(normCell);

                currentRow++;

                if (r.getComments() != null && !r.getComments().isEmpty()) {
                    PdfPCell emptyCell = new PdfPCell(new Phrase("", fontNormal));
                    emptyCell.setBorderWidth(0.1f);
                    table.addCell(emptyCell);

                    PdfPCell commentCell = new PdfPCell(new Phrase(r.getComments(), fontNormal));
                    commentCell.setColspan(3);
                    commentCell.setPaddingTop(2);
                    commentCell.setPaddingBottom(2);
                    commentCell.setBorderWidth(0.1f);
                    table.addCell(commentCell);

                    currentRow++;
                }
            }
        }

        document.add(table);

        // ───── Добавление таблицы с нормами ─────
        PdfPTable normsTable = new PdfPTable(4);
        normsTable.setWidthPercentage(100);
        normsTable.setSpacingBefore(10f);
        normsTable.setWidths(new float[]{30f, 30f, 20f, 20f});

        Font fontNorm = new Font(baseFont, 9f);

        PdfPCell titleCell = new PdfPCell(new Phrase("Нормы для беременных", fontBold));
        titleCell.setColspan(4);
        titleCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        titleCell.setPadding(5);
        titleCell.setBackgroundColor(lightGrey);
        normsTable.addCell(titleCell);

        // Заголовок "ХГЧ" и "АФП", каждый на 2 колонки
        PdfPCell hcgHeader = new PdfPCell(new Phrase("ХГЧ", fontBold));
        hcgHeader.setColspan(2);
        hcgHeader.setHorizontalAlignment(Element.ALIGN_CENTER);
        hcgHeader.setPadding(4);
        normsTable.addCell(hcgHeader);

        PdfPCell afpHeader = new PdfPCell(new Phrase("АФП", fontBold));
        afpHeader.setColspan(2);
        afpHeader.setHorizontalAlignment(Element.ALIGN_CENTER);
        afpHeader.setPadding(4);
        normsTable.addCell(afpHeader);

        String[][] normRows = {
                {"1–10 неделя", "50 – 221 796", "14 неделя", "13 – 47"},
                {"11–15 неделя", "15 205 – 227 034", "15 неделя", "17 – 70"},
                {"16–24 неделя", "5 002 – 75 445", "16 неделя", "19 – 62"},
                {"25–40 неделя", "1 501 – 69 208", "17 неделя", "22 – 69"},
                {"", "", "18 неделя", "25 – 60"},
                {"", "", "19 неделя", "28 – 92"},
                {"", "", "20 неделя", "29 – 104"}
        };

        for (String[] row : normRows) {
            for (String cellText : row) {
                PdfPCell cell = new PdfPCell(new Phrase(cellText, fontNorm));
                cell.setPadding(3);
                normsTable.addCell(cell);
            }
        }

        table.setSpacingAfter(0f); // обнуляем отступ после таблицы
        table.setSpacingBefore(0f); // обнуляем отступ перед таблицей (если нужно)

        document.add(normsTable);
    }

    //Добавление подвала
    private void addFooter(Document document, BaseFont baseFont, List<LabTestResultDto> results) throws DocumentException, IOException {
        if (results == null || results.isEmpty()) return;

        Font fontNormal = new Font(baseFont, 9f);
        Font fontItalic = new Font(baseFont, 9f, Font.ITALIC);

        PdfPTable table = new PdfPTable(4);
        // Задаём ширины колонок в миллиметрах
        float[] widthsMm = {95f, 20f, 40f, 40f};

        // Переводим в пункты
        float[] widthsPoints = new float[widthsMm.length];
        for (int i = 0; i < widthsMm.length; i++) {
            widthsPoints[i] = mmToPoints(widthsMm[i]);
        }

        // Общая ширина таблицы — сумма ширин колонок в пунктах
        float totalWidth = 0f;
        for (float w : widthsPoints) totalWidth += w;

        // Устанавливаем ширину таблицы и фиксируем её
        table.setTotalWidth(totalWidth);
        table.setLockedWidth(true);
        table.setWidths(widthsPoints);

        // --------- 1. ТЕКСТ ---------
        PdfPCell textCell = new PdfPCell();
        textCell.setBorder(Rectangle.NO_BORDER);

        Paragraph p1 = new Paragraph("Заведующая лаборатории: Абдуллаева Людмила Магомедовна", fontNormal);
        Paragraph p2 = new Paragraph("* Результат, выходящий за пределы референсных значений", fontItalic);
        Paragraph p3 = new Paragraph("Результаты исследований не являются диагнозом. Необходима консультация специалиста.", fontNormal);

        p1.setSpacingAfter(6f);
        p2.setSpacingAfter(6f);

        textCell.addElement(p1);
        textCell.addElement(p2);
        textCell.addElement(p3);

        table.addCell(textCell);

        // --------- 2. ПОДПИСЬ ---------
        Image signImage = Image.getInstance(Objects.requireNonNull(getClass().getResource("/static/images/sign.png")));
        signImage.scaleToFit(mmToPoints(35f), mmToPoints(35f)); // масштаб до ширины ячейки

        PdfPCell signCell = new PdfPCell(signImage, true);
        signCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        signCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        signCell.setBorder(Rectangle.NO_BORDER);
        table.addCell(signCell);

        if (!results.isEmpty()) {
            LabTestResultDto firstResult = results.get(0);
            String ids = firstResult.getIds();
            String fio = firstResult.getFio();
            String age = firstResult.getBdate();
            String regDate = firstResult.getCollecdate();
            String finishDate = firstResult.getFinisdate();

            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
            String formattedNow = LocalDateTime.now().format(formatter);

            String lpu = firstResult.getLpu();

            // --------- 3. QR-КОД ---------
            String qrContent = String.join("\n",
                    "ГБУ РД \"РДЦ\"",
                    "ФИО: " + fio,
                    "Возраст: " + age,
                    "Образец №: " + ids,
                    "Дата регистрации: " + regDate,
                    "Дата выполнения: " + finishDate,
                    "Дата печати: " + formattedNow,
                    "Направившая МО: " + lpu
            );

            //Вызываем метод для добавления QR кода в подвал
            try {
                addQrCodeToPdfCell(table, qrContent);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

        }

        // --------- 4. ПЕЧАТЬ ---------
        Image stampImage = Image.getInstance(Objects.requireNonNull(getClass().getResource("/static/images/stamp.png")));
        stampImage.scaleToFit(mmToPoints(35f), mmToPoints(35f)); // ширина ячейки

        PdfPCell stampCell = new PdfPCell(stampImage, true);
        stampCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        stampCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        stampCell.setBorder(Rectangle.NO_BORDER);
        table.addCell(stampCell);

        // Добавляем управляемый вертикальный отступ перед подвалом
        PdfPTable spacer = new PdfPTable(1);
        spacer.setWidthPercentage(100);
        PdfPCell spacerCell = new PdfPCell(new Phrase(" "));
        spacerCell.setFixedHeight(6f); // отступ в пунктах (можно уменьшить или увеличить)
        spacerCell.setBorder(Rectangle.NO_BORDER);
        spacer.addCell(spacerCell);
        document.add(spacer);

        document.add(table);
    }

    //Генерация QR кода
    private BufferedImage generateQrCodeImage(String text, int width, int height) throws WriterException {
        Map<EncodeHintType, Object> hints = new HashMap<>();
        hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
        hints.put(EncodeHintType.MARGIN, 0); // <== Убираем белую рамку
        hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M); // Средняя коррекция

        BitMatrix matrix = new MultiFormatWriter().encode(
                text,
                BarcodeFormat.QR_CODE,
                width,
                height,
                hints
        );

        return MatrixToImageWriter.toBufferedImage(matrix, new MatrixToImageConfig());
    }

    //Вставка QR-кода в PDF (в подвал) через iText
    private void addQrCodeToPdfCell(PdfPTable table, String qrText) throws Exception {
        // 1. Сгенерировать QR-код
        BufferedImage qrImage = generateQrCodeImage(qrText, 200, 200); // ширина и высота в px

        // 2. Конвертировать в iText Image
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(qrImage, "png", baos);
        Image itextImage = Image.getInstance(baos.toByteArray());

        // 3. Масштабировать до ширины ячейки (например, 40 мм ≈ 113 pt)
        itextImage.scaleToFit(113f, 113f);

        // 4. Создать ячейку
        PdfPCell qrCell = new PdfPCell(itextImage, true);
        qrCell.setBorder(Rectangle.NO_BORDER);
        qrCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        qrCell.setVerticalAlignment(Element.ALIGN_MIDDLE);

        // 5. Добавить в таблицу
        table.addCell(qrCell);
    }

    // Вспомогательный метод для заголовков
    private void addHeaderCell(PdfPTable table, String text, Font font, BaseColor bgColor) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setBackgroundColor(bgColor);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setBorderWidth(0.1f);
        cell.setPadding(5);
        table.addCell(cell);
    }

    // Вспомогательный метод для создания ячейки с базовыми параметрами
    private PdfPCell createCell(String text, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setPaddingLeft(mmToPoints(0f));
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        return cell;
    }

    @GetMapping("/labresults/print/viewer")
    public String showPdfViewer(
            @RequestParam String pdfUrl,
            @RequestParam(required = false, defaultValue = "false") boolean batch,
            Model model) {

        model.addAttribute("pdfUrl", pdfUrl);
        model.addAttribute("batch", batch);
        return "pdflab-viewer";
    }
}

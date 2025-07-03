package ru.rdc.PrintTalon.controllers;

import com.itextpdf.text.*;
import com.itextpdf.text.pdf.*;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.xhtmlrenderer.pdf.ITextRenderer;
import ru.rdc.PrintTalon.repository.ServicesRepository;
import ru.rdc.PrintTalon.services.PrintStatsService;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;
import java.util.Objects;

@Controller
@RequiredArgsConstructor
public class PdfController {

    private final ServicesRepository servicesRepository;
    private final PrintStatsService printStatsService;

    //Обновленный вариант, где формирование pdf происходит на стороне сервера
    @GetMapping("/print/pdf/{id}")
    public void generatePdfRaw(@PathVariable("id") Long id, HttpServletResponse response) throws Exception {

        //Логируем печать
        printStatsService.logPrint("TALON", Map.of("id", id));

        Map<String, Object> data = servicesRepository.getTalonData(id);

        ByteArrayOutputStream byteStream = new ByteArrayOutputStream();

        // Создаем документ с размерами A4 и малыми отступами (в пунктах)
        float marginLeft = 20f;
        float marginRight = 20f;
        float marginTop = 20f;
        float marginBottom = 20f;

        Document document = new Document(PageSize.A4, marginLeft, marginRight, marginTop, marginBottom);

        PdfWriter.getInstance(document, byteStream);
        document.open();

        // Шрифт
        //InputStream fontStream = getClass().getResourceAsStream("/fonts/timesnewromanpsmt.ttf");
        Resource resource1 = new ClassPathResource("fonts/timesnewromanpsmt.ttf");
        InputStream fontStream = resource1.getInputStream();
        byte[] fontBytes = fontStream.readAllBytes();
        BaseFont baseFont = BaseFont.createFont(
                "timesnewromanpsmt.ttf", BaseFont.IDENTITY_H, BaseFont.EMBEDDED, BaseFont.CACHED, fontBytes, null);
        Font fontSize10 = new Font(baseFont, 10);
        Font fontSize9 = new Font(baseFont, 9);
        Font fontSize8 = new Font(baseFont, 8);
        Font fontSize7 = new Font(baseFont, 7);

        //InputStream timesNewRomanBoldStream = getClass().getResourceAsStream("/fonts/timesnewromanpsmtbold.ttf");
        Resource resource2 = new ClassPathResource("fonts/timesnewromanpsmtbold.ttf");
        InputStream timesNewRomanBoldStream = resource2.getInputStream();
        byte[] timesNewRomanBoldBytes = timesNewRomanBoldStream.readAllBytes();
        BaseFont timesNewRomanBoldFont = BaseFont.createFont("timesnewromanpsmtbold.ttf", BaseFont.IDENTITY_H, BaseFont.EMBEDDED, BaseFont.CACHED, timesNewRomanBoldBytes, null);
        Font timesNewRomanBold10 = new Font(timesNewRomanBoldFont, 10);
        Font timesNewRomanBold8 = new Font(timesNewRomanBoldFont, 8);

        //InputStream barcodeStream = getClass().getResourceAsStream("/fonts/Barcode.ttf");
        Resource resource3 = new ClassPathResource("fonts/Barcode.TTF");
        InputStream barcodeStream = resource3.getInputStream();
        byte[] barcodeBytes = barcodeStream.readAllBytes();
        BaseFont barcodeFont = BaseFont.createFont("Barcode.TTF", BaseFont.IDENTITY_H, BaseFont.EMBEDDED, BaseFont.CACHED, barcodeBytes, null);
        Font barcodeSize20 = new Font(barcodeFont, 20);


        // Таблица из 2 колонок, ширина в процентах
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{70, 30}); // Соотношение ширины колонок

        Paragraph emptyLine = new Paragraph();

        // ----------- 1-я строка: LPU и BARCODE (с объединением по высоте) -------------
        PdfPCell lpuCell = new PdfPCell(new Phrase((data.get("LPU") != null && !((String) data.get("LPU")).trim().isEmpty())
                ? (String) data.get("LPU")
                : "", fontSize10));
        lpuCell.setBorder(Rectangle.NO_BORDER);
        lpuCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        table.addCell(lpuCell);

        // Создаем ячейку для BARCODE с объединением строк (rowspan = 2)
        PdfPCell barcodeCell = new PdfPCell(new Phrase((data.get("BARCODE") != null && !((String) data.get("BARCODE")).trim().isEmpty())
                ? (String) data.get("BARCODE")
                : "", barcodeSize20));
        barcodeCell.setRowspan(2); // объединяем 2 строки
        barcodeCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        barcodeCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        barcodeCell.setBorder(Rectangle.NO_BORDER);
        table.addCell(barcodeCell);

        // ----------- 2-я строка: PNUMONE (вторая колонка уже объединена, сюда только левая ячейка) -------------
        PdfPCell pnumCell = new PdfPCell(new Phrase((data.get("PNUMONE") != null && !((String) data.get("PNUMONE")).trim().isEmpty())
                ? (String) data.get("PNUMONE")
                : "", fontSize10));
        pnumCell.setBorder(Rectangle.NO_BORDER);
        table.addCell(pnumCell);

        // ----------- 3-я строка: Врач -------------
        PdfPCell doctorCell = new PdfPCell(new Phrase((data.get("DOCTOR") != null && !((String) data.get("DOCTOR")).trim().isEmpty())
                ? (String) data.get("DOCTOR")
                : "", fontSize10));
        doctorCell.setColspan(2);
        doctorCell.setBorder(Rectangle.NO_BORDER);
        table.addCell(doctorCell);

        // ----------- 4-я строка: Пациент -------------
        PdfPCell fioCell = new PdfPCell(new Phrase((data.get("FIO") != null && !((String) data.get("FIO")).trim().isEmpty())
                ? (String) data.get("FIO")
                : "", fontSize10));
        fioCell.setColspan(2);
        fioCell.setBorder(Rectangle.NO_BORDER);
        table.addCell(fioCell);

        // ----------- 5-я строка: Дата рождения | Полис -------------
        PdfPCell bdCell = new PdfPCell(new Phrase((data.get("BD") != null && !((String) data.get("BD")).trim().isEmpty())
                ? (String) data.get("BD")
                : "", fontSize10));
        bdCell.setColspan(1);
        bdCell.setBorder(Rectangle.NO_BORDER);
        table.addCell(bdCell);

        PdfPCell docCell = new PdfPCell(new Phrase((data.get("DOCUMENT") != null && !((String) data.get("DOCUMENT")).trim().isEmpty())
                ? (String) data.get("DOCUMENT")
                : "", fontSize10));
        docCell.setColspan(1);
        docCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        docCell.setBorder(Rectangle.NO_BORDER);
        table.addCell(docCell);

        // ----------- 6-я строка: Регистратор / Дата регистрации -------------
        PdfPCell regoneCell = new PdfPCell(new Phrase((data.get("REGISTRATORONE") != null && !((String) data.get("REGISTRATORONE")).trim().isEmpty())
                ? (String) data.get("REGISTRATORONE")
                : "", fontSize10));
        regoneCell.setColspan(1);
        regoneCell.setBorder(Rectangle.NO_BORDER);
        table.addCell(regoneCell);

        PdfPCell dataregCell = new PdfPCell(new Phrase((data.get("DATAREG") != null && !((String) data.get("DATAREG")).trim().isEmpty())
                ? (String) data.get("DATAREG")
                : "", fontSize10));
        dataregCell.setColspan(1);
        dataregCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        dataregCell.setBorder(Rectangle.NO_BORDER);
        table.addCell(dataregCell);

        // ----------- 7-я строка: Напечатал / Дата печати -------------
        PdfPCell napechatalCell = new PdfPCell(new Phrase((data.get("NAPECHATAL") != null && !((String) data.get("NAPECHATAL")).trim().isEmpty())
                ? (String) data.get("NAPECHATAL")
                : "", fontSize10));
        napechatalCell.setColspan(1);
        napechatalCell.setBorder(Rectangle.NO_BORDER);
        table.addCell(napechatalCell);

        PdfPCell printdatoneCell = new PdfPCell(new Phrase((data.get("PRINTDATONE") != null && !((String) data.get("PRINTDATONE")).trim().isEmpty())
                ? (String) data.get("PRINTDATONE")
                : "", fontSize10));
        printdatoneCell.setColspan(1);
        printdatoneCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        printdatoneCell.setBorder(Rectangle.NO_BORDER);
        table.addCell(printdatoneCell);

        // ----------- 8-я строка: Направлен -------------
        PdfPCell napravlenCell = new PdfPCell(new Phrase((data.get("NAPRAVLEN") != null && !((String) data.get("NAPRAVLEN")).trim().isEmpty())
                ? (String) data.get("NAPRAVLEN")
                : "", timesNewRomanBold10));
        napravlenCell.setColspan(2);
        napravlenCell.setBorder(Rectangle.NO_BORDER);
        table.addCell(napravlenCell);

        // ----------- 9-я строка: Дата обследования / Номер карты -------------
        PdfPCell dateobsloneCell = new PdfPCell(new Phrase((data.get("DATEOBSLONE") != null && !((String) data.get("DATEOBSLONE")).trim().isEmpty())
                ? (String) data.get("DATEOBSLONE")
                : "", timesNewRomanBold10));
        dateobsloneCell.setColspan(1);
        dateobsloneCell.setBorder(Rectangle.NO_BORDER);
        table.addCell(dateobsloneCell);

        PdfPCell mednumoneCell = new PdfPCell(new Phrase((data.get("MEDNUMONE") != null && !((String) data.get("MEDNUMONE")).trim().isEmpty())
                ? (String) data.get("MEDNUMONE")
                : "", timesNewRomanBold10));
        mednumoneCell.setColspan(1);
        mednumoneCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        mednumoneCell.setBorder(Rectangle.NO_BORDER);
        table.addCell(mednumoneCell);

        // ----------- 10-я строка: Кабинет -------------
        PdfPCell roomoneCell = new PdfPCell(new Phrase((data.get("ROOMONE") != null && !((String) data.get("ROOMONE")).trim().isEmpty())
                ? (String) data.get("ROOMONE")
                : "", timesNewRomanBold10));
        roomoneCell.setColspan(2);
        roomoneCell.setBorder(Rectangle.NO_BORDER);
        table.addCell(roomoneCell);

        // ----------- 11-я строка: Номера направлений -------------
        PdfPCell rdnumbsCell = new PdfPCell(new Phrase((data.get("RDNUMBS") != null && !((String) data.get("RDNUMBS")).trim().isEmpty())
                ? (String) data.get("RDNUMBS")
                : "", timesNewRomanBold10));
        rdnumbsCell.setColspan(2);
        rdnumbsCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        rdnumbsCell.setBorder(Rectangle.NO_BORDER);
        table.addCell(rdnumbsCell);

        // ----------- 12-я строка: Заголовок "Исследования" -------------
        PdfPCell isslTitleCell = new PdfPCell(new Phrase("ИССЛЕДОВАНИЯ:", fontSize10));
        isslTitleCell.setColspan(2);
        isslTitleCell.setBorder(Rectangle.NO_BORDER);
        table.addCell(isslTitleCell);

        // ----------- 13-я строка: Список услуг -------------
        PdfPCell usllistCell = new PdfPCell(new Phrase((data.get("USLLIST") != null && !((String) data.get("USLLIST")).trim().isEmpty())
                ? (String) data.get("USLLIST")
                : "", fontSize10));
        usllistCell.setColspan(2);
        usllistCell.setBorder(Rectangle.NO_BORDER);
        table.addCell(usllistCell);

        //РАЗДЕЛИТЕЛЬНАЯ ЛИНИЯ ИЗ ТОЧЕК
        String dottedLine = ".".repeat(200); // 150 — примерное количество символов по ширине страницы
        PdfPCell dottedLineCell = new PdfPCell(new Phrase(dottedLine, fontSize10));
        dottedLineCell.setColspan(2);
        dottedLineCell.setBorder(Rectangle.NO_BORDER);
        dottedLineCell.setHorizontalAlignment(Element.ALIGN_CENTER);

        // ----------- Текст согласия на ПД -------------
        String soglasiePhrase = "Даю свое согласие ГБУ РД «РДЦ» по адресу: РД, г. Махачкала, ул. Магомедтагирова 172 б, на обработку своих персональных данных и персональных данных лица, которого я представляю на условиях, соответствующих ФЗ от 27.07.2006 №152-ФЗ \"О персональных данных\" ______________";
        PdfPCell soglasienaPDLineCell = new PdfPCell(new Phrase(soglasiePhrase, fontSize8));
        soglasienaPDLineCell.setColspan(2);
        soglasienaPDLineCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        soglasienaPDLineCell.setBorder(Rectangle.TOP);
        soglasienaPDLineCell.setBorderWidthTop(1f);
        table.addCell(soglasienaPDLineCell);

        document.add(table);

        //Пустая строка с отступом сверху 5
        emptyLine.setSpacingBefore(5f); // отступ сверху
        document.add(emptyLine);

        // Таблица из 2 колонок, ширина в процентах
        PdfPTable tablesoglasieMed = new PdfPTable(2);
        tablesoglasieMed.setWidthPercentage(100);
        tablesoglasieMed.setWidths(new float[]{70, 30}); // Соотношение ширины колонок

        // ----------- Заголовок согласие на МЕД. вмешательство -------------
        PdfPCell soglasieMedCell = new PdfPCell(new Phrase("Информированное добровольное согласие на медицинское вмешательство", fontSize8));
        soglasieMedCell.setColspan(2);
        soglasieMedCell.setBorder(Rectangle.TOP);
        soglasieMedCell.setBorderWidthTop(1f);
        soglasieMedCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        tablesoglasieMed.addCell(soglasieMedCell);

        // ----------- Текст МЕД. вмешательство -------------
        String strMed = "Я, [INFFIOONE] [INFBDONE] г. рождения, зарегистрированный по адресу: [INFADDRONE] проживающий по адресу: [INFADDRTWO], даю информированное добровольное согласие на виды медицинских вмешательств, включенные  в  Перечень  определенных  видов  медицинских  вмешательств, на которые  граждане  дают  информированное  добровольное  согласие при выборе врача  и  медицинской организации для получения первичной медико-санитарной помощи,  утвержденный  приказом  Министерства здравоохранения и социального развития Российской Федерации от 23 апреля 2012 г. N 390н (далее - виды медицинских  вмешательств,  включенных в Перечень), для получения первичной медико-санитарной  помощи/получения  первичной   медико-санитарной   помощи в ГБУ РД «РДЦ» медицинским работником [INFDOCTORONE] в доступной для меня форме мне разъяснены цели, методы оказания медицинской помощи, связанный с ними риск, возможные варианты медицинских вмешательств, их последствия,  в  том  числе  вероятность  развития  осложнений, а также предполагаемые  результаты оказания медицинской помощи. Мне разъяснено, что я  имею  право  отказаться  от  одного  или  нескольких  видов  медицинских вмешательств,  включенных в Перечень, или потребовать его (их) прекращения, за  исключением  случаев,  предусмотренных  частью 9 статьи 20 Федерального закона  от 21 ноября 2011 г. N 323-ФЗ \"Об основах охраны здоровья граждан в Российской Федерации\".\n" +
                "Сведения о выбранном (выбранных) мною лице (лицах), которому (которым) в соответствии с пунктом 5 части 5 статьи 19 Федерального закона от 21 ноября 2011 г.  N 323-ФЗ \"Об основах охраны здоровья граждан в Российской Федерации\" может быть передана информация о состоянии моего здоровья или состоянии лица, законным представителем которого я являюсь (ненужное зачеркнуть), в том числе после смерти:";

        // Список всех ключей, которые нужно заменить
        String[] keys = {"INFFIOONE", "INFBDONE", "INFADDRONE", "INFADDRTWO", "INFDOCTORONE"};

        // Применяем замену для каждого ключа
        for (String key : keys) {
            // Заменяем в строке `strMed` подстроку вида "[КЛЮЧ]" на значение из `data`
            strMed = strMed.replace(
                    // Шаблон для замены — строка в квадратных скобках, например "[INFFIOONE]"
                    "[" + key + "]",
                    // Условие:
                    // 1. Если `data` содержит ключ `key` И значение не `null` → подставляем его строковое представление
                    // 2. Иначе → подставляем пустую строку (чтобы не оставалось "[КЛЮЧ]")
                    data.containsKey(key) && data.get(key) != null ? data.get(key).toString() : ""
            );
        }

        PdfPCell textMedCell = new PdfPCell(new Phrase(strMed, fontSize8));
        textMedCell.setColspan(2);
        textMedCell.setBorder(Rectangle.NO_BORDER);
        textMedCell.setHorizontalAlignment(Element.ALIGN_JUSTIFIED);
        tablesoglasieMed.addCell(textMedCell);

        // ----------- Линия с ФИО пациента -------------
        PdfPCell fiopatientlineMedCell = new PdfPCell(new Phrase("_______________________________________________________________________________________", fontSize8));
        fiopatientlineMedCell.setColspan(2);
        fiopatientlineMedCell.setBorder(Rectangle.NO_BORDER);
        fiopatientlineMedCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        tablesoglasieMed.addCell(fiopatientlineMedCell);

        // ----------- Линия с ФИО описанием что нужно написать для ФИО пациента -------------
        PdfPCell enterfiopatientlineMedCell = new PdfPCell(new Phrase("(фамилия, имя, отчество (при наличии) гражданина, контактный телефон)", fontSize7));
        enterfiopatientlineMedCell.setColspan(2);
        enterfiopatientlineMedCell.setBorder(Rectangle.NO_BORDER);
        enterfiopatientlineMedCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        tablesoglasieMed.addCell(enterfiopatientlineMedCell);

        document.add(tablesoglasieMed);

        // Создаем таблицу из 2 колонок. Таблица для вывода двух линий с информацией о пациенте и враче в согласии на вмешательство
        PdfPTable tablePeopleInfo = new PdfPTable(3);
        tablePeopleInfo.setWidthPercentage(100);
        tablePeopleInfo.setWidths(new float[]{25, 5, 70});

        /* ********* ПЕРВАЯ СТРОКА ********* */
        // Левая колонка, верхняя ячейка — линия из подчеркиваний
        Chunk lineChunk = new Chunk("", fontSize8);
        Phrase linePhrase = new Phrase(lineChunk);
        PdfPCell tablePeopleInfoLine1LeftCell = new PdfPCell(linePhrase);
        tablePeopleInfoLine1LeftCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        tablePeopleInfoLine1LeftCell.setBorder(Rectangle.NO_BORDER);
        // Убираем все границы, кроме нижней
        tablePeopleInfoLine1LeftCell.setBorderWidthBottom(1f);
        tablePeopleInfoLine1LeftCell.setBorder(Rectangle.BOTTOM);
        tablePeopleInfo.addCell(tablePeopleInfoLine1LeftCell);

        // Средняя колонка, верхняя ячейка — линия из подчеркиваний
        PdfPCell tablePeopleInfoLine1MiddleCell = new PdfPCell(new Phrase(new Chunk("", fontSize8)));
        tablePeopleInfoLine1MiddleCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        tablePeopleInfoLine1MiddleCell.setBorder(Rectangle.NO_BORDER);
        tablePeopleInfo.addCell(tablePeopleInfoLine1MiddleCell);

        // --------------- Добавляем строку для вывода ФИО пациента ---------------
        // Правая колонка, верхняя ячейка — подчёркнутый текст [INFFIOTWO]
        PdfPCell tablePeopleInfoLine1RightCell = new PdfPCell(new Phrase((data.get("INFFIOTWO") != null && !((String) data.get("INFFIOTWO")).trim().isEmpty())
                ? (String) data.get("INFFIOTWO")
                : "", fontSize8));
        tablePeopleInfoLine1RightCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        tablePeopleInfoLine1RightCell.setBorder(Rectangle.NO_BORDER);
        // Убираем все границы, кроме нижней
        tablePeopleInfoLine1RightCell.setBorderWidthBottom(1f);
        tablePeopleInfoLine1RightCell.setBorder(Rectangle.BOTTOM);
        tablePeopleInfo.addCell(tablePeopleInfoLine1RightCell);

        /* ********* ВТОРАЯ СТРОКА ********* */
        // Левая колонка, нижняя ячейка — подпись под линией
        PdfPCell tablePeopleInfoLine2LeftCell = new PdfPCell(new Phrase("(подпись)", fontSize7));
        tablePeopleInfoLine2LeftCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        tablePeopleInfoLine2LeftCell.setBorder(Rectangle.NO_BORDER);
        tablePeopleInfo.addCell(tablePeopleInfoLine2LeftCell);

        // Средняя колонка, верхняя ячейка — линия из подчеркиваний
        PdfPCell tablePeopleInfoLine2MiddleCell = new PdfPCell(new Phrase(new Chunk("", fontSize8)));
        tablePeopleInfoLine2MiddleCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        tablePeopleInfoLine2MiddleCell.setBorder(Rectangle.NO_BORDER);
        tablePeopleInfo.addCell(tablePeopleInfoLine2MiddleCell);

        // Правая колонка, нижняя ячейка — описание
        PdfPCell tablePeopleInfoLine2RightCell = new PdfPCell(new Phrase("(фамилия, имя, отчество (при наличии) гражданина, телефон)", fontSize7));
        tablePeopleInfoLine2RightCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        tablePeopleInfoLine2RightCell.setBorder(Rectangle.NO_BORDER);
        tablePeopleInfo.addCell(tablePeopleInfoLine2RightCell);

        /* ********* ТРЕТЬЯ СТРОКА ********* */
        // Левая колонка, верхняя ячейка — линия из подчеркиваний
        PdfPCell tablePeopleInfoLine3LeftCell = new PdfPCell(new Phrase(new Chunk("", fontSize8)));
        tablePeopleInfoLine3LeftCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        tablePeopleInfoLine3LeftCell.setBorder(Rectangle.NO_BORDER);
        // Убираем все границы, кроме нижней
        tablePeopleInfoLine3LeftCell.setBorderWidthBottom(1f);
        tablePeopleInfoLine3LeftCell.setBorder(Rectangle.BOTTOM);
        tablePeopleInfo.addCell(tablePeopleInfoLine3LeftCell);

        // Средняя колонка, верхняя ячейка — линия из подчеркиваний
        PdfPCell tablePeopleInfoLine3MiddleCell = new PdfPCell(new Phrase(new Chunk("", fontSize8)));
        tablePeopleInfoLine3MiddleCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        tablePeopleInfoLine3MiddleCell.setBorder(Rectangle.NO_BORDER);
        tablePeopleInfo.addCell(tablePeopleInfoLine3MiddleCell);

        // --------------- Добавляем строку для вывода ФИО врача ---------------
        // Правая колонка, верхняя ячейка — подчёркнутый текст [INFDOCTORTWO]
        PdfPCell tablePeopleInfoLine3RightCell = new PdfPCell(new Phrase((data.get("INFDOCTORTWO") != null && !((String) data.get("INFDOCTORTWO")).trim().isEmpty())
                ? (String) data.get("INFDOCTORTWO")
                : "", fontSize8));
        tablePeopleInfoLine3RightCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        tablePeopleInfoLine3RightCell.setBorder(Rectangle.NO_BORDER);
        // Убираем все границы, кроме нижней
        tablePeopleInfoLine3RightCell.setBorderWidthBottom(1f);
        tablePeopleInfoLine3RightCell.setBorder(Rectangle.BOTTOM);
        tablePeopleInfo.addCell(tablePeopleInfoLine3RightCell);

        /* ********* ЧЕТВЕРТАЯ СТРОКА ********* */
        // Левая колонка, нижняя ячейка — подпись под линией
        PdfPCell tablePeopleInfoLine4LeftCell = new PdfPCell(new Phrase("(подпись)", fontSize7));
        tablePeopleInfoLine4LeftCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        tablePeopleInfoLine4LeftCell.setBorder(Rectangle.NO_BORDER);
        tablePeopleInfo.addCell(tablePeopleInfoLine4LeftCell);

        // Средняя колонка, верхняя ячейка — линия из подчеркиваний
        PdfPCell tablePeopleInfoLine4MiddleCell = new PdfPCell(new Phrase(new Chunk("", fontSize8)));
        tablePeopleInfoLine4MiddleCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        tablePeopleInfoLine4MiddleCell.setBorder(Rectangle.NO_BORDER);
        tablePeopleInfo.addCell(tablePeopleInfoLine4MiddleCell);

        // Правая колонка, нижняя ячейка — описание
        PdfPCell tablePeopleInfoLine4RightCell = new PdfPCell(new Phrase("(фамилия, имя, отчество (при наличии) медицинского работника)", fontSize7));
        tablePeopleInfoLine4RightCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        tablePeopleInfoLine4RightCell.setBorder(Rectangle.NO_BORDER);
        tablePeopleInfo.addCell(tablePeopleInfoLine4RightCell);

        document.add(tablePeopleInfo);

        // Создаем таблицу из 2 колонок. Таблица для вывода двух линий с информацией о пациенте и враче в согласии на вмешательство
        PdfPTable tablePeopleInfoDateLine = new PdfPTable(2);
        tablePeopleInfoDateLine.setWidthPercentage(100);
        tablePeopleInfoDateLine.setWidths(new float[]{60, 40});

        /* ********* ПЕРВАЯ СТРОКА ********* */
        PdfPCell tablePeopleInfoDateLineLine1LeftCell = new PdfPCell(new Phrase(new Chunk("", fontSize8)));
        tablePeopleInfoDateLineLine1LeftCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        tablePeopleInfoDateLineLine1LeftCell.setBorder(Rectangle.NO_BORDER);
        tablePeopleInfoDateLine.addCell(tablePeopleInfoDateLineLine1LeftCell);

        // ----------- Линия с датой -------------
        PdfPCell tablePeopleInfoDateLineLine1RightCell = new PdfPCell(new Phrase("\"_____\" __________________ ______г.", fontSize8));
        tablePeopleInfoDateLineLine1RightCell.setBorder(Rectangle.NO_BORDER);
        tablePeopleInfoDateLineLine1RightCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        tablePeopleInfoDateLine.addCell(tablePeopleInfoDateLineLine1RightCell);

        /* ********* ВТОРАЯ СТРОКА ********* */
        PdfPCell tablePeopleInfoDateLineLine2LeftCell = new PdfPCell(new Phrase(new Chunk("", fontSize8)));
        tablePeopleInfoDateLineLine2LeftCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        tablePeopleInfoDateLineLine2LeftCell.setBorder(Rectangle.NO_BORDER);
        tablePeopleInfoDateLine.addCell(tablePeopleInfoDateLineLine2LeftCell);

        PdfPCell tablePeopleInfoDateLineLine2RightCell = new PdfPCell(new Phrase("(дата оформления)", fontSize7));
        tablePeopleInfoDateLineLine2RightCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        tablePeopleInfoDateLineLine2RightCell.setBorder(Rectangle.NO_BORDER);
        tablePeopleInfoDateLine.addCell(tablePeopleInfoDateLineLine2RightCell);

        document.add(tablePeopleInfoDateLine);

        //Пустая строка с отступом сверху 5
        emptyLine.setSpacingBefore(5f); // отступ сверху
        document.add(emptyLine);

        // Создаем таблицу для отрывного талона
        PdfPTable tableotryvTalon = new PdfPTable(2);
        tableotryvTalon.setWidthPercentage(100);
        tableotryvTalon.setWidths(new float[]{75, 25});

        // Загрузка картинки
        Image qrImage = Image.getInstance(Objects.requireNonNull(getClass().getResource("/static/img/qrcode.png")));
        qrImage.scaleToFit(60, 60); // Подогнать размер картинки под ячейку

        // 1-я строка: первая колонка + вторая колонка с картинкой (rowspan=7)
        PdfPCell tableotryvTalonLine1Cell1 = new PdfPCell(new Phrase((data.get("PNUMONE") != null && !((String) data.get("PNUMONE")).trim().isEmpty())
                ? (String) data.get("PNUMONE")
                : "", fontSize10));
        tableotryvTalonLine1Cell1.setBorderWidthTop(1f);
        tableotryvTalonLine1Cell1.setBorder(Rectangle.TOP);
        tableotryvTalonLine1Cell1.setHorizontalAlignment(Element.ALIGN_RIGHT);
        tableotryvTalon.addCell(tableotryvTalonLine1Cell1);

        PdfPCell tableotryvTalonLine1Cell2 = new PdfPCell(qrImage, false);
        tableotryvTalonLine1Cell2.setRowspan(7);
        tableotryvTalonLine1Cell2.setBorderWidthTop(1f);
        tableotryvTalonLine1Cell2.setBorder(Rectangle.TOP);
        tableotryvTalonLine1Cell2.setHorizontalAlignment(Element.ALIGN_RIGHT);
        tableotryvTalonLine1Cell2.setVerticalAlignment(Element.ALIGN_MIDDLE);
        tableotryvTalon.addCell(tableotryvTalonLine1Cell2);

        // 2-я FIOTWO
        PdfPCell tableotryvTalonLine2Cell1 = new PdfPCell(new Phrase((data.get("ROOMTWO") != null && !((String) data.get("FIOTWO")).trim().isEmpty())
                ? (String) data.get("FIOTWO")
                : "", timesNewRomanBold10));
        tableotryvTalonLine2Cell1.setBorder(Rectangle.NO_BORDER);
        tableotryvTalon.addCell(tableotryvTalonLine2Cell1);

        // 3-я строка
        String dateObs = data.get("DATEOBSLTWO") != null ? (String) data.get("DATEOBSLTWO") : "";
        String medNum = data.get("MEDNUMTWO") != null ? (String) data.get("MEDNUMTWO") : "";
        PdfPCell tableotryvTalonLine3Cell1 = new PdfPCell(new Phrase(dateObs + " " + medNum, timesNewRomanBold10));
        tableotryvTalonLine3Cell1.setBorder(Rectangle.NO_BORDER);
        tableotryvTalon.addCell(tableotryvTalonLine3Cell1);

        // 4-я строка
        PdfPCell tableotryvTalonLine4Cell1 = new PdfPCell(new Phrase((data.get("ROOMTWO") != null && !((String) data.get("ROOMTWO")).trim().isEmpty())
                ? (String) data.get("ROOMTWO")
                : "", timesNewRomanBold10));
        tableotryvTalonLine4Cell1.setBorder(Rectangle.NO_BORDER);
        tableotryvTalon.addCell(tableotryvTalonLine4Cell1);

        // 5-я строка
        String printDate = data.get("PRINTDATTWO") != null ? (String) data.get("PRINTDATTWO") : "";
        String regCode = data.get("REGCODE") != null ? (String) data.get("REGCODE") : "";
        PdfPCell tableotryvTalonLine5Cell1 = new PdfPCell(new Phrase(printDate + " " + regCode, fontSize10));
        tableotryvTalonLine5Cell1.setBorder(Rectangle.NO_BORDER);
        tableotryvTalon.addCell(tableotryvTalonLine5Cell1);

        // 6-я строка
        PdfPCell tableotryvTalonLine6Cell1 = new PdfPCell(new Phrase((data.get("USLTITLE") != null && !((String) data.get("USLTITLE")).trim().isEmpty())
                ? (String) data.get("USLTITLE")
                : "", fontSize10));
        tableotryvTalonLine6Cell1.setBorder(Rectangle.NO_BORDER);
        tableotryvTalon.addCell(tableotryvTalonLine6Cell1);

        // 7-я строка
        PdfPCell tableotryvTalonLine7Cell1 = new PdfPCell(new Phrase((data.get("USLLISTTWO") != null && !((String) data.get("USLLISTTWO")).trim().isEmpty())
                ? (String) data.get("USLLISTTWO")
                : "", fontSize10));
        tableotryvTalonLine7Cell1.setBorder(Rectangle.NO_BORDER);
        tableotryvTalon.addCell(tableotryvTalonLine7Cell1);

        //8-я строка
        PdfPCell tableotryvTalonLine8Cell1 = new PdfPCell(new Phrase((data.get("OTRTALONINFO") != null && !((String) data.get("OTRTALONINFO")).trim().isEmpty())
                ? (String) data.get("OTRTALONINFO")
                : "", fontSize10));
        tableotryvTalonLine8Cell1.setBorder(Rectangle.NO_BORDER);
        tableotryvTalonLine8Cell1.setHorizontalAlignment(Element.ALIGN_CENTER);
        tableotryvTalon.addCell(tableotryvTalonLine8Cell1);

        document.add(tableotryvTalon);

        //Пустая строка с отступом сверху 5
        emptyLine.setSpacingBefore(5f); // отступ сверху
        document.add(emptyLine);

        // Создаем таблицу для вывода информации о телеграм боте
        PdfPTable telegramTalon = new PdfPTable(1);
        telegramTalon.setWidthPercentage(100);
        telegramTalon.setWidths(new float[]{100});

        // Получаем значения
        String login = data.get("LOGIN") != null ? (String) data.get("LOGIN") : "";
        String password = data.get("PASSWORD") != null ? (String) data.get("PASSWORD") : "";

        // Создаем фразу с миксом обычного и жирного текста
        Phrase phrase = new Phrase();
        phrase.add(new Chunk("Посмотреть историю лечения или результаты анализов в личном кабинете пациента \nпо ссылке https://lk.dagdiag.ru или в telegram боте rdc05bot отсканировав QR код:\nЛогин: ", fontSize8));
        phrase.add(new Chunk(login, timesNewRomanBold8));  // жирный логин
        phrase.add(new Chunk(" и пароль: ", fontSize8));
        phrase.add(new Chunk(password, timesNewRomanBold8));  // жирный пароль
        phrase.add(new Chunk(" для входа в личный кабинет", fontSize8));

        // Создаем ячейку с этим Phrase
        PdfPCell telegramInfoCell1 = new PdfPCell(phrase);
        telegramInfoCell1.setBorder(Rectangle.NO_BORDER);
        telegramInfoCell1.setBackgroundColor(new BaseColor(255, 233, 148));
        telegramInfoCell1.setHorizontalAlignment(Element.ALIGN_CENTER);

        // Пунктирная граница — добавляем обработчик события ячейки
        telegramInfoCell1.setCellEvent(new PdfPCellEvent() {
            @Override
            public void cellLayout(PdfPCell cell, Rectangle rect, PdfContentByte[] canvases) {
                PdfContentByte cb = canvases[PdfPTable.LINECANVAS];
                cb.saveState();
                cb.setLineDash(3f, 3f); // 3 пункта линия, 3 пункта пробел
                cb.setColorStroke(BaseColor.BLACK);
                cb.setLineWidth(1f);
                cb.rectangle(rect.getLeft(), rect.getBottom(), rect.getWidth(), rect.getHeight());
                cb.stroke();
                cb.restoreState();
            }
        });

        telegramTalon.addCell(telegramInfoCell1);

        document.add(telegramTalon);

        document.close();

        response.setContentType("application/pdf");
        response.setHeader("Content-Disposition", "inline; filename=talon.pdf");
        OutputStream os = response.getOutputStream();
        os.write(byteStream.toByteArray());
        os.flush();
        os.close();
    }

    @GetMapping("/print/pdf/viewer/{id}")
    public String pdfViewer(@PathVariable Long id,
                            @RequestParam(required = false, defaultValue = "false") boolean batch,
                            Model model) {
        String pdfUrl = "/print/pdf/" + id + (batch ? "?batch=true" : "");
        model.addAttribute("pdfUrl", pdfUrl);
        return "pdf-viewer";
    }

}
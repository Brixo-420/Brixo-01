package com.BRIXO.controller;

import com.BRIXO.model.EstadoServicio;
import com.BRIXO.model.Servicio;
import com.BRIXO.model.TipoServicio;
import com.BRIXO.service.CotizacionService;
import com.BRIXO.service.ServicioService;
import jakarta.validation.Valid;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.util.IOUtils;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFClientAnchor;
import org.apache.poi.xssf.usermodel.XSSFDrawing;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Arrays;

@Controller
@RequestMapping("/servicios")
public class ServicioController {

    private final ServicioService servicioService;
    private final CotizacionService cotizacionService;

    public ServicioController(ServicioService servicioService, CotizacionService cotizacionService) {
        this.servicioService = servicioService;
        this.cotizacionService = cotizacionService;
    }

    @GetMapping
    public String listar(
            @RequestParam(required = false) String titulo,
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) String emailCliente,
            Authentication authentication,
            Model model
    ) {
        boolean esAdmin = tieneRol(authentication, "ROLE_ADMIN");
        boolean esCliente = tieneRol(authentication, "ROLE_CLIENTE");
        boolean esContratista = tieneRol(authentication, "ROLE_CONTRATISTA");

        model.addAttribute("servicios", servicioService.listarFiltrados(titulo, estado, emailCliente, authentication.getName(), esAdmin, esCliente, esContratista));
        model.addAttribute("estados", EstadoServicio.values());
        model.addAttribute("titulo", titulo);
        model.addAttribute("estado", estado);
        model.addAttribute("emailCliente", emailCliente);
        model.addAttribute("esAdmin", esAdmin);
        model.addAttribute("esCliente", esCliente);
        model.addAttribute("esContratista", esContratista);
        return "servicios/lista";
    }

    @GetMapping("/nuevo")
    public String nuevo(
            @RequestParam(required = false) String tipo,
            Authentication authentication, 
            Model model, 
            RedirectAttributes redirectAttributes
    ) {
        if (!puedeEditar(authentication)) {
            redirectAttributes.addFlashAttribute("error", "Solo CLIENTE puede crear servicios");
            return "redirect:/servicios";
        }

        Servicio servicio = new Servicio();
        if (tipo != null && !tipo.isBlank()) {
            try {
                servicio.setTipo(TipoServicio.valueOf(tipo.toUpperCase()));
            } catch (IllegalArgumentException e) {
                // Type not valid, skip setting
            }
        }
        
        model.addAttribute("servicio", servicio);
        model.addAttribute("estados", EstadoServicio.values());
        return "servicios/form";
    }

    @PostMapping
    public String crear(
            @Valid @ModelAttribute("servicio") Servicio servicio,
            BindingResult bindingResult,
            Authentication authentication,
            RedirectAttributes redirectAttributes,
            Model model
    ) {
        if (!puedeEditar(authentication)) {
            redirectAttributes.addFlashAttribute("error", "No tienes permisos para crear servicios");
            return "redirect:/servicios";
        }

        if (bindingResult.hasErrors()) {
            model.addAttribute("estados", EstadoServicio.values());
            return "servicios/form";
        }

        try {
            servicioService.crear(servicio, authentication.getName());
            redirectAttributes.addFlashAttribute("ok", "Servicio creado correctamente");
            return "redirect:/servicios";
        } catch (IllegalArgumentException ex) {
            model.addAttribute("error", ex.getMessage());
            model.addAttribute("estados", EstadoServicio.values());
            return "servicios/form";
        }
    }

    @GetMapping("/reporte.xlsx")
    @ResponseBody
    public ResponseEntity<byte[]> exportarExcel(
            @RequestParam(required = false) String titulo,
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) String emailCliente,
            Authentication authentication
    ) throws IOException {
        boolean esAdmin = tieneRol(authentication, "ROLE_ADMIN");
        boolean esCliente = tieneRol(authentication, "ROLE_CLIENTE");
        boolean esContratista = tieneRol(authentication, "ROLE_CONTRATISTA");

        List<Servicio> servicios = servicioService.listarFiltrados(
                titulo, estado, emailCliente, authentication.getName(), esAdmin, esCliente, esContratista);

        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Servicios");
            sheet.setDisplayGridlines(false);
            sheet.setPrintGridlines(false);

            // ── Colores corporativos ──
            byte[] cPrimario   = {(byte)15,(byte)23,(byte)42};       // #0f172a
            byte[] cAccento    = {(byte)14,(byte)165,(byte)233};      // #0ea5e9
            byte[] cAccentoBg  = {(byte)224,(byte)242,(byte)254};     // #e0f2fe (azul claro)
            byte[] cGrisClaro  = {(byte)248,(byte)250,(byte)252};     // #f8fafc
            byte[] cBorde      = {(byte)203,(byte)213,(byte)225};     // #cbd5e1
            byte[] cBordeLight = {(byte)226,(byte)232,(byte)240};     // #e2e8f0
            byte[] cBlanco     = {(byte)255,(byte)255,(byte)255};
            byte[] cSuccess    = {(byte)16,(byte)185,(byte)129};      // #10b981
            byte[] cSuccessBg  = {(byte)209,(byte)250,(byte)229};     // #d1fae5
            byte[] cWarning    = {(byte)245,(byte)158,(byte)11};      // #f59e0b
            byte[] cWarningBg  = {(byte)254,(byte)243,(byte)199};     // #fef3c7
            byte[] cDanger     = {(byte)239,(byte)68,(byte)68};       // #ef4444
            byte[] cSecondary  = {(byte)99,(byte)102,(byte)241};      // #6366f1
            byte[] cSecBg      = {(byte)224,(byte)231,(byte)255};     // #e0e7ff
            byte[] cTotalBg    = {(byte)30,(byte)41,(byte)59};        // #1e293b

            DataFormat fmt = workbook.createDataFormat();
            int numCols = 9;

            // ══════════ BANNER SUPERIOR CON COLOR ══════════
            XSSFCellStyle bannerStyle = workbook.createCellStyle();
            bannerStyle.setFillForegroundColor(new XSSFColor(cPrimario, null));
            bannerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            // Fila 0: barra de color superior (delgada)
            Row bannerRow = sheet.createRow(0);
            bannerRow.setHeightInPoints(6);
            for (int i = 0; i < numCols; i++) {
                Cell c = bannerRow.createCell(i);
                c.setCellStyle(bannerStyle);
            }

            // Filas 1-4: espacio para el logo
            for (int i = 1; i <= 4; i++) {
                Row r = sheet.createRow(i);
                r.setHeightInPoints(i == 1 ? 8 : 22);
            }

            // ══════════ TÍTULO ══════════
            Row titleRow = sheet.createRow(5);
            titleRow.setHeightInPoints(36);
            XSSFCellStyle titleStyle = workbook.createCellStyle();
            Font titleFont = workbook.createFont();
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 20);
            titleFont.setFontName("Calibri");
            titleFont.setColor(new XSSFColor(cPrimario, null).getIndex());
            titleStyle.setFont(titleFont);
            titleStyle.setVerticalAlignment(VerticalAlignment.BOTTOM);
            Cell titleCell = titleRow.createCell(0);
            titleCell.setCellValue("Reporte de Servicios");
            titleCell.setCellStyle(titleStyle);
            sheet.addMergedRegion(new CellRangeAddress(5, 5, 0, numCols - 1));

            // ── Línea acento ──
            Row accentRow = sheet.createRow(6);
            accentRow.setHeightInPoints(4);
            XSSFCellStyle accentStyle = workbook.createCellStyle();
            accentStyle.setFillForegroundColor(new XSSFColor(cAccento, null));
            accentStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            for (int i = 0; i < 4; i++) {
                Cell c = accentRow.createCell(i);
                c.setCellStyle(accentStyle);
            }

            // ══════════ RESUMEN EJECUTIVO ══════════
            long countAbiertos = servicios.stream().filter(s -> s.getEstado() == EstadoServicio.ABIERTO).count();
            long countEnProceso = servicios.stream().filter(s -> s.getEstado() == EstadoServicio.EN_PROCESO).count();
            long countCerrados = servicios.stream().filter(s -> s.getEstado() == EstadoServicio.CERRADO).count();
            long countCotizacion = servicios.stream().filter(s -> s.getEstado() == EstadoServicio.EN_COTIZACION).count();
            double totalPresupuesto = servicios.stream()
                .mapToDouble(s -> s.getPresupuesto() != null ? s.getPresupuesto().doubleValue() : 0).sum();

            // Fila 7: espacio
            Row spaceRow = sheet.createRow(7);
            spaceRow.setHeightInPoints(10);

            // Fila 8: Info y resumen
            Row infoRow = sheet.createRow(8);
            infoRow.setHeightInPoints(22);
            XSSFCellStyle infoStyle = workbook.createCellStyle();
            Font infoFont = workbook.createFont();
            infoFont.setFontHeightInPoints((short) 10);
            infoFont.setFontName("Calibri");
            infoFont.setColor(IndexedColors.GREY_50_PERCENT.getIndex());
            infoStyle.setFont(infoFont);
            infoStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            Cell infoCell = infoRow.createCell(0);
            infoCell.setCellValue("Generado el " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy 'a las' HH:mm")));
            infoCell.setCellStyle(infoStyle);
            sheet.addMergedRegion(new CellRangeAddress(8, 8, 0, 3));

            // Resumen a la derecha
            XSSFCellStyle summaryStyle = workbook.createCellStyle();
            Font summaryFont = workbook.createFont();
            summaryFont.setFontHeightInPoints((short) 9);
            summaryFont.setFontName("Calibri");
            summaryFont.setBold(true);
            summaryStyle.setFont(summaryFont);
            summaryStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            summaryStyle.setAlignment(HorizontalAlignment.CENTER);
            summaryStyle.setFillForegroundColor(new XSSFColor(cAccentoBg, null));
            summaryStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            summaryStyle.setBorderBottom(BorderStyle.THIN);
            summaryStyle.setBorderTop(BorderStyle.THIN);
            summaryStyle.setBorderLeft(BorderStyle.THIN);
            summaryStyle.setBorderRight(BorderStyle.THIN);
            summaryStyle.setBottomBorderColor(new XSSFColor(cBordeLight, null));
            summaryStyle.setTopBorderColor(new XSSFColor(cBordeLight, null));
            summaryStyle.setLeftBorderColor(new XSSFColor(cBordeLight, null));
            summaryStyle.setRightBorderColor(new XSSFColor(cBordeLight, null));

            Cell s5 = infoRow.createCell(4); s5.setCellValue("Total: " + servicios.size()); s5.setCellStyle(summaryStyle);
            Cell s6 = infoRow.createCell(5); s6.setCellValue("Abiertos: " + countAbiertos); s6.setCellStyle(summaryStyle);
            Cell s7 = infoRow.createCell(6); s7.setCellValue("Proceso: " + countEnProceso); s7.setCellStyle(summaryStyle);
            Cell s8 = infoRow.createCell(7); s8.setCellValue("Cotización: " + countCotizacion); s8.setCellStyle(summaryStyle);
            Cell s9 = infoRow.createCell(8); s9.setCellValue("Cerrados: " + countCerrados); s9.setCellStyle(summaryStyle);

            // Fila 9: espacio antes de la tabla
            Row space2 = sheet.createRow(9);
            space2.setHeightInPoints(8);

            // ══════════ CABECERA DE TABLA ══════════
            XSSFCellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setFontHeightInPoints((short) 10);
            headerFont.setFontName("Calibri");
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(new XSSFColor(cPrimario, null));
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            headerStyle.setBorderBottom(BorderStyle.THIN);
            headerStyle.setBorderTop(BorderStyle.THIN);
            headerStyle.setBorderLeft(BorderStyle.THIN);
            headerStyle.setBorderRight(BorderStyle.THIN);
            headerStyle.setBottomBorderColor(new XSSFColor(cPrimario, null));
            headerStyle.setTopBorderColor(new XSSFColor(cPrimario, null));
            headerStyle.setLeftBorderColor(new XSSFColor(cPrimario, null));
            headerStyle.setRightBorderColor(new XSSFColor(cPrimario, null));

            String[] columnas = {"#", "Título", "Tipo", "Ubicación", "Presupuesto", "Estado", "Cliente", "Contratista", "Fecha"};
            Row headerRow = sheet.createRow(10);
            headerRow.setHeightInPoints(30);
            for (int i = 0; i < columnas.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(columnas[i]);
                cell.setCellStyle(headerStyle);
            }

            // ══════════ ESTILOS DE DATOS ══════════
            // Helper: crear estilo base de celda
            XSSFCellStyle[] makeDataPair = new XSSFCellStyle[2]; // [normal, alt]
            for (int p = 0; p < 2; p++) {
                XSSFCellStyle ds = workbook.createCellStyle();
                Font df = workbook.createFont();
                df.setFontHeightInPoints((short) 10);
                df.setFontName("Calibri");
                ds.setFont(df);
                ds.setBorderBottom(BorderStyle.THIN);
                ds.setBorderTop(BorderStyle.THIN);
                ds.setBorderLeft(BorderStyle.THIN);
                ds.setBorderRight(BorderStyle.THIN);
                ds.setBottomBorderColor(new XSSFColor(cBordeLight, null));
                ds.setTopBorderColor(new XSSFColor(cBordeLight, null));
                ds.setLeftBorderColor(new XSSFColor(cBordeLight, null));
                ds.setRightBorderColor(new XSSFColor(cBordeLight, null));
                ds.setVerticalAlignment(VerticalAlignment.CENTER);
                if (p == 1) {
                    ds.setFillForegroundColor(new XSSFColor(cGrisClaro, null));
                    ds.setFillPattern(FillPatternType.SOLID_FOREGROUND);
                }
                makeDataPair[p] = ds;
            }

            // Estilo moneda
            XSSFCellStyle[] moneyPair = new XSSFCellStyle[2];
            for (int p = 0; p < 2; p++) {
                XSSFCellStyle ms = workbook.createCellStyle();
                ms.cloneStyleFrom(makeDataPair[p]);
                ms.setDataFormat(fmt.getFormat("$#,##0.00"));
                ms.setAlignment(HorizontalAlignment.RIGHT);
                Font mf = workbook.createFont();
                mf.setFontHeightInPoints((short) 10);
                mf.setFontName("Calibri");
                mf.setBold(true);
                ms.setFont(mf);
                moneyPair[p] = ms;
            }

            // Estilo ID centrado
            XSSFCellStyle[] idPair = new XSSFCellStyle[2];
            for (int p = 0; p < 2; p++) {
                XSSFCellStyle is = workbook.createCellStyle();
                is.cloneStyleFrom(makeDataPair[p]);
                is.setAlignment(HorizontalAlignment.CENTER);
                Font idf = workbook.createFont();
                idf.setFontHeightInPoints((short) 10);
                idf.setFontName("Calibri");
                idf.setColor(IndexedColors.GREY_50_PERCENT.getIndex());
                is.setFont(idf);
                idPair[p] = is;
            }

            // Estilo fecha centrado
            XSSFCellStyle[] datePair = new XSSFCellStyle[2];
            for (int p = 0; p < 2; p++) {
                XSSFCellStyle ds = workbook.createCellStyle();
                ds.cloneStyleFrom(makeDataPair[p]);
                ds.setAlignment(HorizontalAlignment.CENTER);
                datePair[p] = ds;
            }

            // Estilos para estados con colores
            java.util.Map<String, XSSFCellStyle[]> estadoStyles = new java.util.HashMap<>();
            Object[][] estadoColores = {
                {"ABIERTO", cWarningBg, cWarning},
                {"EN_COTIZACION", cSecBg, cSecondary},
                {"EN_PROCESO", cAccentoBg, cAccento},
                {"CERRADO", cSuccessBg, cSuccess}
            };
            for (Object[] ec : estadoColores) {
                XSSFCellStyle[] pair = new XSSFCellStyle[2];
                for (int p = 0; p < 2; p++) {
                    XSSFCellStyle es = workbook.createCellStyle();
                    es.cloneStyleFrom(makeDataPair[p]);
                    es.setFillForegroundColor(new XSSFColor((byte[]) ec[1], null));
                    es.setFillPattern(FillPatternType.SOLID_FOREGROUND);
                    es.setAlignment(HorizontalAlignment.CENTER);
                    Font ef = workbook.createFont();
                    ef.setFontHeightInPoints((short) 9);
                    ef.setFontName("Calibri");
                    ef.setBold(true);
                    ef.setColor(new XSSFColor((byte[]) ec[2], null).getIndex());
                    es.setFont(ef);
                    pair[p] = es;
                }
                estadoStyles.put((String) ec[0], pair);
            }

            // ══════════ DATOS ══════════
            int rowIdx = 11;
            for (int idx = 0; idx < servicios.size(); idx++) {
                Servicio s = servicios.get(idx);
                Row row = sheet.createRow(rowIdx++);
                row.setHeightInPoints(26);
                int p = idx % 2;

                Cell c0 = row.createCell(0); c0.setCellValue(s.getId()); c0.setCellStyle(idPair[p]);
                Cell c1 = row.createCell(1); c1.setCellValue(s.getTitulo()); c1.setCellStyle(makeDataPair[p]);
                Cell c2 = row.createCell(2); c2.setCellValue(s.getTipo() != null ? s.getTipo().getDisplayName() : ""); c2.setCellStyle(makeDataPair[p]);
                Cell c3 = row.createCell(3); c3.setCellValue(s.getUbicacion()); c3.setCellStyle(makeDataPair[p]);

                double presupuesto = s.getPresupuesto() != null ? s.getPresupuesto().doubleValue() : 0;
                Cell c4 = row.createCell(4); c4.setCellValue(presupuesto); c4.setCellStyle(moneyPair[p]);

                String estadoName = s.getEstado() != null ? s.getEstado().name() : "";
                Cell c5 = row.createCell(5);
                c5.setCellValue(estadoName.replace("_", " "));
                XSSFCellStyle[] estPair = estadoStyles.get(estadoName);
                c5.setCellStyle(estPair != null ? estPair[p] : makeDataPair[p]);

                Cell c6 = row.createCell(6); c6.setCellValue(s.getCliente() != null ? s.getCliente().getNombre() : ""); c6.setCellStyle(makeDataPair[p]);
                Cell c7 = row.createCell(7); c7.setCellValue(s.getContratistaAsignado() != null ? s.getContratistaAsignado().getNombre() : "Sin asignar"); c7.setCellStyle(makeDataPair[p]);
                Cell c8 = row.createCell(8); c8.setCellValue(s.getFechaCreacion() != null ? s.getFechaCreacion().format(dtf) : ""); c8.setCellStyle(datePair[p]);
            }

            // ══════════ FILA DE TOTALES ══════════
            Row totalRow = sheet.createRow(rowIdx);
            totalRow.setHeightInPoints(32);

            XSSFCellStyle totalStyle = workbook.createCellStyle();
            Font totalFont = workbook.createFont();
            totalFont.setBold(true);
            totalFont.setFontHeightInPoints((short) 11);
            totalFont.setFontName("Calibri");
            totalFont.setColor(IndexedColors.WHITE.getIndex());
            totalStyle.setFont(totalFont);
            totalStyle.setFillForegroundColor(new XSSFColor(cTotalBg, null));
            totalStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            totalStyle.setBorderBottom(BorderStyle.MEDIUM);
            totalStyle.setBorderTop(BorderStyle.MEDIUM);
            totalStyle.setBorderLeft(BorderStyle.THIN);
            totalStyle.setBorderRight(BorderStyle.THIN);
            totalStyle.setVerticalAlignment(VerticalAlignment.CENTER);

            XSSFCellStyle totalRightStyle = workbook.createCellStyle();
            totalRightStyle.cloneStyleFrom(totalStyle);
            totalRightStyle.setAlignment(HorizontalAlignment.RIGHT);

            XSSFCellStyle totalMoneyStyle = workbook.createCellStyle();
            totalMoneyStyle.cloneStyleFrom(totalStyle);
            totalMoneyStyle.setDataFormat(fmt.getFormat("$#,##0.00"));
            totalMoneyStyle.setAlignment(HorizontalAlignment.RIGHT);

            XSSFCellStyle totalCenterStyle = workbook.createCellStyle();
            totalCenterStyle.cloneStyleFrom(totalStyle);
            totalCenterStyle.setAlignment(HorizontalAlignment.CENTER);

            for (int i = 0; i < numCols; i++) {
                Cell tc = totalRow.createCell(i);
                tc.setCellStyle(totalStyle);
            }
            totalRow.getCell(2).setCellValue(servicios.size() + " servicios");
            totalRow.getCell(2).setCellStyle(totalCenterStyle);
            totalRow.getCell(3).setCellValue("TOTAL");
            totalRow.getCell(3).setCellStyle(totalRightStyle);
            totalRow.getCell(4).setCellValue(totalPresupuesto);
            totalRow.getCell(4).setCellStyle(totalMoneyStyle);

            // ══════════ PIE DE PÁGINA ══════════
            Row footerSpace = sheet.createRow(rowIdx + 1);
            footerSpace.setHeightInPoints(12);

            Row footerRow = sheet.createRow(rowIdx + 2);
            footerRow.setHeightInPoints(20);

            // Línea de acento en footer
            Row footerAccent = sheet.createRow(rowIdx + 3);
            footerAccent.setHeightInPoints(3);
            for (int i = 0; i < 3; i++) {
                Cell c = footerAccent.createCell(i);
                c.setCellStyle(accentStyle);
            }

            XSSFCellStyle footerStyle = workbook.createCellStyle();
            Font footerFont = workbook.createFont();
            footerFont.setItalic(true);
            footerFont.setFontHeightInPoints((short) 9);
            footerFont.setColor(IndexedColors.GREY_50_PERCENT.getIndex());
            footerFont.setFontName("Calibri");
            footerStyle.setFont(footerFont);
            footerStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            Cell footerCell = footerRow.createCell(0);
            footerCell.setCellValue("BRIXO  —  Tu obra, nuestro compromiso  |  www.brixo.com");
            footerCell.setCellStyle(footerStyle);
            sheet.addMergedRegion(new CellRangeAddress(rowIdx + 2, rowIdx + 2, 0, numCols - 1));

            // ══════════ ANCHOS DE COLUMNA ══════════
            sheet.setColumnWidth(0, 2000);   // #
            sheet.setColumnWidth(1, 9000);   // Título
            sheet.setColumnWidth(2, 4500);   // Tipo
            sheet.setColumnWidth(3, 5000);   // Ubicación
            sheet.setColumnWidth(4, 5000);   // Presupuesto
            sheet.setColumnWidth(5, 5000);   // Estado
            sheet.setColumnWidth(6, 5500);   // Cliente
            sheet.setColumnWidth(7, 5500);   // Contratista
            sheet.setColumnWidth(8, 5500);   // Fecha

            // Configurar impresión
            sheet.setFitToPage(true);
            sheet.getPrintSetup().setFitWidth((short) 1);
            sheet.getPrintSetup().setFitHeight((short) 0);
            sheet.getPrintSetup().setLandscape(true);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=brixo-servicios-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + ".xlsx")
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .body(out.toByteArray());
        }
    }

    @GetMapping("/{id}/detalle")
    public String verDetalle(@PathVariable Long id, Authentication authentication, RedirectAttributes redirectAttributes, Model model) {
        try {
            Servicio servicio = servicioService.buscarPorId(id);
            
            // Permitir ver detalles a: cliente (propietario), contratista (asignado o no), admin
            boolean esAdmin = tieneRol(authentication, "ROLE_ADMIN");
            boolean esCliente = tieneRol(authentication, "ROLE_CLIENTE");
            boolean esContratista = tieneRol(authentication, "ROLE_CONTRATISTA");
            
            String emailActual = authentication.getName();
            boolean esClientePropietario = esCliente && servicio.getCliente() != null && servicio.getCliente().getEmail().equals(emailActual);
            boolean esContratistaAsignado = esContratista && servicio.getContratistaAsignado() != null && servicio.getContratistaAsignado().getEmail().equals(emailActual);
            
            if (!esAdmin && !esClientePropietario && !esContratista) {
                redirectAttributes.addFlashAttribute("error", "No tienes permiso para ver este servicio");
                return "redirect:/servicios";
            }
            
            var cotizaciones = cotizacionService.listarPorServicio(id);
            boolean yaCotizo = esContratista && cotizaciones.stream()
                    .anyMatch(c -> c.getContratista().getEmail().equals(emailActual)
                            && c.getEstado() == com.BRIXO.model.EstadoCotizacion.PENDIENTE);

            model.addAttribute("servicio", servicio);
            model.addAttribute("esAdmin", esAdmin);
            model.addAttribute("esCliente", esCliente);
            model.addAttribute("esContratista", esContratista);
            model.addAttribute("esClientePropietario", esClientePropietario);
            model.addAttribute("esContratistaAsignado", esContratistaAsignado);
            model.addAttribute("cotizaciones", cotizaciones);
            model.addAttribute("yaCotizo", yaCotizo);
            return "servicios/detalle";
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
            return "redirect:/servicios";
        }
    }

    @GetMapping("/{id}/editar")
    public String editar(@PathVariable Long id, Authentication authentication, RedirectAttributes redirectAttributes, Model model) {
        if (!puedeEditar(authentication)) {
            redirectAttributes.addFlashAttribute("error", "No tienes permisos para editar servicios");
            return "redirect:/servicios";
        }

        try {
            model.addAttribute("servicio", servicioService.buscarPorId(id));
            model.addAttribute("estados", EstadoServicio.values());
            return "servicios/form";
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
            return "redirect:/servicios";
        }
    }

    @PostMapping("/{id}")
    public String actualizar(
            @PathVariable Long id,
            @Valid @ModelAttribute("servicio") Servicio servicio,
            BindingResult bindingResult,
            Authentication authentication,
            RedirectAttributes redirectAttributes,
            Model model
    ) {
        if (!puedeEditar(authentication)) {
            redirectAttributes.addFlashAttribute("error", "No tienes permisos para actualizar servicios");
            return "redirect:/servicios";
        }

        if (bindingResult.hasErrors()) {
            model.addAttribute("estados", EstadoServicio.values());
            return "servicios/form";
        }

        try {
            boolean esAdmin = tieneRol(authentication, "ROLE_ADMIN");
            servicioService.actualizar(id, servicio, authentication.getName(), esAdmin);
            redirectAttributes.addFlashAttribute("ok", "Servicio actualizado correctamente");
            return "redirect:/servicios";
        } catch (IllegalArgumentException ex) {
            model.addAttribute("error", ex.getMessage());
            model.addAttribute("estados", EstadoServicio.values());
            return "servicios/form";
        }
    }

    @PostMapping("/{id}/eliminar")
    public String eliminar(@PathVariable Long id, Authentication authentication, RedirectAttributes redirectAttributes) {
        if (!puedeEditar(authentication)) {
            redirectAttributes.addFlashAttribute("error", "No tienes permisos para eliminar servicios");
            return "redirect:/servicios";
        }

        try {
            boolean esAdmin = tieneRol(authentication, "ROLE_ADMIN");
            servicioService.eliminar(id, authentication.getName(), esAdmin);
            redirectAttributes.addFlashAttribute("ok", "Servicio eliminado correctamente");
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/servicios";
    }

    @PostMapping("/{id}/iniciar")
    public String iniciarServicio(@PathVariable Long id, Authentication authentication, RedirectAttributes redirectAttributes) {
        if (!puedeGestionarEstado(authentication)) {
            redirectAttributes.addFlashAttribute("error", "No tienes permisos para iniciar servicios");
            return "redirect:/servicios/" + id + "/detalle";
        }

        try {
            boolean esAdmin = tieneRol(authentication, "ROLE_ADMIN");
            servicioService.iniciarServicio(id, authentication.getName(), esAdmin);
            redirectAttributes.addFlashAttribute("ok", "Servicio iniciado correctamente");
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/servicios/" + id + "/detalle";
    }

    @PostMapping("/{id}/finalizar")
    public String finalizarServicio(@PathVariable Long id, Authentication authentication, RedirectAttributes redirectAttributes) {
        if (!puedeGestionarEstado(authentication)) {
            redirectAttributes.addFlashAttribute("error", "No tienes permisos para finalizar servicios");
            return "redirect:/servicios";
        }

        try {
            boolean esAdmin = tieneRol(authentication, "ROLE_ADMIN");
            servicioService.finalizarServicio(id, authentication.getName(), esAdmin);
            redirectAttributes.addFlashAttribute("ok", "Servicio finalizado correctamente");
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/servicios";
    }

    @PostMapping("/{id}/asignarme")
    public String asignarme(@PathVariable Long id, Authentication authentication, RedirectAttributes redirectAttributes) {
        if (!tieneRol(authentication, "ROLE_CONTRATISTA")) {
            redirectAttributes.addFlashAttribute("error", "Solo un contratista puede asignarse al servicio");
            return "redirect:/servicios";
        }

        try {
            servicioService.asignarContratista(id, authentication.getName(), false);
            redirectAttributes.addFlashAttribute("ok", "Te asignaste al servicio correctamente");
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/servicios";
    }

    @GetMapping("/api/tipos")
    @ResponseBody
    public ResponseEntity<List<java.util.Map<String, String>>> getTipos() {
        return ResponseEntity.ok(
            Arrays.stream(TipoServicio.values())
                .map(tipo -> {
                    java.util.Map<String, String> map = new java.util.HashMap<>();
                    map.put("value", tipo.name());
                    map.put("label", tipo.getDisplayName());
                    return map;
                })
                .toList()
        );
    }

    private boolean puedeEditar(Authentication authentication) {
        return tieneRol(authentication, "ROLE_CLIENTE");
    }

    private boolean puedeGestionarEstado(Authentication authentication) {
        return tieneRol(authentication, "ROLE_ADMIN") || tieneRol(authentication, "ROLE_CONTRATISTA");
    }

    private boolean tieneRol(Authentication authentication, String rol) {
        return authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals(rol));
    }

}

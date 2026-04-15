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
            redirectAttributes.addFlashAttribute("error", "Solo CLIENTE o ADMIN pueden crear servicios");
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

            // ── Colores corporativos ──
            byte[] colorPrimario = new byte[]{(byte) 15, (byte) 23, (byte) 42};     // #0f172a (azul oscuro)
            byte[] colorAccento  = new byte[]{(byte) 14, (byte) 165, (byte) 233};    // #0ea5e9
            byte[] colorGrisClaro = new byte[]{(byte) 248, (byte) 250, (byte) 252};  // #f8fafc
            byte[] colorBorde    = new byte[]{(byte) 226, (byte) 232, (byte) 240};   // #e2e8f0

            DataFormat dataFormat = workbook.createDataFormat();
            int numCols = 9;

            // ── Insertar logo ──
            int logoRowStart = 0;
            try {
                ClassPathResource logoRes = new ClassPathResource("static/images/logo-brixo.png");
                if (logoRes.exists()) {
                    try (InputStream logoStream = logoRes.getInputStream()) {
                        byte[] logoBytes = IOUtils.toByteArray(logoStream);
                        int pictureIdx = workbook.addPicture(logoBytes, Workbook.PICTURE_TYPE_PNG);
                        XSSFDrawing drawing = (XSSFDrawing) sheet.createDrawingPatriarch();
                        XSSFClientAnchor anchor = new XSSFClientAnchor(0, 0, 0, 0, 0, 0, 3, 4);
                        anchor.setAnchorType(XSSFClientAnchor.AnchorType.MOVE_AND_RESIZE);
                        drawing.createPicture(anchor, pictureIdx);
                    }
                }
            } catch (Exception ignored) {
                // Si no hay logo, continuar sin él
            }

            // Filas vacías para el logo
            for (int i = 0; i < 4; i++) {
                Row r = sheet.createRow(i);
                r.setHeightInPoints(22);
            }

            // ── Título del reporte ──
            Row titleRow = sheet.createRow(4);
            titleRow.setHeightInPoints(32);
            CellStyle titleStyle = workbook.createCellStyle();
            Font titleFont = workbook.createFont();
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 18);
            titleFont.setFontName("Calibri");
            ((XSSFCellStyle) titleStyle).setFont(titleFont);
            XSSFColor titleColor = new XSSFColor(colorPrimario, null);
            titleFont.setColor(titleColor.getIndex());
            ((XSSFCellStyle) titleStyle).setFont(titleFont);
            Cell titleCell = titleRow.createCell(0);
            titleCell.setCellValue("Reporte de Servicios");
            titleCell.setCellStyle(titleStyle);
            sheet.addMergedRegion(new CellRangeAddress(4, 4, 0, numCols - 1));

            // ── Subtítulo con fecha ──
            Row subtitleRow = sheet.createRow(5);
            subtitleRow.setHeightInPoints(20);
            CellStyle subtitleStyle = workbook.createCellStyle();
            Font subtitleFont = workbook.createFont();
            subtitleFont.setFontHeightInPoints((short) 10);
            subtitleFont.setFontName("Calibri");
            subtitleFont.setColor(IndexedColors.GREY_50_PERCENT.getIndex());
            subtitleStyle.setFont(subtitleFont);
            Cell subtitleCell = subtitleRow.createCell(0);
            subtitleCell.setCellValue("Generado el " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy 'a las' HH:mm")) + "  |  Total: " + servicios.size() + " servicios");
            subtitleCell.setCellStyle(subtitleStyle);
            sheet.addMergedRegion(new CellRangeAddress(5, 5, 0, numCols - 1));

            // Fila separadora
            Row sepRow = sheet.createRow(6);
            sepRow.setHeightInPoints(6);

            // ── Estilo cabecera de tabla ──
            XSSFCellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setFontHeightInPoints((short) 11);
            headerFont.setFontName("Calibri");
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(new XSSFColor(colorPrimario, null));
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            headerStyle.setBorderBottom(BorderStyle.THIN);
            headerStyle.setBorderTop(BorderStyle.THIN);
            headerStyle.setBorderLeft(BorderStyle.THIN);
            headerStyle.setBorderRight(BorderStyle.THIN);

            String[] columnas = {"ID", "Título", "Tipo", "Ubicación", "Presupuesto", "Estado", "Cliente", "Contratista", "Fecha creación"};
            Row headerRow = sheet.createRow(7);
            headerRow.setHeightInPoints(28);
            for (int i = 0; i < columnas.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(columnas[i]);
                cell.setCellStyle(headerStyle);
            }

            // ── Estilos de datos ──
            XSSFCellStyle dataStyle = workbook.createCellStyle();
            Font dataFont = workbook.createFont();
            dataFont.setFontHeightInPoints((short) 10);
            dataFont.setFontName("Calibri");
            dataStyle.setFont(dataFont);
            dataStyle.setBorderBottom(BorderStyle.THIN);
            dataStyle.setBorderTop(BorderStyle.THIN);
            dataStyle.setBorderLeft(BorderStyle.THIN);
            dataStyle.setBorderRight(BorderStyle.THIN);
            dataStyle.setBottomBorderColor(new XSSFColor(colorBorde, null));
            dataStyle.setTopBorderColor(new XSSFColor(colorBorde, null));
            dataStyle.setLeftBorderColor(new XSSFColor(colorBorde, null));
            dataStyle.setRightBorderColor(new XSSFColor(colorBorde, null));
            dataStyle.setVerticalAlignment(VerticalAlignment.CENTER);

            // Estilo fila alterna
            XSSFCellStyle altStyle = workbook.createCellStyle();
            altStyle.cloneStyleFrom(dataStyle);
            altStyle.setFillForegroundColor(new XSSFColor(colorGrisClaro, null));
            altStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            // Estilo moneda
            XSSFCellStyle moneyStyle = workbook.createCellStyle();
            moneyStyle.cloneStyleFrom(dataStyle);
            moneyStyle.setDataFormat(dataFormat.getFormat("$#,##0.00"));
            moneyStyle.setAlignment(HorizontalAlignment.RIGHT);

            XSSFCellStyle moneyAltStyle = workbook.createCellStyle();
            moneyAltStyle.cloneStyleFrom(altStyle);
            moneyAltStyle.setDataFormat(dataFormat.getFormat("$#,##0.00"));
            moneyAltStyle.setAlignment(HorizontalAlignment.RIGHT);

            // Estilo ID centrado
            XSSFCellStyle idStyle = workbook.createCellStyle();
            idStyle.cloneStyleFrom(dataStyle);
            idStyle.setAlignment(HorizontalAlignment.CENTER);

            XSSFCellStyle idAltStyle = workbook.createCellStyle();
            idAltStyle.cloneStyleFrom(altStyle);
            idAltStyle.setAlignment(HorizontalAlignment.CENTER);

            // ── Datos ──
            int rowIdx = 8;
            double totalPresupuesto = 0;
            for (int idx = 0; idx < servicios.size(); idx++) {
                Servicio s = servicios.get(idx);
                Row row = sheet.createRow(rowIdx++);
                row.setHeightInPoints(24);
                boolean esAlt = idx % 2 == 1;
                CellStyle cs = esAlt ? altStyle : dataStyle;
                CellStyle ms = esAlt ? moneyAltStyle : moneyStyle;
                CellStyle is = esAlt ? idAltStyle : idStyle;

                Cell c0 = row.createCell(0); c0.setCellValue(s.getId()); c0.setCellStyle(is);
                Cell c1 = row.createCell(1); c1.setCellValue(s.getTitulo()); c1.setCellStyle(cs);
                Cell c2 = row.createCell(2); c2.setCellValue(s.getTipo() != null ? s.getTipo().getDisplayName() : ""); c2.setCellStyle(cs);
                Cell c3 = row.createCell(3); c3.setCellValue(s.getUbicacion()); c3.setCellStyle(cs);

                double presupuesto = s.getPresupuesto() != null ? s.getPresupuesto().doubleValue() : 0;
                totalPresupuesto += presupuesto;
                Cell c4 = row.createCell(4); c4.setCellValue(presupuesto); c4.setCellStyle(ms);

                Cell c5 = row.createCell(5); c5.setCellValue(s.getEstado() != null ? s.getEstado().name() : ""); c5.setCellStyle(cs);
                Cell c6 = row.createCell(6); c6.setCellValue(s.getCliente() != null ? s.getCliente().getNombre() : ""); c6.setCellStyle(cs);
                Cell c7 = row.createCell(7); c7.setCellValue(s.getContratistaAsignado() != null ? s.getContratistaAsignado().getNombre() : ""); c7.setCellStyle(cs);
                Cell c8 = row.createCell(8); c8.setCellValue(s.getFechaCreacion() != null ? s.getFechaCreacion().format(dtf) : ""); c8.setCellStyle(cs);
            }

            // ── Fila de totales ──
            Row totalRow = sheet.createRow(rowIdx);
            totalRow.setHeightInPoints(28);
            XSSFCellStyle totalLabelStyle = workbook.createCellStyle();
            Font totalFont = workbook.createFont();
            totalFont.setBold(true);
            totalFont.setFontHeightInPoints((short) 11);
            totalFont.setFontName("Calibri");
            totalLabelStyle.setFont(totalFont);
            totalLabelStyle.setFillForegroundColor(new XSSFColor(new byte[]{(byte) 241, (byte) 245, (byte) 249}, null));
            totalLabelStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            totalLabelStyle.setBorderBottom(BorderStyle.MEDIUM);
            totalLabelStyle.setBorderTop(BorderStyle.MEDIUM);
            totalLabelStyle.setBorderLeft(BorderStyle.THIN);
            totalLabelStyle.setBorderRight(BorderStyle.THIN);
            totalLabelStyle.setAlignment(HorizontalAlignment.RIGHT);
            totalLabelStyle.setVerticalAlignment(VerticalAlignment.CENTER);

            XSSFCellStyle totalValueStyle = workbook.createCellStyle();
            totalValueStyle.cloneStyleFrom(totalLabelStyle);
            totalValueStyle.setDataFormat(dataFormat.getFormat("$#,##0.00"));

            // Celdas vacías con estilo
            for (int i = 0; i < numCols; i++) {
                Cell tc = totalRow.createCell(i);
                tc.setCellStyle(totalLabelStyle);
            }
            totalRow.getCell(3).setCellValue("TOTAL:");
            Cell totalValueCell = totalRow.getCell(4);
            totalValueCell.setCellValue(totalPresupuesto);
            totalValueCell.setCellStyle(totalValueStyle);
            totalRow.getCell(5).setCellValue(servicios.size() + " servicios");

            // ── Autoajustar columnas ──
            for (int i = 0; i < numCols; i++) {
                sheet.autoSizeColumn(i);
                // Ancho mínimo
                if (sheet.getColumnWidth(i) < 3500) sheet.setColumnWidth(i, 3500);
            }
            // Columna título más ancha
            if (sheet.getColumnWidth(1) < 8000) sheet.setColumnWidth(1, 8000);

            // ── Pie de página ──
            Row footerRow = sheet.createRow(rowIdx + 2);
            CellStyle footerStyle = workbook.createCellStyle();
            Font footerFont = workbook.createFont();
            footerFont.setItalic(true);
            footerFont.setFontHeightInPoints((short) 9);
            footerFont.setColor(IndexedColors.GREY_50_PERCENT.getIndex());
            footerFont.setFontName("Calibri");
            footerStyle.setFont(footerFont);
            Cell footerCell = footerRow.createCell(0);
            footerCell.setCellValue("BRIXO — Tu obra, nuestro compromiso");
            footerCell.setCellStyle(footerStyle);
            sheet.addMergedRegion(new CellRangeAddress(rowIdx + 2, rowIdx + 2, 0, numCols - 1));

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=servicios-reporte.xlsx")
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
        return tieneRol(authentication, "ROLE_ADMIN") || tieneRol(authentication, "ROLE_CLIENTE");
    }

    private boolean puedeGestionarEstado(Authentication authentication) {
        return tieneRol(authentication, "ROLE_ADMIN") || tieneRol(authentication, "ROLE_CONTRATISTA");
    }

    private boolean tieneRol(Authentication authentication, String rol) {
        return authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals(rol));
    }

}

package com.BRIXO.controller;

import com.BRIXO.model.EstadoServicio;
import com.BRIXO.model.Servicio;
import com.BRIXO.model.TipoServicio;
import com.BRIXO.service.CotizacionService;
import com.BRIXO.service.ServicioService;
import jakarta.validation.Valid;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
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

        model.addAttribute("servicios", servicioService.listarFiltrados(titulo, estado, emailCliente, authentication.getName(), esAdmin, esCliente));
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

        List<Servicio> servicios = servicioService.listarFiltrados(
                titulo, estado, emailCliente, authentication.getName(), esAdmin, esCliente);

        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Servicios");

            // Estilo cabecera
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);

            String[] columnas = {"ID", "Título", "Tipo", "Ubicación", "Presupuesto", "Estado", "Cliente", "Contratista", "Fecha creación"};
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < columnas.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(columnas[i]);
                cell.setCellStyle(headerStyle);
            }

            // Datos
            int rowIdx = 1;
            for (Servicio s : servicios) {
                Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(s.getId());
                row.createCell(1).setCellValue(s.getTitulo());
                row.createCell(2).setCellValue(s.getTipo() != null ? s.getTipo().getDisplayName() : "");
                row.createCell(3).setCellValue(s.getUbicacion());
                row.createCell(4).setCellValue(s.getPresupuesto() != null ? s.getPresupuesto().doubleValue() : 0);
                row.createCell(5).setCellValue(s.getEstado() != null ? s.getEstado().name() : "");
                row.createCell(6).setCellValue(s.getCliente() != null ? s.getCliente().getEmail() : "");
                row.createCell(7).setCellValue(s.getContratistaAsignado() != null ? s.getContratistaAsignado().getEmail() : "");
                row.createCell(8).setCellValue(s.getFechaCreacion() != null ? s.getFechaCreacion().format(dtf) : "");
            }

            // Autoajustar columnas
            for (int i = 0; i < columnas.length; i++) {
                sheet.autoSizeColumn(i);
            }

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

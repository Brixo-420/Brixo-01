package com.BRIXO.controller;

import com.BRIXO.model.EstadoServicio;
import com.BRIXO.model.Servicio;
import com.BRIXO.model.TipoServicio;
import com.BRIXO.service.ServicioService;
import jakarta.validation.Valid;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
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
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Arrays;

@Controller
@RequestMapping("/servicios")
public class ServicioController {

    private final ServicioService servicioService;

    public ServicioController(ServicioService servicioService) {
        this.servicioService = servicioService;
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

        @GetMapping(value = "/reporte.csv", produces = "text/csv")
        @ResponseBody
        public ResponseEntity<byte[]> exportarCsv(
            @RequestParam(required = false) String titulo,
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) String emailCliente,
            Authentication authentication
        ) {
        boolean esAdmin = tieneRol(authentication, "ROLE_ADMIN");
        boolean esCliente = tieneRol(authentication, "ROLE_CLIENTE");

        List<Servicio> servicios = servicioService.listarFiltrados(
            titulo,
            estado,
            emailCliente,
            authentication.getName(),
            esAdmin,
            esCliente
        );

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        StringBuilder csv = new StringBuilder();
        csv.append("id,titulo,descripcion,ubicacion,presupuesto,estado,clienteEmail,fechaCreacion\n");

        for (Servicio servicio : servicios) {
            csv.append(servicio.getId()).append(",")
                .append(escaparCsv(servicio.getTitulo())).append(",")
                .append(escaparCsv(servicio.getDescripcion())).append(",")
                .append(escaparCsv(servicio.getUbicacion())).append(",")
                .append(servicio.getPresupuesto()).append(",")
                .append(servicio.getEstado()).append(",")
                .append(escaparCsv(servicio.getCliente() != null ? servicio.getCliente().getEmail() : "")).append(",")
                .append(servicio.getFechaCreacion() != null ? servicio.getFechaCreacion().format(formatter) : "")
                .append("\n");
        }

        byte[] body = csv.toString().getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=servicios-reporte.csv")
            .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
            .body(body);
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

    @PostMapping("/importar-excel")
    public String importarExcel(
            @RequestParam("archivo") MultipartFile archivo,
            Authentication authentication,
            RedirectAttributes redirectAttributes
    ) {
        if (!puedeEditar(authentication)) {
            redirectAttributes.addFlashAttribute("error", "No tienes permisos para importar servicios");
            return "redirect:/servicios";
        }

        if (archivo == null || archivo.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Debes seleccionar un archivo .xlsx");
            return "redirect:/servicios";
        }

        if (!archivo.getOriginalFilename().toLowerCase().endsWith(".xlsx")) {
            redirectAttributes.addFlashAttribute("error", "Formato invalido. Usa un archivo .xlsx");
            return "redirect:/servicios";
        }

        int creados = 0;
        DataFormatter formatter = new DataFormatter();
        try (Workbook workbook = new XSSFWorkbook(archivo.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null || filaVacia(row, formatter)) {
                    continue;
                }

                Servicio servicio = new Servicio();
                servicio.setTitulo(valorTexto(row.getCell(0), formatter));
                servicio.setDescripcion(valorTexto(row.getCell(1), formatter));
                servicio.setUbicacion(valorTexto(row.getCell(2), formatter));

                String presupuestoTexto = valorTexto(row.getCell(3), formatter);
                if (presupuestoTexto.isBlank()) {
                    throw new IllegalArgumentException("Fila " + (i + 1) + ": presupuesto vacio");
                }
                servicio.setPresupuesto(new BigDecimal(presupuestoTexto.replace(",", ".")));

                String estadoTexto = valorTexto(row.getCell(4), formatter);
                EstadoServicio estado = estadoTexto.isBlank() ? EstadoServicio.ABIERTO : EstadoServicio.valueOf(estadoTexto.toUpperCase());
                servicio.setEstado(estado);

                servicioService.crear(servicio, authentication.getName());
                creados++;
            }
            redirectAttributes.addFlashAttribute("ok", "Importacion completada. Servicios creados: " + creados);
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("error", "Error en importacion: " + ex.getMessage());
        } catch (IOException ex) {
            redirectAttributes.addFlashAttribute("error", "No se pudo leer el archivo Excel");
        }

        return "redirect:/servicios";
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
            
            model.addAttribute("servicio", servicio);
            model.addAttribute("esAdmin", esAdmin);
            model.addAttribute("esCliente", esCliente);
            model.addAttribute("esContratista", esContratista);
            model.addAttribute("esClientePropietario", esClientePropietario);
            model.addAttribute("esContratistaAsignado", esContratistaAsignado);
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
            return "redirect:/servicios";
        }

        try {
            boolean esAdmin = tieneRol(authentication, "ROLE_ADMIN");
            servicioService.iniciarServicio(id, authentication.getName(), esAdmin);
            redirectAttributes.addFlashAttribute("ok", "Servicio iniciado correctamente");
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/servicios";
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

    private String escaparCsv(String value) {
        if (value == null) {
            return "\"\"";
        }
        String escaped = value.replace("\"", "\"\"");
        return "\"" + escaped + "\"";
    }

    private boolean filaVacia(Row row, DataFormatter formatter) {
        for (int i = 0; i < 5; i++) {
            Cell cell = row.getCell(i);
            if (!valorTexto(cell, formatter).isBlank()) {
                return false;
            }
        }
        return true;
    }

    private String valorTexto(Cell cell, DataFormatter formatter) {
        if (cell == null) {
            return "";
        }
        return formatter.formatCellValue(cell).trim();
    }
}

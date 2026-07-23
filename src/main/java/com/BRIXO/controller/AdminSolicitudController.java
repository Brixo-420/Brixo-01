package com.BRIXO.controller;

import com.BRIXO.model.SolicitudContratista;
import com.BRIXO.service.SolicitudContratistaService;
import com.BRIXO.repository.SolicitudContratistaRepository;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Controller
@RequestMapping("/admin/solicitudes")
public class AdminSolicitudController {

    private final SolicitudContratistaService solicitudService;
    private final SolicitudContratistaRepository solicitudRepository;

    public AdminSolicitudController(SolicitudContratistaService solicitudService,
                                    SolicitudContratistaRepository solicitudRepository) {
        this.solicitudService = solicitudService;
        this.solicitudRepository = solicitudRepository;
    }

    @GetMapping
    public String listar(Model model) {
        model.addAttribute("solicitudes", solicitudService.listarTodas());
        model.addAttribute("pendientes", solicitudService.contarPendientes());
        return "admin/solicitudes";
    }

    @PostMapping("/{id}/aprobar")
    public String aprobar(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        solicitudService.aprobar(id);
        redirectAttributes.addFlashAttribute("ok", "Solicitud aprobada. El usuario ahora es contratista.");
        return "redirect:/admin/solicitudes";
    }

    @PostMapping("/{id}/rechazar")
    public String rechazar(@PathVariable Long id,
                           @RequestParam(required = false) String motivoRechazo,
                           RedirectAttributes redirectAttributes) {
        solicitudService.rechazar(id, motivoRechazo);
        redirectAttributes.addFlashAttribute("ok", "Solicitud rechazada. El usuario fue notificado.");
        return "redirect:/admin/solicitudes";
    }

    @GetMapping("/{id}/foto/{tipo}")
    public ResponseEntity<byte[]> verFoto(@PathVariable Long id, @PathVariable String tipo) {
        SolicitudContratista solicitud = solicitudRepository.findById(id).orElse(null);
        if (solicitud == null) return ResponseEntity.notFound().build();

        String rutaStr = switch (tipo) {
            case "documento"    -> solicitud.getDocumentoPath();
            case "selfie"       -> solicitud.getSelfiePath();
            case "certificado"  -> solicitud.getCertificadoPath();
            case "recibo"       -> solicitud.getReciboServiciosPath();
            case "antecedentes" -> solicitud.getAntecedentesPath();
            default             -> null;
        };

        if (rutaStr == null || rutaStr.isBlank()) return ResponseEntity.notFound().build();

        try {
            Path ruta = Paths.get(rutaStr);
            if (!Files.exists(ruta)) return ResponseEntity.notFound().build();

            byte[] bytes = Files.readAllBytes(ruta);
            String lower = rutaStr.toLowerCase();
            MediaType mediaType;
            if (lower.endsWith(".pdf"))  mediaType = MediaType.APPLICATION_PDF;
            else if (lower.endsWith(".png")) mediaType = MediaType.IMAGE_PNG;
            else mediaType = MediaType.IMAGE_JPEG;

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(mediaType);
            headers.setContentLength(bytes.length);
            if (mediaType.equals(MediaType.APPLICATION_PDF)) {
                String filename = tipo + "_" + id + ".pdf";
                headers.set("Content-Disposition", "inline; filename=\"" + filename + "\"");
            }
            return new ResponseEntity<>(bytes, headers, HttpStatus.OK);

        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}

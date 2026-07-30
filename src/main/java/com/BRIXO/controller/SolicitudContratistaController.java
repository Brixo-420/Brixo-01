package com.BRIXO.controller;

import com.BRIXO.model.Usuario;
import com.BRIXO.model.ValidacionIdentidad;
import com.BRIXO.service.IdentityValidationService;
import com.BRIXO.service.SolicitudContratistaService;
import com.BRIXO.service.UsuarioService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Controller
public class SolicitudContratistaController {

    private final SolicitudContratistaService solicitudService;
    private final IdentityValidationService identityService;
    private final UsuarioService usuarioService;
    private final R2FileService r2FileService;

    public SolicitudContratistaController(SolicitudContratistaService solicitudService,
                                          IdentityValidationService identityService,
                                          UsuarioService usuarioService,
                                          R2FileService r2FileService) {
        this.solicitudService = solicitudService;
        this.identityService = identityService;
        this.usuarioService = usuarioService;
        this.r2FileService = r2FileService;
    }

    @PostMapping("/solicitar-contratista")
    public String solicitarContratista(Authentication authentication,
                                       HttpSession session,
                                       RedirectAttributes redirectAttributes) {
        try {
            // Solo verifica elegibilidad — NO crea nada en BD todavía
            solicitudService.validarElegibilidad(authentication.getName());
            session.setAttribute("puedeVerificarContratista", true);
            return "redirect:/contratista/verificar";
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
            return "redirect:/dashboard";
        }
    }

    @GetMapping("/contratista/verificar")
    public String verificarForm(HttpSession session, Model model) {
        if (session.getAttribute("puedeVerificarContratista") == null) {
            return "redirect:/dashboard";
        }
        return "verificar-identidad";
    }

    @PostMapping("/contratista/verificar")
    public String verificar(HttpSession session,
                            Authentication authentication,
                            @RequestParam String numeroCedula,
                            @RequestParam(required = false) String areaEspecialidad,
                            @RequestParam MultipartFile documento,
                            @RequestParam MultipartFile selfie,
                            @RequestParam(required = false) MultipartFile certificado,
                            @RequestParam(required = false) MultipartFile reciboServicios,
                            @RequestParam(required = false) MultipartFile antecedentes,
                            RedirectAttributes ra,
                            Model model) {

        if (session.getAttribute("puedeVerificarContratista") == null)
            return "redirect:/dashboard";

        // Retener valores del formulario para pre-rellenar en caso de error
        if (numeroCedula != null) model.addAttribute("cedulaPre", numeroCedula.trim());
        if (areaEspecialidad != null && !areaEspecialidad.isBlank())
            model.addAttribute("areaPreseleccionada", areaEspecialidad.trim());

        if (numeroCedula == null || numeroCedula.isBlank()) {
            model.addAttribute("error", "El número de cédula es obligatorio.");
            return "verificar-identidad";
        }
        if (areaEspecialidad == null || areaEspecialidad.isBlank()) {
            model.addAttribute("error", "Debes seleccionar tu área de especialidad.");
            return "verificar-identidad";
        }
        if (documento.isEmpty() || selfie.isEmpty()) {
            model.addAttribute("error", "Debes subir la foto de tu cédula y la selfie.");
            return "verificar-identidad";
        }
        if (certificado == null || certificado.isEmpty()) {
            model.addAttribute("error", "Debes subir el certificado de especialidad.");
            return "verificar-identidad";
        }
        if (reciboServicios == null || reciboServicios.isEmpty()) {
            model.addAttribute("error", "Debes subir un recibo de servicios públicos.");
            return "verificar-identidad";
        }
        if (antecedentes == null || antecedentes.isEmpty()) {
            model.addAttribute("error", "Debes subir el certificado de antecedentes policiales.");
            return "verificar-identidad";
        }

        try {
            Usuario usuario = usuarioService.buscarPorEmail(authentication.getName());
            ValidacionIdentidad result = identityService.validar(usuario, documento, selfie);

            String certPath   = guardarArchivo(certificado,     "cert");
            String reciboPath = guardarArchivo(reciboServicios, "recibo");
            String antecPath  = guardarArchivo(antecedentes,    "antec");

            // Crea la solicitud completa en BD y notifica al admin solo ahora
            solicitudService.crearYGuardar(
                    authentication.getName(),
                    numeroCedula.trim(),
                    result.isVerificado(),
                    result.getScore(),
                    result.getError(),
                    result.getDocumentoPath(),
                    result.getSelfiePath(),
                    areaEspecialidad,
                    certPath,
                    reciboPath,
                    antecPath
            );
            session.removeAttribute("puedeVerificarContratista");

            String msg = result.isVerificado()
                    ? "Solicitud enviada. Tu identidad fue verificada. El administrador revisará tus documentos."
                    : "Solicitud enviada. El administrador revisará tu caso manualmente.";
            ra.addFlashAttribute("ok", msg);
            return "redirect:/dashboard";

        } catch (Exception e) {
            model.addAttribute("error", "Error al procesar los archivos: " + e.getMessage());
            return "verificar-identidad";
        }
    }

    private String guardarArchivo(MultipartFile file, String prefix) throws IOException {
        if (file == null || file.isEmpty()) return null;
        String original = file.getOriginalFilename();
        String ext = "";
        if (original != null && original.contains("."))
            ext = original.substring(original.lastIndexOf('.'));
        String filename = prefix + "_" + UUID.randomUUID() + ext;
        return r2FileService.uploadFile(file.getBytes(), filename);
    }
}

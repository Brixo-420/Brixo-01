package com.BRIXO.controller;

import com.BRIXO.model.Usuario;
import com.BRIXO.service.ResenaService;
import com.BRIXO.service.UsuarioService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class ResenaController {

    private static final Logger log = LoggerFactory.getLogger(ResenaController.class);

    private final ResenaService resenaService;
    private final UsuarioService usuarioService;

    public ResenaController(ResenaService resenaService, UsuarioService usuarioService) {
        this.resenaService = resenaService;
        this.usuarioService = usuarioService;
    }

    // ── Resenas sobre un servicio ────────────────────────────────────────────

    @PostMapping("/servicios/{servicioId}/resenas")
    public String crear(@PathVariable Long servicioId,
                        @RequestParam(required = false) Integer calificacion,
                        @RequestParam(required = false) String comentario,
                        Authentication authentication,
                        RedirectAttributes redirectAttributes) {
        try {
            resenaService.crear(servicioId, authentication.getName(), calificacion, comentario);
            redirectAttributes.addFlashAttribute("ok", "¡Gracias! Tu reseña se publicó correctamente");
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        } catch (DataAccessException ex) {
            log.error("Error de base de datos al crear la resena del servicio {}", servicioId, ex);
            redirectAttributes.addFlashAttribute("error", "No se pudo guardar la reseña. Intentalo de nuevo.");
        }
        return "redirect:/servicios/" + servicioId + "/detalle";
    }

    @PostMapping("/servicios/{servicioId}/resenas/{resenaId}/editar")
    public String editar(@PathVariable Long servicioId,
                         @PathVariable Long resenaId,
                         @RequestParam(required = false) Integer calificacion,
                         @RequestParam(required = false) String comentario,
                         Authentication authentication,
                         RedirectAttributes redirectAttributes) {
        try {
            resenaService.actualizar(resenaId, authentication.getName(), calificacion, comentario);
            redirectAttributes.addFlashAttribute("ok", "Reseña actualizada correctamente");
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/servicios/" + servicioId + "/detalle";
    }

    @PostMapping("/servicios/{servicioId}/resenas/{resenaId}/eliminar")
    public String eliminar(@PathVariable Long servicioId,
                           @PathVariable Long resenaId,
                           Authentication authentication,
                           RedirectAttributes redirectAttributes) {
        try {
            resenaService.eliminar(resenaId, tieneRol(authentication, "ROLE_ADMIN"));
            redirectAttributes.addFlashAttribute("ok", "Reseña eliminada");
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/servicios/" + servicioId + "/detalle";
    }

    // ── Mis resenas ──────────────────────────────────────────────────────────

    @GetMapping("/resenas")
    public String misResenas(Authentication authentication, Model model) {
        String email = authentication.getName();
        Usuario usuario = usuarioService.buscarPorEmail(email);

        model.addAttribute("usuario", usuario);
        model.addAttribute("resumen", resenaService.resumen(usuario.getId()));
        model.addAttribute("recibidas", resenaService.listarRecibidas(email));
        model.addAttribute("escritas", resenaService.listarEscritas(email));
        model.addAttribute("pendientes", resenaService.pendientesPorResenar(email));
        return "resenas/lista";
    }

    // ── Reputacion publica de un usuario ─────────────────────────────────────

    @GetMapping("/resenas/usuario/{usuarioId}")
    public String reputacionUsuario(@PathVariable Long usuarioId,
                                    Model model,
                                    RedirectAttributes redirectAttributes) {
        try {
            Usuario usuario = usuarioService.buscarPorId(usuarioId);
            model.addAttribute("perfil", usuario);
            model.addAttribute("resumen", resenaService.resumen(usuarioId));
            model.addAttribute("resenas", resenaService.listarRecibidasPorUsuario(usuarioId));
            return "resenas/usuario";
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
            return "redirect:/resenas";
        }
    }

    private boolean tieneRol(Authentication authentication, String rol) {
        return authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals(rol));
    }
}

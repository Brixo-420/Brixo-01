package com.BRIXO.controller;

import com.BRIXO.model.Usuario;
import com.BRIXO.service.UsuarioService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/usuarios")
public class UsuarioController {

    private final UsuarioService usuarioService;

    public UsuarioController(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    @GetMapping
    public String listar(Model model) {
        model.addAttribute("usuarios", usuarioService.listarTodos());
        return "usuarios/lista";
    }

    @GetMapping("/nuevo")
    public String nuevo(Model model) {
        model.addAttribute("usuario", new Usuario());
        model.addAttribute("roles", usuarioService.listarRoles());
        return "usuarios/form";
    }

    @PostMapping
    public String crear(
            @Valid @ModelAttribute("usuario") Usuario usuario,
            BindingResult bindingResult,
            @RequestParam(value = "rolId", required = false) Long rolId,
            RedirectAttributes redirectAttributes,
            Model model
    ) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("roles", usuarioService.listarRoles());
            return "usuarios/form";
        }

        try {
            usuarioService.crear(usuario, rolId);
            redirectAttributes.addFlashAttribute("ok", "Usuario creado correctamente");
            return "redirect:/admin/usuarios";
        } catch (IllegalArgumentException ex) {
            model.addAttribute("roles", usuarioService.listarRoles());
            model.addAttribute("error", ex.getMessage());
            return "usuarios/form";
        }
    }

    @GetMapping("/{id}/editar")
    public String editar(@PathVariable Long id, Model model) {
        model.addAttribute("usuario", usuarioService.buscarPorId(id));
        model.addAttribute("roles", usuarioService.listarRoles());
        return "usuarios/form";
    }

    @PostMapping("/{id}")
    public String actualizar(
            @PathVariable Long id,
            @Valid @ModelAttribute("usuario") Usuario usuario,
            BindingResult bindingResult,
            @RequestParam(value = "rolId", required = false) Long rolId,
            RedirectAttributes redirectAttributes,
            Model model
    ) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("roles", usuarioService.listarRoles());
            return "usuarios/form";
        }

        try {
            usuarioService.actualizar(id, usuario, rolId);
            redirectAttributes.addFlashAttribute("ok", "Usuario actualizado correctamente");
            return "redirect:/admin/usuarios";
        } catch (IllegalArgumentException ex) {
            model.addAttribute("roles", usuarioService.listarRoles());
            model.addAttribute("error", ex.getMessage());
            return "usuarios/form";
        }
    }

    @PostMapping("/{id}/eliminar")
    public String eliminar(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            usuarioService.eliminar(id);
            redirectAttributes.addFlashAttribute("ok", "Usuario eliminado correctamente");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin/usuarios";
    }
}

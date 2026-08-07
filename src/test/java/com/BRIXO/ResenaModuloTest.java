package com.BRIXO;

import com.BRIXO.model.EstadoServicio;
import com.BRIXO.model.Resena;
import com.BRIXO.model.Servicio;
import com.BRIXO.model.TipoResena;
import com.BRIXO.model.TipoServicio;
import com.BRIXO.model.Usuario;
import com.BRIXO.repository.NotificacionRepository;
import com.BRIXO.repository.ResenaRepository;
import com.BRIXO.repository.RolRepository;
import com.BRIXO.repository.ServicioRepository;
import com.BRIXO.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * Flujo completo del modulo de resenas sobre una base H2 en memoria. Al pedir
 * las vistas tambien se comprueba que las plantillas Thymeleaf rendericen.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:brixo_test;DB_CLOSE_DELAY=-1;MODE=MySQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false"
})
class ResenaModuloTest {

    private static final String CLIENTE = "cliente.resenas@test.com";
    private static final String CONTRATISTA = "contratista.resenas@test.com";
    private static final String INTRUSO = "intruso.resenas@test.com";

    @Autowired MockMvc mockMvc;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired RolRepository rolRepository;
    @Autowired ServicioRepository servicioRepository;
    @Autowired ResenaRepository resenaRepository;
    @Autowired NotificacionRepository notificacionRepository;

    private Servicio servicio;

    @BeforeEach
    void prepararDatos() {
        Usuario cliente = crearUsuario(CLIENTE, "Ana Cliente", "CLIENTE");
        Usuario contratista = crearUsuario(CONTRATISTA, "Beto Contratista", "CONTRATISTA");
        crearUsuario(INTRUSO, "Intruso Curioso", "CONTRATISTA");

        servicio = new Servicio();
        servicio.setTitulo("Cambio de tuberia en cocina");
        servicio.setDescripcion("Reemplazo completo de la tuberia bajo el lavaplatos");
        servicio.setTipo(TipoServicio.PLOMERIA);
        servicio.setUbicacion("Bogota");
        servicio.setPresupuesto(new BigDecimal("350000"));
        servicio.setEstado(EstadoServicio.CERRADO);
        servicio.setCliente(cliente);
        servicio.setContratistaAsignado(contratista);
        servicio = servicioRepository.save(servicio);
    }

    private Usuario crearUsuario(String email, String nombre, String rol) {
        Usuario u = new Usuario();
        u.setNombre(nombre);
        u.setEmail(email);
        u.setTelefono("3001234567");
        u.setDireccion("Calle 1 # 2-3");
        u.setCiudad("Bogota");
        u.setPassword("$2a$10$abcdefghijklmnopqrstuv");
        u.setRol(rolRepository.findByNombre(rol).orElseThrow());
        return usuarioRepository.save(u);
    }

    // ── Creacion ─────────────────────────────────────────────────────────────

    @Test
    @WithMockUser(username = CLIENTE, roles = "CLIENTE")
    void elClienteCalificaAlContratista() throws Exception {
        mockMvc.perform(post("/servicios/{id}/resenas", servicio.getId()).with(csrf())
                        .param("calificacion", "5")
                        .param("comentario", "Trabajo impecable y muy puntual"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("ok"));

        List<Resena> resenas = resenaRepository.findByServicioIdOrderByFechaCreacionDesc(servicio.getId());
        assertThat(resenas).hasSize(1);
        assertThat(resenas.get(0).getCalificacion()).isEqualTo(5);
        assertThat(resenas.get(0).getTipo()).isEqualTo(TipoResena.CLIENTE_A_CONTRATISTA);
        assertThat(resenas.get(0).getDestinatario().getEmail()).isEqualTo(CONTRATISTA);
        assertThat(notificacionRepository.countByUsuarioEmailAndLeidaFalse(CONTRATISTA)).isEqualTo(1);
    }

    @Test
    @WithMockUser(username = CONTRATISTA, roles = "CONTRATISTA")
    void elContratistaCalificaAlCliente() throws Exception {
        mockMvc.perform(post("/servicios/{id}/resenas", servicio.getId()).with(csrf())
                        .param("calificacion", "4"))
                .andExpect(flash().attributeExists("ok"));

        Resena resena = resenaRepository.findByServicioIdAndAutorEmail(servicio.getId(), CONTRATISTA).orElseThrow();
        assertThat(resena.getTipo()).isEqualTo(TipoResena.CONTRATISTA_A_CLIENTE);
        assertThat(resena.getDestinatario().getEmail()).isEqualTo(CLIENTE);
        assertThat(resena.getComentario()).isNull();
    }

    // ── Reglas de negocio ────────────────────────────────────────────────────

    @Test
    @WithMockUser(username = CLIENTE, roles = "CLIENTE")
    void noSePuedeResenarDosVecesElMismoServicio() throws Exception {
        mockMvc.perform(post("/servicios/{id}/resenas", servicio.getId()).with(csrf())
                .param("calificacion", "5"));

        mockMvc.perform(post("/servicios/{id}/resenas", servicio.getId()).with(csrf())
                        .param("calificacion", "1"))
                .andExpect(flash().attributeExists("error"));

        assertThat(resenaRepository.findByServicioIdOrderByFechaCreacionDesc(servicio.getId())).hasSize(1);
    }

    @Test
    @WithMockUser(username = INTRUSO, roles = "CONTRATISTA")
    void unTerceroNoPuedeResenar() throws Exception {
        mockMvc.perform(post("/servicios/{id}/resenas", servicio.getId()).with(csrf())
                        .param("calificacion", "1"))
                .andExpect(flash().attributeExists("error"));

        assertThat(resenaRepository.findByServicioIdOrderByFechaCreacionDesc(servicio.getId())).isEmpty();
    }

    @Test
    @WithMockUser(username = CLIENTE, roles = "CLIENTE")
    void noSePuedeResenarUnServicioSinFinalizar() throws Exception {
        servicio.setEstado(EstadoServicio.EN_PROCESO);
        servicioRepository.save(servicio);

        mockMvc.perform(post("/servicios/{id}/resenas", servicio.getId()).with(csrf())
                        .param("calificacion", "5"))
                .andExpect(flash().attributeExists("error"));

        assertThat(resenaRepository.findByServicioIdOrderByFechaCreacionDesc(servicio.getId())).isEmpty();
    }

    @Test
    @WithMockUser(username = CLIENTE, roles = "CLIENTE")
    void laCalificacionFueraDeRangoSeRechaza() throws Exception {
        mockMvc.perform(post("/servicios/{id}/resenas", servicio.getId()).with(csrf())
                        .param("calificacion", "9"))
                .andExpect(flash().attributeExists("error"));

        assertThat(resenaRepository.findByServicioIdOrderByFechaCreacionDesc(servicio.getId())).isEmpty();
    }

    // ── Edicion y moderacion ─────────────────────────────────────────────────

    @Test
    @WithMockUser(username = CLIENTE, roles = "CLIENTE")
    void elAutorPuedeEditarSuResena() throws Exception {
        mockMvc.perform(post("/servicios/{id}/resenas", servicio.getId()).with(csrf())
                .param("calificacion", "5").param("comentario", "Todo bien"));
        Resena resena = resenaRepository.findByServicioIdAndAutorEmail(servicio.getId(), CLIENTE).orElseThrow();

        mockMvc.perform(post("/servicios/{sid}/resenas/{rid}/editar", servicio.getId(), resena.getId()).with(csrf())
                        .param("calificacion", "3").param("comentario", "Se demoro mas de lo pactado"))
                .andExpect(flash().attributeExists("ok"));

        Resena editada = resenaRepository.findById(resena.getId()).orElseThrow();
        assertThat(editada.getCalificacion()).isEqualTo(3);
        assertThat(editada.getComentario()).isEqualTo("Se demoro mas de lo pactado");
        assertThat(editada.getFechaEdicion()).isNotNull();
    }

    @Test
    @WithMockUser(username = CONTRATISTA, roles = "CONTRATISTA")
    void nadiePuedeEditarLaResenaDeOtro() throws Exception {
        Resena ajena = guardarResena(5, "Excelente", TipoResena.CLIENTE_A_CONTRATISTA, CLIENTE, CONTRATISTA);

        mockMvc.perform(post("/servicios/{sid}/resenas/{rid}/editar", servicio.getId(), ajena.getId()).with(csrf())
                        .param("calificacion", "1"))
                .andExpect(flash().attributeExists("error"));

        assertThat(resenaRepository.findById(ajena.getId()).orElseThrow().getCalificacion()).isEqualTo(5);
    }

    @Test
    @WithMockUser(username = CONTRATISTA, roles = "CONTRATISTA")
    void soloElAdminPuedeEliminarResenas() throws Exception {
        Resena resena = guardarResena(1, "Comentario inapropiado", TipoResena.CLIENTE_A_CONTRATISTA, CLIENTE, CONTRATISTA);

        mockMvc.perform(post("/servicios/{sid}/resenas/{rid}/eliminar", servicio.getId(), resena.getId()).with(csrf()))
                .andExpect(flash().attributeExists("error"));

        assertThat(resenaRepository.findById(resena.getId())).isPresent();
    }

    @Test
    @WithMockUser(username = "admin@brixo.com", roles = "ADMIN")
    void elAdminModeraUnaResena() throws Exception {
        Resena resena = guardarResena(1, "Comentario inapropiado", TipoResena.CLIENTE_A_CONTRATISTA, CLIENTE, CONTRATISTA);

        mockMvc.perform(post("/servicios/{sid}/resenas/{rid}/eliminar", servicio.getId(), resena.getId()).with(csrf()))
                .andExpect(flash().attributeExists("ok"));

        assertThat(resenaRepository.findById(resena.getId())).isEmpty();
    }

    // ── Vistas ───────────────────────────────────────────────────────────────

    @Test
    @WithMockUser(username = CLIENTE, roles = "CLIENTE")
    void elDetalleDelServicioMuestraElFormularioDeResena() throws Exception {
        mockMvc.perform(get("/servicios/{id}/detalle", servicio.getId()))
                .andExpect(status().isOk())
                .andExpect(view().name("servicios/detalle"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Reseñas del trabajo")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Beto Contratista")));
    }

    @Test
    @WithMockUser(username = CONTRATISTA, roles = "CONTRATISTA")
    void laPaginaDeMisResenasCalculaElPromedio() throws Exception {
        guardarResena(5, "Excelente", TipoResena.CLIENTE_A_CONTRATISTA, CLIENTE, CONTRATISTA);
        Servicio otro = otroServicioCerrado();
        Resena segunda = new Resena();
        segunda.setServicio(otro);
        segunda.setAutor(usuarioRepository.findByEmail(CLIENTE).orElseThrow());
        segunda.setDestinatario(usuarioRepository.findByEmail(CONTRATISTA).orElseThrow());
        segunda.setCalificacion(4);
        segunda.setTipo(TipoResena.CLIENTE_A_CONTRATISTA);
        resenaRepository.save(segunda);

        mockMvc.perform(get("/resenas"))
                .andExpect(status().isOk())
                .andExpect(view().name("resenas/lista"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("4.5")));
    }

    @Test
    @WithMockUser(username = CLIENTE, roles = "CLIENTE")
    void misResenasListaLosTrabajosPendientesPorCalificar() throws Exception {
        mockMvc.perform(get("/resenas"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Trabajos por calificar")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Cambio de tuberia en cocina")));
    }

    @Test
    @WithMockUser(username = CLIENTE, roles = "CLIENTE")
    void laReputacionPublicaMuestraLasOpiniones() throws Exception {
        guardarResena(5, "Muy recomendado", TipoResena.CLIENTE_A_CONTRATISTA, CLIENTE, CONTRATISTA);
        Long contratistaId = usuarioRepository.findByEmail(CONTRATISTA).orElseThrow().getId();

        mockMvc.perform(get("/resenas/usuario/{id}", contratistaId))
                .andExpect(status().isOk())
                .andExpect(view().name("resenas/usuario"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Muy recomendado")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("5.0")));
    }

    @Test
    @WithMockUser(username = CLIENTE, roles = "CLIENTE")
    void elPerfilMuestraLaReputacion() throws Exception {
        mockMvc.perform(get("/perfil"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Sin reseñas todavía")));
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private Resena guardarResena(int calificacion, String comentario, TipoResena tipo, String autor, String destinatario) {
        Resena r = new Resena();
        r.setServicio(servicio);
        r.setAutor(usuarioRepository.findByEmail(autor).orElseThrow());
        r.setDestinatario(usuarioRepository.findByEmail(destinatario).orElseThrow());
        r.setCalificacion(calificacion);
        r.setComentario(comentario);
        r.setTipo(tipo);
        return resenaRepository.save(r);
    }

    private Servicio otroServicioCerrado() {
        Servicio s = new Servicio();
        s.setTitulo("Pintura de sala");
        s.setDescripcion("Pintar la sala con dos manos de vinilo");
        s.setTipo(TipoServicio.PINTURA);
        s.setUbicacion("Bogota");
        s.setPresupuesto(new BigDecimal("200000"));
        s.setEstado(EstadoServicio.CERRADO);
        s.setCliente(usuarioRepository.findByEmail(CLIENTE).orElseThrow());
        s.setContratistaAsignado(usuarioRepository.findByEmail(CONTRATISTA).orElseThrow());
        return servicioRepository.save(s);
    }
}

package com.hawkify.config;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.hawkify.auditoria.AuditoriaService;
import com.hawkify.catalogo.Categoria;
import com.hawkify.catalogo.CategoriaRepository;
import com.hawkify.catalogo.EstadoFisico;
import com.hawkify.catalogo.EstadoPublicacion;
import com.hawkify.catalogo.EstadoUnidad;
import com.hawkify.catalogo.Marca;
import com.hawkify.catalogo.MarcaRepository;
import com.hawkify.catalogo.Producto;
import com.hawkify.catalogo.ProductoEspecificacion;
import com.hawkify.catalogo.ProductoImagen;
import com.hawkify.catalogo.ProductoRepository;
import com.hawkify.catalogo.UnidadProducto;
import com.hawkify.catalogo.UnidadProductoRepository;
import com.hawkify.common.Formatos;
import com.hawkify.notificacion.NotificacionService;
import com.hawkify.notificacion.TipoNotificacion;
import com.hawkify.resena.AccionModeracion;
import com.hawkify.resena.Calificacion;
import com.hawkify.resena.CalificacionRepository;
import com.hawkify.resena.EstadoModeracion;
import com.hawkify.resena.ModeracionResena;
import com.hawkify.resena.ModeracionResenaRepository;
import com.hawkify.resena.Resena;
import com.hawkify.resena.ResenaRepository;
import com.hawkify.reserva.Devolucion;
import com.hawkify.reserva.DevolucionRepository;
import com.hawkify.reserva.EstadoEquipo;
import com.hawkify.reserva.EstadoPago;
import com.hawkify.reserva.EstadoReserva;
import com.hawkify.reserva.MetodoPago;
import com.hawkify.reserva.ModalidadEntrega;
import com.hawkify.reserva.PagoSimulado;
import com.hawkify.reserva.PagoSimuladoRepository;
import com.hawkify.reserva.Reserva;
import com.hawkify.reserva.ReservaRepository;
import com.hawkify.usuario.CodigoPermiso;
import com.hawkify.usuario.Direccion;
import com.hawkify.usuario.DireccionRepository;
import com.hawkify.usuario.EstadoUsuario;
import com.hawkify.usuario.PermisoRepository;
import com.hawkify.usuario.RolNombre;
import com.hawkify.usuario.RolRepository;
import com.hawkify.usuario.Usuario;
import com.hawkify.usuario.UsuarioRepository;
import com.hawkify.wishlist.ListaDeseosItem;
import com.hawkify.wishlist.ListaDeseosItemRepository;

import lombok.RequiredArgsConstructor;

/**
 * Datos de demostracion coherentes: cuentas de los tres roles, inventario con
 * unidades fisicas, historial de alquileres de varios meses (para reportes),
 * reservas en curso, resenas (una moderada), wishlist y notificaciones.
 * Solo corre si el catalogo esta vacio y hawkify.demo.seed=true.
 */
@Component
@Order(2)
@RequiredArgsConstructor
public class DemoSeeder implements ApplicationRunner {

    public static final String PASSWORD_DEMO = "Hawkify2026*";
    private static final Logger log = LoggerFactory.getLogger(DemoSeeder.class);
    private static final ZoneId ZONA = ZoneId.of("America/Bogota");

    private final UsuarioRepository usuarioRepository;
    private final RolRepository rolRepository;
    private final PermisoRepository permisoRepository;
    private final DireccionRepository direccionRepository;
    private final CategoriaRepository categoriaRepository;
    private final MarcaRepository marcaRepository;
    private final ProductoRepository productoRepository;
    private final UnidadProductoRepository unidadRepository;
    private final ReservaRepository reservaRepository;
    private final PagoSimuladoRepository pagoRepository;
    private final DevolucionRepository devolucionRepository;
    private final CalificacionRepository calificacionRepository;
    private final ResenaRepository resenaRepository;
    private final ModeracionResenaRepository moderacionRepository;
    private final ListaDeseosItemRepository listaDeseosRepository;
    private final NotificacionService notificaciones;
    private final AuditoriaService auditoria;
    private final PasswordEncoder passwordEncoder;

    @Value("${hawkify.demo.seed:false}")
    private boolean habilitado;

    private final Random random = new Random(20260923L);
    private LocalDate hoy;
    private String hashDemo;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!habilitado || productoRepository.count() > 0) {
            return;
        }
        hoy = LocalDate.now(ZONA);
        hashDemo = passwordEncoder.encode(PASSWORD_DEMO);

        // ---------------- cuentas ----------------
        Usuario superadmin = usuarioRepository.findAll().stream().filter(Usuario::esSuperadmin).findFirst()
                .orElseGet(() -> usuario("Mariana Ortiz Restrepo", "superadmin@hawkify.co", RolNombre.SUPERADMIN,
                        "3004567812", "52874120"));
        Usuario julian = usuario("Julián Castaño Mejía", "admin@hawkify.co", RolNombre.ADMINISTRADOR,
                "3015552040", "1045789321");
        Usuario paola = usuario("Paola Herrera Díaz", "bodega@hawkify.co", RolNombre.ADMINISTRADOR,
                "3107781122", "1143220981");
        Usuario laura = usuario("Laura Gómez Pineda", "laura.gomez@correo.co", RolNombre.USUARIO_FINAL,
                "3012345678", "1047456123");
        Usuario andres = usuario("Andrés Ríos Villa", "andres.rios@correo.co", RolNombre.USUARIO_FINAL,
                "3159876543", "72345678");
        Usuario camilo = usuario("Camilo Pardo Suárez", "camilo.pardo@correo.co", RolNombre.USUARIO_FINAL,
                "3205554433", "1129876540");
        Usuario daniela = usuario("Daniela Muñoz Arango", "daniela.munoz@correo.co", RolNombre.USUARIO_FINAL,
                "3046612233", "1001234567");

        permisos(julian, EnumSet.allOf(CodigoPermiso.class));
        permisos(paola, EnumSet.of(CodigoPermiso.CATALOGO_GESTIONAR, CodigoPermiso.RESERVAS_GESTIONAR,
                CodigoPermiso.RESENAS_MODERAR));

        direccion(laura, "Casa", "Cra 53 #76-115, Apto 402", "Barranquilla", "Atlántico", true);
        direccion(laura, "Obra Alto Prado", "Calle 79 #57-30", "Barranquilla", "Atlántico", false);
        direccion(camilo, "Taller", "Calle 30 #8-45, Bodega 3", "Soledad", "Atlántico", true);

        // ---------------- catalogo ----------------
        Categoria perforacion = categoria("Perforación");
        Categoria corte = categoria("Corte");
        Categoria carpinteria = categoria("Carpintería");
        Categoria aire = categoria("Aire y neumática");
        Categoria kits = categoria("Kits y combos");
        Categoria jardin = categoria("Jardín y exteriores");

        Producto taladro = producto(julian, julian, perforacion, "Bosch", "Taladro Percutor Bosch GSB 18V-50",
                25000, EstadoFisico.NUEVO, 3, List.of("assets/products/taladro-bosch.jpg"),
                "Taladro percutor inalámbrico de 18 V con motor sin escobillas. Perfora concreto, ladrillo, madera y "
                        + "metal. Incluye dos baterías de 4.0 Ah, cargador rápido y maletín. Ideal para instalar "
                        + "repisas, cortineros y soportes de TV sin cables de por medio.",
                "Usar gafas de protección. No perforar muros con tubería o cableado sin detector. Devolver con las "
                        + "baterías cargadas al menos al 50 %.",
                Map.of("Voltaje", "18 V", "Baterías", "2 x Ion-Litio 4.0 Ah", "Mandril", "13 mm metálico",
                        "Torque máximo", "50 Nm", "Peso", "1.8 kg", "Incluye", "Maletín L-Case, cargador GAL 18V-40"));

        Producto sierra = producto(julian, julian, corte, "DeWalt", "Sierra Circular DeWalt DWE575 7-1/4\"",
                35000, EstadoFisico.NUEVO, 2, List.of("assets/products/sierra-dewalt.jpg"),
                "Sierra circular de 1600 W para cortes rectos y en bisel sobre madera, MDF y aglomerado. Base de "
                        + "aluminio, freno eléctrico y ventana de línea de corte. Se entrega con disco de 24 dientes.",
                "Solo para madera y derivados. Verificar que la guarda inferior retorne libremente antes de cada "
                        + "corte. No se permite usar discos abrasivos.",
                Map.of("Potencia", "1600 W", "Disco", "7-1/4\" (184 mm)", "Profundidad a 90°", "65 mm",
                        "Bisel", "0 a 57°", "Peso", "3.9 kg"));

        Producto pulidora = producto(julian, julian, corte, "Makita", "Pulidora Angular Makita GA4530 4-1/2\"",
                18000, EstadoFisico.USADO, 3, List.of("assets/products/pulidora-makita.jpg"),
                "Pulidora angular compacta de 720 W para corte y desbaste de metal, cerámica y piedra. Cuerpo "
                        + "delgado para mejor agarre y protección contra polvo en el motor.",
                "Obligatorio el uso de guarda, careta y guantes. El disco no esta incluido en la tarifa; puedes "
                        + "comprar uno en la entrega.",
                Map.of("Potencia", "720 W", "Disco", "4-1/2\" (115 mm)", "Velocidad", "11.000 RPM",
                        "Rosca", "M14", "Peso", "1.4 kg"));

        Producto rotomartillo = producto(julian, julian, perforacion, "Hilti", "Rotomartillo Hilti TE 6-A22",
                42000, EstadoFisico.NUEVO, 2, List.of("assets/products/rotomartillo-hilti.jpg"),
                "Rotomartillo SDS-Plus a batería con sistema antivibración AVR. Perfora concreto armado hasta 22 mm "
                        + "y cincela en modo percusión. Pensado para anclajes, pases de tubería y demolición ligera.",
                "Usar solo brocas SDS-Plus. No usar como palanca. Reportar cualquier sonido anormal en el embrague.",
                Map.of("Sistema", "SDS-Plus", "Voltaje", "22 V", "Energía de impacto", "2.1 J",
                        "Perforación concreto", "4 a 22 mm", "Funciones", "Rotación / percusión / cincelado",
                        "Peso", "3.2 kg"));

        Producto compresor = producto(julian, julian, aire, "Stanley", "Compresor de Aire Stanley 24 L 2 HP",
                30000, EstadoFisico.EN_MANTENIMIENTO, 1, List.of("assets/products/compresor-stanley.jpg"),
                "Compresor de tanque de 24 litros, lubricado con aceite, para pintura con pistola, inflado, "
                        + "limpieza y herramienta neumática ligera. Incluye manguera espiral de 7.5 m y kit de 5 accesorios.",
                "Purgar el tanque al terminar cada jornada. No mover con presión en el tanque.",
                Map.of("Tanque", "24 L", "Potencia", "2 HP", "Presión máxima", "115 PSI",
                        "Caudal", "6.3 CFM a 40 PSI", "Peso", "22 kg"));

        Producto lijadora = producto(andres, julian, carpinteria, "Black+Decker",
                "Lijadora Orbital Black+Decker BDERO100", 15000, EstadoFisico.USADO, 2,
                List.of("assets/products/lijadora-blackdecker.jpg"),
                "Lijadora roto-orbital de 5\" para acabados finos en madera antes de pintar o barnizar. Sistema "
                        + "de agarre de lija por velcro y bolsa recolectora de polvo.",
                "Se entregan 5 lijas grano 120. Devolver sin la lija puesta y con la bolsa de polvo vacia.",
                Map.of("Potencia", "240 W", "Base", "5\" (125 mm)", "Orbitas", "12.000 OPM",
                        "Peso", "1.2 kg"));

        Producto impacto = producto(andres, julian, perforacion, "Makita", "Atornillador de Impacto Makita DTD153",
                28000, EstadoFisico.NUEVO, 2, List.of("assets/products/taladro-impacto-makita.jpg"),
                "Atornillador de impacto de 18 V sin escobillas con 170 Nm de torque. Para tornillería pesada, "
                        + "estructura metalica liviana y montaje de drywall. Luz LED y encastre hexagonal de 1/4\".",
                "Usar puntas de impacto (no puntas comunes). Batería y cargador incluidos.",
                Map.of("Voltaje", "18 V", "Torque máximo", "170 Nm", "Encastre", "Hexagonal 1/4\"",
                        "Impactos", "0 - 3.600 IPM", "Peso", "1.5 kg"));

        Producto caladora = producto(paola, paola, corte, "Bosch", "Sierra Caladora Bosch GST 700",
                22000, EstadoFisico.NUEVO, 2, List.of("assets/products/sierra-jig-bosch.jpg"),
                "Sierra caladora de 500 W para cortes curvos y calados en madera, laminados, PVC y lámina metalica "
                        + "delgada. Soplador de viruta y velocidad variable para mayor precisión.",
                "Incluye 3 hojas para madera. Las hojas para metal se venden aparte.",
                Map.of("Potencia", "500 W", "Carrera", "18 mm", "Profundidad en madera", "70 mm",
                        "Velocidad", "500 - 3.100 cpm", "Peso", "1.9 kg"));

        Producto demoledor = producto(paola, paola, perforacion, "Hilti", "Martillo Demoledor Hilti TE 500-AVR",
                48000, EstadoFisico.USADO, 1, List.of("assets/products/martillo-hilti.jpg"),
                "Martillo cincelador SDS-Max de 1100 W para demolición de muros, pisos y levantamiento de baldosa. "
                        + "Sistema AVR que reduce la vibración en la jornada.",
                "Se entrega con un cincel punta y uno plano. El usuario debe tener experiencia previa con equipos "
                        + "de demolición.",
                Map.of("Sistema", "SDS-Max", "Potencia", "1100 W", "Energía de impacto", "8.5 J",
                        "Golpes por minuto", "2.760", "Peso", "5.9 kg"));

        Producto kit = producto(julian, julian, kits, "Bosch", "Kit Bosch Professional 18V: 4 herramientas + bolso",
                65000, EstadoFisico.NUEVO, 1, List.of("assets/packs/bosch-pack-hero.jpg",
                        "assets/packs/bosch-pack-thumb1.jpg", "assets/packs/bosch-pack-thumb2.jpg",
                        "assets/packs/bosch-pack-thumb3.jpg", "assets/packs/bosch-pack-thumb4.jpg"),
                "Todo lo necesario para una remodelación con una sola plataforma de batería: taladro percutor, "
                        + "sierra circular, sierra sable y linterna LED. Cuatro baterías 4.0 Ah, cargador rápido y "
                        + "bolso de lona reforzada.",
                "El kit se revisa completo en la entrega y en la devolución; cada pieza faltante se cobra por "
                        + "separado según la política de garantía.",
                Map.of("Plataforma", "Bosch Professional 18 V", "Incluye", "Taladro, sierra circular, sierra sable, linterna",
                        "Baterías", "4 x 4.0 Ah", "Autonomía estimada", "8 a 10 h de uso mixto", "Peso total", "9.2 kg"));

        Producto hidrolavadora = new Producto();
        hidrolavadora.setPropietario(andres);
        hidrolavadora.setCategoria(jardin);
        hidrolavadora.setMarca(marca("Karcher"));
        hidrolavadora.setNombre("Hidrolavadora Karcher K3 Power Control");
        hidrolavadora.setDescripcion("Hidrolavadora de 1600 W para fachadas, carros, patios y muebles de exterior. "
                + "Presión ajustable desde la pistola y manguera de 6 m.");
        hidrolavadora.setCondicionesUso("Conectar solo a agua limpia. No usar con agua caliente.");
        hidrolavadora.setTarifaDia(BigDecimal.valueOf(32000));
        hidrolavadora.setEstadoFisico(EstadoFisico.USADO);
        hidrolavadora.setEstadoPublicacion(EstadoPublicacion.PENDIENTE_APROBACION);
        hidrolavadora.getEspecificaciones().add(new ProductoEspecificacion(hidrolavadora, "Presión máxima", "120 bar"));
        hidrolavadora.getEspecificaciones().add(new ProductoEspecificacion(hidrolavadora, "Caudal", "380 l/h"));
        productoRepository.saveAndFlush(hidrolavadora);
        unidades(hidrolavadora, 1);

        // ---------------- historial de alquileres (ultimos 5 meses) ----------------
        List<Usuario> clientes = List.of(laura, camilo, daniela, andres);
        List<Producto> alquilables = List.of(taladro, sierra, pulidora, rotomartillo, lijadora, impacto, caladora,
                demoledor, kit, taladro, rotomartillo, sierra);
        String[] resenasPositivas = {
                "Llegó a tiempo y con la batería full. Hice todos los anclajes del apartamento en una tarde.",
                "Muy buen estado, se nota que le hacen mantenimiento. Repetiría sin pensarlo.",
                "La entrega fue puntual y me explicaron cómo usarla. Excelente para el precio por día.",
                "Justo lo que necesitaba para una remodelación corta. Cero complicaciones en la devolución.",
                "Potente y liviana. Me ahorré comprar una para un solo trabajo.",
        };
        String[] resenasRegulares = {
                "Cumplió, aunque el cable estaba algo gastado. Funciono bien todo el alquiler.",
                "Buena herramienta, pero la entrega se demoró un par de horas.",
        };

        for (int i = 150; i >= 20; i -= 4 + random.nextInt(5)) {
            Producto p = alquilables.get(random.nextInt(alquilables.size()));
            Usuario cliente = clientes.get(random.nextInt(clientes.size()));
            if (cliente.getId().equals(p.getPropietario().getId())) {
                continue;
            }
            int dias = 1 + random.nextInt(4);
            boolean cancelada = random.nextInt(9) == 0;
            Reserva r = reserva(cliente, p, hoy.minusDays(i), hoy.minusDays(i - dias + 1),
                    cancelada ? EstadoReserva.CANCELADA : EstadoReserva.FINALIZADA, random.nextInt(4) == 0);
            if (r == null || cancelada) {
                continue;
            }
            devolucion(r, gestor(p), EstadoEquipo.SIN_NOVEDAD, null);
            if (random.nextInt(10) < 7) {
                int estrellas = random.nextInt(10) < 8 ? 4 + random.nextInt(2) : 3;
                String texto = random.nextInt(3) == 0 ? null
                        : estrellas >= 4 ? resenasPositivas[random.nextInt(resenasPositivas.length)]
                        : resenasRegulares[random.nextInt(resenasRegulares.length)];
                calificar(r, estrellas, texto);
            }
        }

        // ---------------- situaciones actuales ----------------
        Reserva rLaura1 = reserva(laura, taladro, hoy.minusDays(14), hoy.minusDays(12), EstadoReserva.FINALIZADA, false);
        devolucion(rLaura1, julian, EstadoEquipo.SIN_NOVEDAD, null);
        calificar(rLaura1, 5, "Instalé cocina completa con este taladro. Las dos baterías alcanzaron para todo el día.");

        Reserva rLaura2 = reserva(laura, demoledor, hoy.minusDays(10), hoy.minusDays(9), EstadoReserva.FINALIZADA, false);
        devolucion(rLaura2, paola, EstadoEquipo.SIN_NOVEDAD, null);
        Resena spam = calificar(rLaura2, 2, "Muy pesado. Mejor vengan a mi local en Soledad que les alquilo uno más "
                + "barato, escríbanme al 300 000 0000.");
        spam.setEstadoModeracion(EstadoModeracion.OCULTA);
        ModeracionResena mod = new ModeracionResena();
        mod.setResena(spam);
        mod.setModerador(superadmin);
        mod.setAccion(AccionModeracion.OCULTAR);
        mod.setMotivo("Contenido publicitario no autorizado (datos de contacto de un tercero)");
        mod.setFecha(OffsetDateTime.now().minusDays(8));
        moderacionRepository.save(mod);
        auditoria.registrar(superadmin, "resena_ocultar", "resena", spam.getId(), Map.of("estado", "visible"),
                Map.of("estado", "oculta", "motivo", mod.getMotivo()));

        Reserva rCamiloCaladora = reserva(camilo, caladora, hoy.minusDays(6), hoy.minusDays(5),
                EstadoReserva.CON_INCIDENCIA, false);
        devolucion(rCamiloCaladora, paola, EstadoEquipo.CON_DANO,
                "La guarda llegó floja y la base presenta un golpe en la esquina frontal. Se envia a revisión.");
        rCamiloCaladora.getUnidad().setEstado(EstadoUnidad.EN_MANTENIMIENTO);

        Reserva rLijadora = reserva(laura, lijadora, hoy.minusDays(5), hoy.minusDays(4), EstadoReserva.FINALIZADA, false);
        devolucion(rLijadora, julian, EstadoEquipo.SIN_NOVEDAD, null);

        reserva(laura, impacto, hoy.minusDays(1), hoy.plusDays(1), EstadoReserva.EN_CURSO, true);
        reserva(camilo, taladro, hoy.minusDays(2), hoy, EstadoReserva.EN_CURSO, false);
        reserva(laura, rotomartillo, hoy.plusDays(4), hoy.plusDays(6), EstadoReserva.CONFIRMADA, true);
        reserva(camilo, sierra, hoy.plusDays(2), hoy.plusDays(4), EstadoReserva.CONFIRMADA, false);
        reserva(daniela, kit, hoy, hoy.plusDays(2), EstadoReserva.CONFIRMADA, true);
        reserva(andres, pulidora, hoy.plusDays(8), hoy.plusDays(9), EstadoReserva.CONFIRMADA, false);
        reserva(daniela, taladro, hoy.plusDays(1), hoy.plusDays(3), EstadoReserva.CONFIRMADA, false);

        // ---------------- wishlist y notificaciones ----------------
        listaDeseosRepository.save(new ListaDeseosItem(laura, compresor));
        listaDeseosRepository.save(new ListaDeseosItem(laura, kit));
        listaDeseosRepository.save(new ListaDeseosItem(laura, caladora));
        listaDeseosRepository.save(new ListaDeseosItem(camilo, demoledor));
        listaDeseosRepository.save(new ListaDeseosItem(daniela, rotomartillo));

        notificaciones.notificar(laura, TipoNotificacion.CAMBIO_TARIFA, "producto", kit.getId(),
                "La tarifa de \"" + kit.getNombre() + "\" bajo de $72.000 a $65.000 por día.");
        notificaciones.notificar(andres, TipoNotificacion.PUBLICACION, "producto", lijadora.getId(),
                "Tu herramienta \"" + lijadora.getNombre() + "\" fue aprobada y ya aparece en el catalogo.");
        notificaciones.notificar(andres, TipoNotificacion.PUBLICACION, "producto", hidrolavadora.getId(),
                "Recibimos \"" + hidrolavadora.getNombre() + "\". Un administrador la revisara en menos de 24 horas.");

        auditoria.registrar(julian, "producto_aprobado", "producto", lijadora.getId(),
                Map.of("estadoPublicacion", "pendiente_aprobacion"), Map.of("estadoPublicacion", "aprobado"));
        auditoria.registrar(julian, "producto_aprobado", "producto", impacto.getId(),
                Map.of("estadoPublicacion", "pendiente_aprobacion"), Map.of("estadoPublicacion", "aprobado"));
        auditoria.registrar(superadmin, "usuario_creado", "usuario", paola.getId(), null,
                Map.of("correo", paola.getCorreo(), "rol", "administrador"));
        auditoria.registrar(paola, "devolucion_registrada", "reserva", rCamiloCaladora.getId(),
                Map.of("estado", "en_curso"), Map.of("estado", "con_incidencia", "estadoEquipo", "con_dano"));

        log.info("""

                ==============================================================
                 Datos de demostracion cargados. Contrasena de todas: {}
                   superadmin@hawkify.co   (superadmin)
                   admin@hawkify.co        (administrador, todos los permisos)
                   bodega@hawkify.co       (administrador, alcance limitado)
                   laura.gomez@correo.co   (usuario final)
                   andres.rios@correo.co   (usuario final que publica herramientas)
                ==============================================================""", PASSWORD_DEMO);
    }

    // ------------------------------------------------------------------

    private Usuario usuario(String nombre, String correo, String rol, String telefono, String documento) {
        Usuario u = Usuario.builder()
                .nombreCompleto(nombre)
                .correo(correo)
                .passwordHash(hashDemo)
                .rol(rolRepository.findByNombre(rol).orElseThrow())
                .telefono(telefono)
                .documentoIdentidad(documento)
                .estado(EstadoUsuario.ACTIVO)
                .correoVerificado(true)
                .aceptoPoliticaEn(OffsetDateTime.now().minusMonths(6))
                .build();
        return usuarioRepository.saveAndFlush(u);
    }

    private void permisos(Usuario admin, Set<CodigoPermiso> codigos) {
        permisoRepository.findByCodigoIn(codigos.stream().map(CodigoPermiso::valor).toList())
                .forEach(p -> permisoRepository.asignar(admin.getId(), p.getId()));
    }

    private void direccion(Usuario u, String etiqueta, String linea1, String ciudad, String depto, boolean pred) {
        Direccion d = new Direccion();
        d.setUsuario(u);
        d.setEtiqueta(etiqueta);
        d.setLinea1(linea1);
        d.setCiudad(ciudad);
        d.setDepartamento(depto);
        d.setPredeterminada(pred);
        direccionRepository.save(d);
    }

    private Categoria categoria(String nombre) {
        return categoriaRepository.findAll().stream()
                .filter(c -> c.getNombre().equalsIgnoreCase(nombre))
                .findFirst()
                .orElseGet(() -> {
                    Categoria c = new Categoria();
                    c.setNombre(nombre);
                    return categoriaRepository.save(c);
                });
    }

    private Marca marca(String nombre) {
        return marcaRepository.findByNombreIgnoreCase(nombre).orElseGet(() -> marcaRepository.save(new Marca(nombre)));
    }

    private Producto producto(Usuario propietario, Usuario revisor, Categoria categoria, String marca, String nombre,
                              int tarifa, EstadoFisico fisico, int unidades, List<String> imagenes,
                              String descripcion, String condiciones, Map<String, String> specs) {
        Producto p = new Producto();
        p.setPropietario(propietario);
        p.setAdministradorRevisor(revisor);
        p.setCategoria(categoria);
        p.setMarca(marca(marca));
        p.setNombre(nombre);
        p.setDescripcion(descripcion);
        p.setCondicionesUso(condiciones);
        p.setTarifaDia(BigDecimal.valueOf(tarifa));
        p.setEstadoFisico(fisico);
        p.setEstadoPublicacion(EstadoPublicacion.APROBADO);
        for (int i = 0; i < imagenes.size(); i++) {
            p.getImagenes().add(new ProductoImagen(p, imagenes.get(i), (short) i, i == 0));
        }
        specs.forEach((k, v) -> p.getEspecificaciones().add(new ProductoEspecificacion(p, k, v)));
        productoRepository.saveAndFlush(p);
        unidades(p, unidades);
        return p;
    }

    private void unidades(Producto p, int n) {
        for (int i = 1; i <= n; i++) {
            UnidadProducto u = new UnidadProducto(p, p.getCodigo() + "-" + String.format("%02d", i));
            p.getUnidades().add(unidadRepository.save(u));
        }
    }

    private Usuario gestor(Producto p) {
        return p.getAdministradorRevisor() != null ? p.getAdministradorRevisor() : p.getPropietario();
    }

    private Reserva reserva(Usuario cliente, Producto p, LocalDate inicio, LocalDate fin, EstadoReserva estado,
                            boolean domicilio) {
        List<UnidadProducto> libres = unidadRepository.libres(p.getId(), inicio, fin);
        if (libres.isEmpty()) {
            return null;
        }
        Reserva r = new Reserva();
        r.setUsuario(cliente);
        r.setUnidad(libres.getFirst());
        r.setFechaInicio(inicio);
        r.setFechaFin(fin);
        r.setTarifaDiaSnapshot(p.getTarifaDia());
        r.setSubtotal(p.getTarifaDia().multiply(BigDecimal.valueOf(r.getDias())));
        r.setModalidadEntrega(domicilio ? ModalidadEntrega.DOMICILIO : ModalidadEntrega.RECOGIDA);
        r.setCostoEnvio(domicilio ? BigDecimal.valueOf(15000) : BigDecimal.ZERO);
        if (domicilio) {
            r.setDireccionEntrega(direccionRepository.findByUsuarioIdOrderByPredeterminadaDescEtiquetaAsc(cliente.getId())
                    .stream().findFirst().map(Direccion::comoTexto).orElse("Cra 46 #85-20, Barranquilla, Atlántico"));
        }
        r.setTotal(r.getSubtotal().add(r.getCostoEnvio()));
        r.setEstado(estado);
        r.setCreadoEn(inicio.minusDays(2 + random.nextInt(4)).atTime(9 + random.nextInt(9), random.nextInt(60))
                .atZone(ZONA).toOffsetDateTime());
        if (estado == EstadoReserva.CANCELADA) {
            r.setMotivoCancelacion("Cambio de planes en la obra");
            r.setCanceladoEn(r.getCreadoEn().plusHours(5));
        }
        reservaRepository.saveAndFlush(r);

        PagoSimulado pago = new PagoSimulado();
        pago.setReserva(r);
        pago.setMonto(r.getTotal());
        MetodoPago metodo = MetodoPago.values()[random.nextInt(MetodoPago.values().length)];
        pago.setMetodo(metodo);
        pago.setEstado(estado == EstadoReserva.CANCELADA ? EstadoPago.REEMBOLSADO : EstadoPago.APROBADO);
        pago.setReferencia("HKP-" + r.getCreadoEn().toLocalDate().toString().replace("-", "") + "-"
                + r.getId().toString().substring(0, 6).toUpperCase());
        pago.setDetalle(switch (metodo) {
            case TARJETA_SIMULADA -> "Visa terminada en " + (4000 + random.nextInt(5999));
            case PSE_SIMULADO -> "PSE - Bancolombia";
            case NEQUI_SIMULADO -> "Nequi terminado en " + (1000 + random.nextInt(8999));
        });
        pago.setFechaPago(r.getCreadoEn().plusMinutes(3));
        pagoRepository.save(pago);

        if (estado == EstadoReserva.CONFIRMADA || estado == EstadoReserva.EN_CURSO) {
            notificaciones.notificar(cliente, TipoNotificacion.RESERVA, "reserva", r.getId(),
                    "Reserva " + r.getCodigo() + " confirmada: " + p.getNombre() + " del "
                            + Formatos.fecha(inicio) + " al " + Formatos.fecha(fin) + ".");
        }
        return r;
    }

    private void devolucion(Reserva r, Usuario registradoPor, EstadoEquipo estado, String observaciones) {
        if (r == null) {
            return;
        }
        Devolucion d = new Devolucion();
        d.setReserva(r);
        d.setRegistradoPor(registradoPor);
        d.setEstadoEquipo(estado);
        d.setObservaciones(observaciones);
        d.setFechaRealDevolucion(r.getFechaFin().atTime(17, 30).atZone(ZONA).toOffsetDateTime());
        devolucionRepository.save(d);
    }

    private Resena calificar(Reserva r, int estrellas, String texto) {
        if (r == null) {
            return null;
        }
        Calificacion c = new Calificacion();
        c.setReserva(r);
        c.setUsuario(r.getUsuario());
        c.setProducto(r.getProducto());
        c.setEstrellas((short) estrellas);
        c.setCreadoEn(r.getFechaFin().plusDays(1).atTime(20, 15).atZone(ZONA).toOffsetDateTime());
        calificacionRepository.saveAndFlush(c);
        if (texto == null) {
            return null;
        }
        Resena res = new Resena();
        res.setCalificacion(c);
        res.setTexto(texto);
        res.setCreadoEn(c.getCreadoEn());
        res.setActualizadoEn(c.getCreadoEn());
        return resenaRepository.save(res);
    }
}

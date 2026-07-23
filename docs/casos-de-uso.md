
# Casos de Uso — Brixoo

Documento de especificación de casos de uso del sistema Brixoo (plataforma que conecta clientes con contratistas verificados por reconocimiento facial).

---

## CU-01 — Registrarse

| Campo | Detalle |
|---|---|
| **Rol** | Visitante (no autenticado) |
| **Descripción** | Permite que una persona cree una cuenta en Brixoo con información básica. Toda cuenta nueva se crea con rol **CLIENTE**. |
| **Precondición** | El visitante no ha iniciado sesión y está en la pantalla de login/registro. |

**Secuencia normal**

1. El visitante hace clic en "Registrarse".
2. El sistema muestra el formulario (nombre, correo, teléfono, contraseña, dirección, ciudad).
3. El visitante completa los campos y hace clic en "Registrar".
4. El sistema valida que todos los campos obligatorios estén completos y que el correo no esté registrado.
5. El sistema cifra la contraseña, guarda el usuario con rol CLIENTE y muestra "Registro exitoso".
6. El sistema redirige al login.

**Postcondición**: Se crea un nuevo usuario con rol CLIENTE. El usuario puede iniciar sesión.

**Excepciones**

1. Si un campo obligatorio está vacío, el sistema muestra el error y conserva los datos digitados.
2. Si el correo ya está registrado, el sistema muestra "El correo ya está registrado".
3. Si la contraseña tiene menos de 6 caracteres, el sistema pide una contraseña más segura.

---

## CU-02 — Iniciar Sesión

| Campo | Detalle |
|---|---|
| **Rol** | Usuario registrado |
| **Descripción** | Autentica a un usuario existente y lo dirige a su panel según su rol. Incluye bloqueo de cuenta tras intentos fallidos repetidos. |
| **Precondición** | El usuario tiene una cuenta activa y no bloqueada. |

**Secuencia normal**

1. El usuario ingresa correo y contraseña en la pantalla de login.
2. El sistema valida las credenciales contra la base de datos.
3. El sistema crea la sesión autenticada con el rol correspondiente.
4. El sistema redirige al panel principal (dashboard).

**Postcondición**: Sesión iniciada. El usuario es redirigido a su panel con los permisos de su rol (CLIENTE, CONTRATISTA o ADMIN).

**Excepciones**

1. Si las credenciales son inválidas, el sistema muestra un mensaje de error genérico y permanece en login.
2. Si la cuenta está bloqueada por intentos fallidos previos, el sistema muestra el motivo y el tiempo de espera restante.

---

## CU-03 — Solicitar Verificación como Contratista

| Campo | Detalle |
|---|---|
| **Rol** | CLIENTE |
| **Descripción** | Un cliente inicia el trámite para convertirse en contratista verificado, subiendo cédula, selfie y documentos de soporte. |
| **Precondición** | El cliente no tiene una solicitud pendiente ni ya es contratista aprobado. |

**Secuencia normal**

1. El cliente hace clic en "Quiero ser contratista" y el sistema valida su elegibilidad.
2. El cliente ingresa su cédula y elige su área de especialidad.
3. El cliente sube foto de cédula, selfie, certificado de especialidad, recibo de servicios públicos y antecedentes policiales.
4. El sistema confirma que todos los archivos obligatorios están presentes.
5. El sistema envía cédula y selfie al servicio de reconocimiento facial y obtiene un puntaje de coincidencia.
6. El sistema guarda los documentos de soporte y crea la solicitud con el resultado de la verificación.
7. El sistema notifica al administrador y muestra el resultado al cliente.

**Postcondición**: Se crea una solicitud de contratista (verificada o pendiente de revisión manual). El administrador queda notificado.

**Excepciones**

1. Si el cliente ya tiene una solicitud pendiente o ya es contratista, el sistema le impide entrar al formulario.
2. Si falta un archivo o campo obligatorio, el sistema muestra el error puntual y conserva cédula y área ya digitadas.
3. Si el puntaje de reconocimiento facial es bajo, la solicitud se crea igual, marcada para revisión manual.
4. Si falla el procesamiento de archivos o el microservicio, el sistema informa el error y conserva el formulario.

---

## CU-04 — Revisar Solicitud de Contratista

| Campo | Detalle |
|---|---|
| **Rol** | ADMINISTRADOR |
| **Descripción** | El administrador audita manualmente los documentos y el resultado del reconocimiento facial de cada solicitud, y decide si el usuario se convierte en contratista. |
| **Precondición** | Existe al menos una solicitud de contratista pendiente de revisión. |

**Secuencia normal**

1. El administrador abre el listado de solicitudes pendientes.
2. Revisa cédula, selfie y documentos de soporte de una solicitud.
3. El administrador aprueba la solicitud.
4. El sistema cambia el rol del usuario a CONTRATISTA y marca su cuenta como aprobada.
5. El sistema notifica al usuario que ya puede operar como contratista.

**Postcondición**: La solicitud queda APROBADA (el usuario pasa a rol CONTRATISTA) o RECHAZADA (con motivo visible para el solicitante).

**Excepciones**

1. Si el administrador rechaza en su lugar (con un motivo opcional), la solicitud queda RECHAZADA y el usuario es notificado con el motivo.
2. Si el documento o foto solicitada no existe en disco, el sistema responde "no encontrado".

---

## CU-05 — Publicar un Servicio

| Campo | Detalle |
|---|---|
| **Rol** | CLIENTE |
| **Descripción** | Un cliente describe una necesidad de construcción o reparación para que los contratistas disponibles la coticen. |
| **Precondición** | El usuario autenticado tiene rol CLIENTE. |

**Secuencia normal**

1. El cliente abre el formulario de nuevo servicio.
2. Completa título, tipo, ubicación, presupuesto estimado y descripción.
3. El sistema valida los campos obligatorios.
4. El sistema crea el servicio en estado ABIERTO, asociado al cliente.
5. El sistema redirige al listado de servicios con mensaje de éxito.

**Postcondición**: Nuevo servicio en estado ABIERTO, visible en el listado y disponible para cotización.

**Excepciones**

1. Si el usuario no tiene rol CLIENTE, el sistema bloquea el acceso al formulario.
2. Si hay errores de validación, el sistema re-muestra el formulario con los campos inválidos señalados.

---

## CU-06 — Cotizar un Servicio

| Campo | Detalle |
|---|---|
| **Rol** | CONTRATISTA |
| **Descripción** | Un contratista revisa un servicio abierto y envía una propuesta de monto y mensaje al cliente. |
| **Precondición** | El servicio existe, el usuario tiene el rol CONTRATISTA y no tiene ya una cotización pendiente sobre ese servicio. |

**Secuencia normal**

1. El contratista abre el detalle de un servicio abierto.
2. Ingresa el monto propuesto y un mensaje opcional.
3. El sistema crea una cotización asociada al servicio y al contratista.
4. El sistema notifica al cliente propietario del servicio.
5. El sistema redirige de vuelta al detalle del servicio con mensaje de éxito.

**Postcondición**: Nueva cotización en estado PENDIENTE, visible para el cliente propietario del servicio.

**Excepciones**

1. El contratista ya tiene una cotización pendiente sobre ese servicio. El sistema la rechaza para evitar duplicados.
2. El servicio ya no acepta cotizaciones. El sistema muestra el error de negocio.

---

## CU-07 — Aceptar Cotización

| Campo | Detalle |
|---|---|
| **Rol** | CLIENTE |
| **Descripción** | El cliente compara las cotizaciones recibidas para su servicio y acepta una, lo que asigna automáticamente al contratista elegido. |
| **Precondición** | El servicio pertenece al cliente autenticado y tiene al menos una cotización pendiente. |

**Secuencia normal**

1. El cliente revisa las cotizaciones recibidas en el detalle del servicio.
2. El cliente acepta una de ellas.
3. El sistema marca la cotización como ACEPTADA y asigna al contratista del servicio.
4. El sistema notifica al contratista aceptado.

**Postcondición**: El servicio queda con un contratista asignado; el resto de cotizaciones pendientes del mismo servicio se cierran.

**Excepciones**

1. El cliente rechaza en su lugar. La cotización pasa a RECHAZADA, se notifica al contratista y el servicio sigue abierto a nuevas propuestas.
2. La cotización no pertenece a un servicio del cliente autenticado, o ya fue procesada. El sistema muestra un error de negocio.

---

## CU-08 — Iniciar y Finalizar un Servicio

| Campo | Detalle |
|---|---|
| **Rol** | CONTRATISTA asignado / ADMINISTRADOR |
| **Descripción** | El contratista asignado marca el arranque de la obra y, al terminar, su cierre. Un administrador puede realizar ambas acciones en su representación. |
| **Precondición** | El servicio tiene un contratista asignado (para iniciar) y está EN_PROCESO (para finalizar). |

**Secuencia normal**

1. El contratista asignado abre el detalle del servicio y confirma el inicio de la obra.
2. El sistema valida el permiso y cambia el estado a EN_PROCESO.
3. Al terminar el trabajo, el contratista confirma la finalización.
4. El sistema cambia el estado a CERRADO.

**Postcondición**: El estado del servicio refleja su ejecución real: EN_PROCESO al iniciar, CERRADO al finalizar.

**Excepciones**

1. Un usuario sin rol CONTRATISTA ni ADMIN intenta iniciar o finalizar. El sistema bloquea la acción.
2. Se intenta finalizar un servicio que no está EN_PROCESO, o iniciar uno sin contratista asignado. El sistema muestra el error de negocio.

---

## CU-09 — Gestión de Usuarios del Sistema

| Campo | Detalle |
|---|---|
| **Rol** | ADMINISTRADOR |
| **Descripción** | El administrador crea, edita y elimina cuentas de usuario desde el panel de gestión, con control de integridad para no romper datos relacionados. |
| **Precondición** | El administrador tiene sesión activa con el rol ADMIN. |

**Secuencia normal**

1. El administrador abre el listado de usuarios, con filtros por nombre, correo o rol.
2. Crea un usuario nuevo indicando nombre, correo, contraseña y rol.
3. Edita uno existente cambiando sus datos y su rol.
4. Confirma la eliminación de una cuenta.
5. El sistema aplica el cambio y redirige al listado con un mensaje de confirmación.

**Postcondición**: El usuario queda creado, actualizado o eliminado; el listado se refresca de inmediato.

**Excepciones**

1. El correo ya está en uso al crear o editar. El sistema muestra el error, sin aplicar el cambio.
2. Se intenta eliminar un usuario con servicios, cotizaciones, solicitudes o notificaciones asociadas. El sistema rechaza el borrado por integridad referencial y explica el motivo.
3. El id de usuario no existe. El sistema muestra "Usuario no encontrado".

---

## CU-10 — Editar o Eliminar Perfil Propio

| Campo | Detalle |
|---|---|
| **Rol** | Usuario (cualquier rol) |
| **Descripción** | Cualquier usuario puede actualizar sus propios datos de contacto y contraseña, o eliminar definitivamente su cuenta confirmando su contraseña actual. |
| **Precondición** | El usuario tiene una sesión activa. |

**Secuencia normal**

1. El usuario abre el perfil y revisa sus datos actuales.
2. Modifica nombre, correo, teléfono, dirección, ciudad o contraseña.
3. El sistema valida que el nuevo correo, si cambió, no esté en uso de otra cuenta.
4. El sistema aplica los cambios; si el correo cambió, refresca la sesión para mantenerla activa.

**Postcondición**: El perfil queda actualizado, o la cuenta y la sesión quedan eliminadas por completo.

**Excepciones**

1. Si el usuario opta por eliminar su cuenta confirmando su contraseña actual, el sistema borra la cuenta, cierra la sesión y redirige al login.
2. Si la contraseña de confirmación no coincide, el sistema muestra "La contraseña es incorrecta" y no elimina la cuenta.
3. Si el nuevo correo ya está registrado por otro usuario, el sistema muestra el error, sin aplicar el cambio.

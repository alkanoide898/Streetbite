# StreetBite — Guía de la prueba

Aplicación Android de descubrimiento de foodtrucks. El backend está hecho con **Supabase**:

- **Supabase Auth** para registro, inicio de sesión, sesión persistente y Discord.
- **PostgREST** para leer y escribir el perfil en `public.usuarios`.
- **RLS** para que cada usuario solo pueda acceder a su propia fila.

La prueba se centra en 4 puntos:

1. Provider.
2. Splash.
3. Registro e inicio de sesión con Supabase.
4. Políticas.

---

## 1. Provider — Supabase Auth

### Qué es

Supabase es el proveedor del backend y de la autenticación. La app no guarda contraseñas en la tabla `usuarios`: las credenciales las administra **Supabase Auth** (`auth.users`).

### Configuración del proyecto

Archivo: `app/src/main/java/com/chacho/streetbite/SupabaseConfig.java`

```java
SUPABASE_URL = "https://ffzifmaqccjsnldrleyc.supabase.co"
SUPABASE_PUBLISHABLE_KEY = "sb_publishable_4klD2tLocxDQIsQRYUZSmw_Xad9YUkc"
```

La *publishable key* es pública por diseño: puede ir en el cliente. Lo que protege los datos es RLS, no el secreto de esa llave. La *service role key* nunca debe ir en la app.

### Providers usados

En **Supabase > Authentication > Providers**:

- **Email**: habilitado para registro e inicio de sesión con correo y contraseña.
- **Discord**: habilitado para el botón “Continuar con Discord”.

Para Discord también debe estar configurado:

**Discord Developer Portal > OAuth2 > Redirects**

```text
https://ffzifmaqccjsnldrleyc.supabase.co/auth/v1/callback
```

**Supabase > Authentication > URL Configuration > Redirect URLs**

```text
streetbite://login-callback
```

### Qué mostrar en la prueba

- `SupabaseConfig.java` con la URL y la publishable key.
- Pantalla de **Providers** con Email y Discord habilitados.
- Botón de Discord funcionando dentro de la app.

### Qué decir

> “La app usa Supabase como backend. Las credenciales se validan con Supabase Auth y el perfil se guarda en `public.usuarios` mediante PostgREST. No se almacenan contraseñas en la tabla de usuarios.”

---

## 2. Splash

### Qué hace

El splash:

1. Muestra el logo y la animación de StreetBite.
2. Revisa si existe una sesión guardada.
3. Si el token expiró, lo renueva con el `refresh_token`.
4. Decide si entra a **Home** o al **Login**.

### Archivos

- `SplashActivity.java`
- `Sesion.java`
- `res/layout/activity_splash.xml`
- `res/values/themes.xml` (splash del sistema)

### Cómo demostrarlo

**Sin sesión:**

1. Borra los datos de la app o desinstálala.
2. Abre la app.
3. Debe mostrarse el splash y luego el login.

**Con sesión:**

1. Inicia sesión.
2. Cierra la app por completo.
3. Vuelve a abrirla.
4. Debe mostrarse el splash y entrar directo a Home, sin pedir login.

### Qué decir

> “El splash valida la sesión, renueva el token si es necesario y navega al login o a Home según corresponda.”

---

## 3. Registro e inicio de sesión con Supabase

### Registro

Archivo: `RegistroActivity.java`

Flujo:

1. El usuario ingresa nombre, correo, contraseña y confirmación.
2. Se valida el correo y que las contraseñas coincidan.
3. Se llama a `POST /auth/v1/signup`.
4. Supabase crea el usuario en `auth.users`.
5. La app crea/actualiza el perfil en `public.usuarios` y entra a Home.

### Inicio de sesión

Archivo: `MainActivity.java`

Flujo:

1. El usuario ingresa correo y contraseña.
2. Se llama a `POST /auth/v1/token?grant_type=password`.
3. Supabase devuelve `access_token` y `refresh_token`.
4. La app guarda la sesión y sincroniza el perfil.
5. Entra a Home.

### Sesión

Archivo: `Sesion.java`

Guarda:

- `access_token`
- `refresh_token`
- `expires_at`
- `user_id`
- `nombre`
- `correo`

Cuando el token expira, se renueva con:

```text
POST /auth/v1/token?grant_type=refresh_token
```

### Tabla `usuarios`

La tabla no guarda contraseñas. Guarda:

| Columna | Tipo | Descripción |
|---|---|---|
| `id` | `uuid` | Clave primaria, generada automáticamente. |
| `usuario_id` | `uuid` | ID del usuario en `auth.users`. Único. |
| `nombre` | `text` | Nombre del usuario. |
| `correo` | `text` | Correo del usuario. |
| `created_at` | `timestamptz` | Fecha de creación. |

La relación importante es:

```text
public.usuarios.usuario_id = auth.users.id
```

### Endpoints usados

```text
POST /auth/v1/signup
POST /auth/v1/token?grant_type=password
POST /auth/v1/token?grant_type=refresh_token
POST /auth/v1/token?grant_type=pkce
POST /auth/v1/recover
POST /auth/v1/logout
GET  /auth/v1/user

GET  /rest/v1/usuarios?usuario_id=eq.<uuid>
POST /rest/v1/usuarios
```

### Cómo demostrarlo

1. Borra los datos de la app.
2. Abre la app.
3. Regístrate con un correo nuevo.
4. Verifica que entraste a Home.
5. Ve a **Supabase > Table Editor > usuarios**.
6. Debe aparecer una fila con el `usuario_id` del usuario creado.
7. Cierra sesión.
8. Inicia sesión con el mismo usuario.
9. Prueba una contraseña incorrecta y muestra el mensaje de error.

### Confirm email

Si **Confirm email** está activado en Supabase, el registro no devuelve sesión y la app avisa que debes confirmar el correo. Para la prueba conviene:

- Desactivar **Confirm email**, o
- Usar un usuario ya confirmado.

### Qué decir

> “El registro y el login se hacen contra Supabase Auth. Al autenticarse, la app sincroniza el perfil en `public.usuarios` usando el mismo UUID de `auth.users`.”

---

## 4. Políticas — RLS

La tabla `public.usuarios` tiene **Row Level Security** activo. Cada usuario autenticado solo puede acceder a su propia fila.

### Verificar que RLS está activo

```sql
select relrowsecurity
from pg_class
where oid = 'public.usuarios'::regclass;
```

Debe devolver `true`.

### Políticas configuradas

| Política | Comando | Regla |
|---|---|---|
| Usuarios pueden ver su perfil | `SELECT` | `auth.uid() = usuario_id` |
| Usuarios pueden crear su perfil | `INSERT` | `auth.uid() = usuario_id` |
| Usuarios pueden actualizar su perfil | `UPDATE` | `auth.uid() = usuario_id` |
| Usuarios pueden eliminar su perfil | `DELETE` | `auth.uid() = usuario_id` |

Las cuatro están asignadas al rol `authenticated`.

### Consulta para mostrar las políticas

```sql
select policyname, roles, cmd, qual, with_check
from pg_policies
where schemaname = 'public'
  and tablename = 'usuarios'
order by cmd;
```

### Cómo demostrar que RLS funciona

1. Inicia sesión con el usuario A y verifica que el perfil muestra sus datos.
2. Cierra sesión e inicia con el usuario B.
3. El usuario B no debe ver los datos del usuario A.
4. Desde la API con la publishable key, sin sesión, no se deben obtener filas:

```text
GET /rest/v1/usuarios?select=usuario_id
apikey: <publishable_key>
```

Debe devolver una lista vacía o un error de permisos, nunca los perfiles de otros usuarios.

### Importante

El **SQL Editor** de Supabase ejecuta las consultas como administrador y **se salta RLS**. Para demostrar las políticas hay que usar la app o la API REST con la publishable key.

### Qué decir

> “La tabla tiene RLS activo. Las cuatro políticas comparan `auth.uid()` con `usuario_id`, por lo que un usuario autenticado solo puede leer, crear, actualizar o eliminar su propia fila.”

---

## Checklist rápido para la prueba

### Antes de empezar

- [ ] Ejecutar `supabase/usuarios.sql` si falta alguna política o el trigger.
- [ ] Verificar que `relrowsecurity = true`.
- [ ] Verificar que existen las 4 políticas.
- [ ] Verificar que el índice único `usuarios_usuario_id_key` existe.
- [ ] Tener el provider Email habilitado.
- [ ] Tener Discord configurado, si se va a mostrar.
- [ ] Borrar los datos de la app para empezar sin sesión.

### Durante la prueba

- [ ] Mostrar `SupabaseConfig.java`.
- [ ] Mostrar Providers en Supabase.
- [ ] Abrir la app sin sesión: splash → login.
- [ ] Registrar un usuario: login → Home.
- [ ] Mostrar la fila nueva en `usuarios`.
- [ ] Cerrar y abrir la app: splash → Home.
- [ ] Mostrar el perfil con datos reales.
- [ ] Cerrar sesión: vuelve al login.
- [ ] Iniciar sesión de nuevo.
- [ ] Mostrar las 4 políticas y `relrowsecurity = true`.

---

## Errores comunes

### `42P10: no unique or exclusion constraint matching the ON CONFLICT`

Falta el índice único en `usuario_id`:

```sql
create unique index if not exists usuarios_usuario_id_key
on public.usuarios (usuario_id);
```

### El perfil no aparece en `usuarios`

Revisar:

- Que el trigger `on_auth_user_created` exista.
- Que la app tenga un `access_token` válido.
- Que las políticas INSERT/SELECT estén creadas.
- Que `usuario_id` coincida con el `id` de `auth.users`.

### Discord no vuelve a la app

Revisar:

- Redirect de Discord: `https://ffzifmaqccjsnldrleyc.supabase.co/auth/v1/callback`
- Redirect URL de Supabase: `streetbite://login-callback`

### El registro no inicia sesión

Puede ser por **Confirm email** activado. Desactivarlo para la prueba o confirmar el correo antes de iniciar sesión.

---

## Compilar la app

```powershell
.\gradlew.bat :app:assembleDebug
```

El APK de depuración queda en:

```text
app/build/outputs/apk/debug/app-debug.apk
```

---

## Historial de cambios

### 2026-11-02 — Autenticación y perfiles

- Autenticación con **Supabase Auth** (registro, login, recuperación y Discord PKCE).
- Perfil de usuario en `public.usuarios` vía PostgREST, vinculado a `auth.users` por `usuario_id`.
- **Sesión persistente** con `refresh_token` y renovación automática al abrir la app.
- **Splash** que valida la sesión y decide entre Login y Home.
- Pantalla de **Perfil** con nombre, correo e inicial reales, y cierre de sesión real.
- **RLS** activo en `public.usuarios` con 4 políticas (`SELECT`, `INSERT`, `UPDATE`, `DELETE`) para `authenticated`.
- Índice único `usuarios_usuario_id_key` y trigger `on_auth_user_created`.
- Documentación de la prueba: provider, splash, registro/login y políticas.

<!-- Agregar aqui las proximas versiones siguiendo el mismo formato. -->




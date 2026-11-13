# StreetBite

**StreetBite** es una app Android para descubrir foodtrucks en San Carlos. La idea
es simple: abrir la app y en segundos saber qué comer, dónde está el local y cómo
contactarlo.

El catálogo vive en Supabase, las cuentas se manejan con Supabase Auth y la app
funciona incluso si el backend no responde, gracias a un catálogo local de
respaldo.

---

## Funcionalidades

- **Catálogo real de San Carlos**: los 9 locales cargados en Supabase, con
  nombre, descripción, calificación, dirección, horario y contacto.
- **Búsqueda y filtros**: búsqueda por nombre, descripción o categoría, filtro
  por categoría y switch de “solo abiertos”.
- **Categorías dinámicas**: los chips se generan desde las categorías presentes
  en el catálogo; si mañana se agrega “sushi”, el chip aparece solo.
- **Mejor calificados**: fila horizontal con los locales mejor puntuados.
- **Mapa**: un marcador por local, con encuadre automático para que todos queden
  visibles al abrir.
- **Detalle del local**: foto, estado, distancia, dirección, horario y accesos
  directos a WhatsApp e Instagram.
- **Cuentas**: registro e inicio de sesión con correo/contraseña y con Discord.
- **Sesión persistente**: al reabrir la app se renueva el token y se entra
  directo a Home.
- **Perfil**: datos reales del usuario, configuración de sonidos y cierre de
  sesión.
- **Sonidos y animaciones**: feedback táctil y transiciones cuidadas, con opción
  de desactivar los sonidos.
- **Respaldo local**: si Supabase falla, la app sigue mostrando el catálogo.

---

## Stack

| Capa | Tecnología |
|---|---|
| App | Android nativo (Java), minSdk 29, targetSdk 37 |
| Listas | `RecyclerView` + `DiffUtil` |
| Mapas | osmdroid |
| Backend | Supabase |
| Autenticación | Supabase Auth (Email + Discord PKCE) |
| Datos | PostgREST + RLS |
| Sonidos | `SoundPool` con WAV propios |
| Animaciones | APIs nativas de Views (sin librerías externas) |

---

## Estructura del proyecto

```text
app/src/main/java/com/chacho/streetbite/
├── SplashActivity.java          # valida sesión y decide login / home
├── MainActivity.java            # inicio de sesión
├── RegistroActivity.java        # creación de cuenta
├── HomeActivity.java            # catálogo, búsqueda, filtros y chips
├── MapActivity.java             # mapa con los locales
├── DetalleFoodtruckActivity.java
├── PerfilActivity.java          # perfil, configuración y logout
├── FoodtruckAdapter.java        # tarjetas, DiffUtil y animaciones
├── FoodtruckRepositorio.java    # caché, carga remota y respaldo local
├── Foodtruck.java               # modelo y conversión desde JSON
├── SupabaseHelper.java          # llamadas a Auth y PostgREST
├── SupabaseConfig.java          # URL y publishable key
├── Sesion.java                  # tokens y sesión persistente
├── Sonidos.java                 # efectos de interfaz
└── BaseActivity.java            # sonidos, insets y base común

app/src/main/res/layout/         # pantallas e ítems de lista
app/src/main/res/raw/            # tap.wav y tarjeta.wav
supabase/                        # scripts SQL y datos de los locales
```

---

## Puesta en marcha

### Requisitos

- Android Studio con JDK 11 y Android SDK 37.
- Un proyecto de Supabase con las tablas de este repositorio.
- Opcional para el mapa: conexión a internet (osmdroid descarga los tiles).

### Configuración

La URL y la publishable key están en
`app/src/main/java/com/chacho/streetbite/SupabaseConfig.java`:

```java
SUPABASE_URL = "https://ffzifmaqccjsnldrleyc.supabase.co"
SUPABASE_PUBLISHABLE_KEY = "sb_publishable_4klD2tLocxDQIsQRYUZSmw_Xad9YUkc"
```

La *publishable key* es pública por diseño y puede ir en el cliente: lo que
protege los datos son las políticas RLS. La *service role key* nunca debe
incluirse en la app.

En **Supabase → Authentication → Providers** deben estar habilitados:

- **Email**: registro e inicio de sesión con correo y contraseña.
- **Discord**: botón “Continuar con Discord”.

Para Discord también hay que registrar estas URLs:

```text
Discord Developer Portal → OAuth2 → Redirects
https://ffzifmaqccjsnldrleyc.supabase.co/auth/v1/callback

Supabase → Authentication → URL Configuration → Redirect URLs
streetbite://login-callback
```

### Base de datos

Ejecutar en **Supabase → SQL Editor**:

1. `supabase/usuarios.sql` — tabla de perfiles, políticas y trigger.
2. `supabase/foodtrucks.sql` — catálogo y locales reales de San Carlos.

Verificación rápida:

```sql
select count(*) from public.foodtrucks;          -- 9
select relrowsecurity from pg_class
  where oid = 'public.usuarios'::regclass;        -- true
select relrowsecurity from pg_class
  where oid = 'public.foodtrucks'::regclass;      -- true
```

### Compilar

```powershell
.\gradlew.bat :app:assembleDebug
```

El APK de depuración queda en:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Para instalarlo en un dispositivo conectado:

```powershell
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## Base de datos

### `public.usuarios`

Guarda el perfil público del usuario. Las contraseñas las administra Supabase
Auth en `auth.users`; esta tabla no las almacena.

| Columna | Tipo | Descripción |
|---|---|---|
| `id` | `uuid` | Clave primaria. |
| `usuario_id` | `uuid` | ID en `auth.users`. Único. |
| `nombre` | `text` | Nombre visible. |
| `correo` | `text` | Correo del usuario. |
| `created_at` | `timestamptz` | Fecha de creación. |

La relación es `public.usuarios.usuario_id = auth.users.id`.

**RLS activo** con cuatro políticas asignadas al rol `authenticated`:

| Política | Comando | Regla |
|---|---|---|
| Ver perfil | `SELECT` | `auth.uid() = usuario_id` |
| Crear perfil | `INSERT` | `auth.uid() = usuario_id` |
| Actualizar perfil | `UPDATE` | `auth.uid() = usuario_id` |
| Eliminar perfil | `DELETE` | `auth.uid() = usuario_id` |

Un usuario autenticado solo puede leer, crear, actualizar o eliminar su propia
fila.

### `public.foodtrucks`

| Columna | Tipo | Descripción |
|---|---|---|
| `id` | `uuid` | Clave primaria. |
| `nombre` | `text` | Nombre del foodtruck. Único. |
| `descripcion` | `text` | Texto corto de la tarjeta. |
| `categoria` | `text` | Una o varias categorías separadas por coma. |
| `rating` | `numeric(2,1)` | Calificación de 0 a 5. |
| `lat` / `lon` | `double precision` | Coordenadas para el mapa. |
| `abierto` | `boolean` | Estado actual. |
| `imagen` | `text` | Drawable local (`tacos`, `burguer`, `pizza`, `completos`). |
| `direccion` | `text` | Dirección del local. |
| `horario` | `text` | Horario de atención. |
| `telefono` | `text` | Teléfono / WhatsApp. |
| `instagram` | `text` | Usuario de Instagram, sin `@`. |
| `creado_por` | `uuid` | Usuario que lo creó (FK a `auth.users`). |
| `created_at` / `updated_at` | `timestamptz` | Fechas automáticas. |

**RLS activo**:

```text
SELECT → público (anon + authenticated)
INSERT → authenticated, creado_por = auth.uid()
UPDATE → authenticated, creado_por = auth.uid()
DELETE → authenticated, creado_por = auth.uid()
```

Cualquiera puede leer el catálogo, pero solo el dueño de un local puede
modificarlo.

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
GET  /rest/v1/foodtrucks?select=...&order=rating.desc,nombre.asc
```

---

## Cómo funciona por dentro

### Arranque y sesión

`SplashActivity` revisa si hay una sesión guardada, renueva el token con el
`refresh_token` si expiró y navega a Home o al login. `Sesion.java` persiste
`access_token`, `refresh_token`, `expires_at`, `user_id`, `nombre` y `correo`.

### Registro e inicio de sesión

- **Registro** (`RegistroActivity`): valida correo y contraseñas, llama a
  `/auth/v1/signup`, crea el perfil en `public.usuarios` y entra a Home.
- **Login** (`MainActivity`): llama a `/auth/v1/token?grant_type=password`,
  guarda la sesión, sincroniza el perfil y entra a Home.
- **Discord**: flujo PKCE con retorno a la app mediante `streetbite://login-callback`.

> **Nota:** si **Confirm email** está activado en Supabase, el registro no
> devuelve sesión y la app avisa que hay que confirmar el correo. Para pruebas
> rápidas conviene desactivarlo o usar un usuario ya confirmado.

### Catálogo y respaldo local

1. `HomeActivity` pide el catálogo a `FoodtruckRepositorio.cargar(...)`.
2. El repositorio consulta Supabase con `SupabaseHelper.listarFoodtrucks(...)`.
3. Si la respuesta es válida, se usa la lista remota y se guarda en caché.
4. Si falla la red o la tabla no responde, se mantiene el respaldo local.
5. `HomeActivity` filtra por categoría, búsqueda y “solo abiertos”, y actualiza
   la lista vertical, la fila de destacados y los chips.
6. `MapActivity` usa la misma caché para dibujar los marcadores.

### Detalle y mapa

- `DetalleFoodtruckActivity` muestra toda la información del local y abre
  WhatsApp o Instagram con un toque.
- `MapActivity` dibuja un marcador propio por local (cada uno con su
  `Drawable`, para que osmdroid no comparta los límites) y encuadra la cámara
  para mostrar todos los locales.

### Sonidos

- `Sonidos.java` carga los efectos con `SoundPool`, así se reproducen al
  instante y pueden solaparse sin cortarse.
- `BaseActivity` detecta el toque sobre cualquier vista con listener desde
  `dispatchTouchEvent`: los botones, chips y tarjetas ya existentes suenan sin
  envolver cada listener.
- Botones, chips y navegación usan `tap.wav`; las tarjetas usan `tarjeta.wav`.
- Al hacer scroll no suenan.
- Se desactivan en **Perfil → Configuración** y el estado queda guardado en
  `SharedPreferences` (`StreetBiteSonidos`).
- Los WAV son propios, generados por síntesis, sin licencias externas.

### Animaciones

- **Entrada escalonada**: las primeras 6 tarjetas aparecen con fade y una subida
  de 14 dp, con 55 ms de retraso entre una y otra.
- **Feedback al presionar**: las tarjetas se encogen al 97 % y vuelven con un
  rebote leve; la sombra sube mientras están presionadas.
- **Chips**: el chip elegido hace un “pop” y la fila se desplaza suavemente
  hasta dejarlo centrado.
- **Filtros**: `DiffUtil` permite que RecyclerView anime las tarjetas que
  entran, salen o se mueven.
- **Barra inferior**: el botón tocado rebota y se reinicia al volver a Home.
- **Transición al detalle**: la foto de la tarjeta viaja hasta la cabecera del
  detalle con una transición compartida.
- Si el usuario desactiva las animaciones del sistema, la entrada, los chips y
  la barra inferior se aplican sin animación.

### Barras del sistema

- Android 15 y 16 dibujan la app **edge-to-edge**: el contenido pasa por debajo
  de la barra de estado y la de navegación.
- `BaseActivity` suma el alto de `systemBars` al padding de la vista raíz del
  layout, para que los botones superiores (Volver, Perfil) no queden en la
  franja que el sistema intercepta.
- El ajuste se hace sobre la raíz del layout y no sobre `android.R.id.content`,
  así el fondo sigue pintando detrás de la barra de estado sin bandas.
- `SplashActivity` queda excluido porque su diseño ya ocupa toda la pantalla.

---

## Verificación antes de publicar

- [ ] Ejecutar `supabase/usuarios.sql` y `supabase/foodtrucks.sql`.
- [ ] Confirmar `relrowsecurity = true` en ambas tablas.
- [ ] Confirmar que existen las políticas de `usuarios` y `foodtrucks`.
- [ ] Probar registro, login, cierre de sesión y reapertura con sesión guardada.
- [ ] Probar Discord de punta a punta (ida y vuelta a la app).
- [ ] Revisar los 9 locales en Home, mapa y detalle.
- [ ] Probar búsqueda, categorías y “solo abiertos”.
- [ ] Probar WhatsApp e Instagram desde el detalle.
- [ ] Verificar sonidos y su interruptor en Perfil.
- [ ] Revisar la app en un dispositivo con animaciones desactivadas.

---

## Solución de problemas

### `42P10: no unique or exclusion constraint matching the ON CONFLICT`

Falta el índice único en `usuario_id`:

```sql
create unique index if not exists usuarios_usuario_id_key
on public.usuarios (usuario_id);
```

### El perfil no aparece en `usuarios`

Revisar que exista el trigger `on_auth_user_created`, que la app tenga un
`access_token` válido, que las políticas INSERT/SELECT estén creadas y que
`usuario_id` coincida con el `id` de `auth.users`.

### Discord no vuelve a la app

Revisar el redirect en Discord
(`https://ffzifmaqccjsnldrleyc.supabase.co/auth/v1/callback`) y la redirect URL
de Supabase (`streetbite://login-callback`).

### El registro no inicia sesión

Suele ser por **Confirm email** activado. Desactivarlo o confirmar el correo
antes de iniciar sesión.

### El SQL Editor “ignora” RLS

El SQL Editor de Supabase ejecuta como administrador y se salta RLS. Para
comprobar las políticas hay que usar la app o la API REST con la publishable
key, por ejemplo:

```text
GET /rest/v1/usuarios?select=usuario_id
apikey: <publishable_key>
```

Sin sesión debe devolver una lista vacía o un error de permisos, nunca los
perfiles de otros usuarios.

---

## Próximos pasos

- Fotos reales por local en lugar de ilustraciones.
- Panel para que cada dueño edite su foodtruck sin pasar por el SQL Editor.
- Notificaciones cuando un local favorito abre o cambia de horario.
- Favoritos y reseñas de usuarios.
- Publicación en Play Store.

---

## Historial de cambios

### 2026-11-12 — Animaciones de interfaz

- `FoodtruckAdapter` usa `DiffUtil` con ids estables; los filtros se reacomodan
  con animación en vez de saltar.
- Entrada escalonada de las primeras tarjetas (fade + 14 dp).
- Feedback de presión en tarjetas: escala 0.97, sombra elevada y rebote, sin
  consumir el toque ni afectar los sonidos.
- Chips con “pop” y desplazamiento automático hasta el chip elegido.
- Barra inferior con rebote del botón tocado.
- Transición compartida de la foto al abrir el detalle.

### 2026-11-12 — Botones bajo la barra de estado

- Se corrigió el “Volver” de Perfil, del detalle y del mapa: quedaban debajo de
  la barra de estado y el sistema se comía el toque.
- `BaseActivity` aplica los insets de `systemBars` al padding de la raíz del
  layout, sin tocar cada pantalla por separado.
- El splash queda excluido del ajuste.

### 2026-11-12 — Sonidos de interfaz

- Se agregaron `tap.wav` (cristal, 140 ms) y `tarjeta.wav` (burbuja, 117 ms) en
  `res/raw`.
- `Sonidos.java` carga los efectos con `SoundPool`.
- `BaseActivity` centraliza la detección del toque para toda la app.
- Las tarjetas usan un sonido distinto al de botones y chips.
- Interruptor “Sonidos al tocar botones y tarjetas” en Perfil → Configuración.

### 2026-11-08 — Coordenadas corregidas

- Se validaron las 9 ubicaciones con la ficha/geocodificación de Google Maps,
  contrastadas con ArcGIS y OpenStreetMap.
- Correcciones principales: K'milo Móvil (Francisco Bilbao 934), Rocka Pizza
  (Independencia 1291), Supras Gourmet (Independencia 1302), La Playa Food
  Truck (Vicuña Mackenna 322), PartyBurguer (Chacabuco 621), Agri Dulce
  (Brasil 280) y West Burguers (sector Alameda).
- El Bajón del Tío Pato se ubica con su ficha de Google en el sector Alameda
  (Av. Arturo Prat Sur); se mantiene “Puelma 146” como dirección del local. Su
  teléfono se corrigió a `+56992493338`.
- Las Mechadas de Mechita no tiene ficha propia con dirección exacta; se ubica
  en el sector Alameda, junto a los demás carros de comida.
- Los locales del mismo sector (Alameda, Party/Playa, Rocka/Supras) se solapan
  al zoom inicial, pero cada marcador conserva su posición real y se separan al
  acercar.

### 2026-11-08 — Marcadores del mapa

- Se corrigió el mapa que mostraba un solo marcador gigante: ahora cada
  `Marker` recibe su propio `Drawable` rasterizado.
- Los marcadores son círculos de 40 dp con anclaje al centro.
- Al abrir el mapa, la cámara encuadra automáticamente los 9 locales.

### 2026-11-06 — Locales reales de San Carlos

- Cargados los **9 locales reales**: K'milo Móvil, Las Mechadas de Mechita,
  El Bajón del Tío Pato, La Playa Food Truck, West Burguers, Rocka Pizza,
  PartyBurguer, Supras Gourmet y Agri Dulce.
- Se agregaron las columnas `telefono` e `instagram`.
- El detalle abre WhatsApp e Instagram con un toque.
- `categoria` admite varias categorías separadas por coma; se filtran y generan
  chips para cada una.
- El respaldo local también usa los locales reales.

### 2026-11-06 — Enfoque en San Carlos

- La app usa **San Carlos** como ciudad por defecto (textos y mapa).
- Coordenadas de respaldo del mapa: `-36.4248, -71.9581` (centro de San Carlos).
- Los chips de categoría se generan dinámicamente desde el catálogo.
- Nuevo `supabase/locales-san-carlos.md` con la plantilla para cargar locales.

### 2026-11-06 — Catálogo real de foodtrucks

- Nueva tabla `public.foodtrucks` con RLS.
- La app carga el catálogo desde Supabase (`GET /rest/v1/foodtrucks`).
- Lista vertical dinámica con RecyclerView en Home.
- Fila horizontal de mejores calificados por rating.
- Filtros de categoría, búsqueda y “solo abiertos” sobre datos reales.
- Mapa con marcadores cargados desde la misma caché.
- Detalle con dirección y horario.
- Respaldo local si Supabase no responde.
- Se agregó la dependencia `androidx.recyclerview:recyclerview`.

### 2026-11-02 — Autenticación y perfiles

- Autenticación con **Supabase Auth** (registro, login, recuperación y Discord
  PKCE).
- Perfil en `public.usuarios` vía PostgREST, vinculado a `auth.users` por
  `usuario_id`.
- **Sesión persistente** con `refresh_token` y renovación automática.
- **Splash** que valida la sesión y decide entre Login y Home.
- Pantalla de **Perfil** con datos reales y cierre de sesión.
- **RLS** activo en `public.usuarios` con cuatro políticas para `authenticated`.
- Índice único `usuarios_usuario_id_key` y trigger `on_auth_user_created`.

<!-- Agregar aquí las próximas versiones siguiendo el mismo formato. -->
